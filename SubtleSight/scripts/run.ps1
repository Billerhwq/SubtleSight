[CmdletBinding()]
param(
  [string]$DataDir = "",
  [int]$Port = 8080,
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $DataDir) { $DataDir = Join-Path $root "data" }

if (-not $SkipBuild) {
  Push-Location $root
  try {
    pnpm --dir web-ui install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw "pnpm install failed" }
    pnpm --dir web-ui build
    if ($LASTEXITCODE -ne 0) { throw "web build failed" }
    mvn -q -pl server-app -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "server package failed" }
  } finally { Pop-Location }
}

$env:SUBTLESIGHT_DATA_DIR = [IO.Path]::GetFullPath($DataDir)
$env:SUBTLESIGHT_PORT = "$Port"
$env:SUBTLESIGHT_ALLOWED_ORIGINS = "http://127.0.0.1:$Port,http://localhost:$Port"
$jar = Join-Path $root "server-app\target\server-app-1.0.0-SNAPSHOT.jar"
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { (Get-Command java -ErrorAction Stop).Source }
& $java --add-modules jdk.incubator.vector -jar $jar
