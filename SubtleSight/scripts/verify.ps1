[CmdletBinding()]
param([switch]$SkipInstall)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$tempDir = Join-Path $root ".tmp"
$oldTemp = @{ Temp = $env:TEMP; Tmp = $env:TMP }
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$env:TEMP = $tempDir
$env:TMP = $tempDir
Push-Location $root
try {
  if (-not $SkipInstall) {
    pnpm --dir web-ui install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw "pnpm install failed" }
  }
  pnpm --dir web-ui lint
  if ($LASTEXITCODE -ne 0) { throw "web lint failed" }
  pnpm --dir web-ui test
  if ($LASTEXITCODE -ne 0) { throw "web tests failed" }
  pnpm --dir web-ui build
  if ($LASTEXITCODE -ne 0) { throw "web production build failed" }
  mvn clean verify
  if ($LASTEXITCODE -ne 0) { throw "Maven verify failed" }
  & (Join-Path $PSScriptRoot "smoke.ps1")
  if ($LASTEXITCODE -ne 0) { throw "HTTP smoke acceptance failed" }
  mvn -q -pl coverage-report -am verify -DskipTests
  if ($LASTEXITCODE -ne 0) { throw "Coverage aggregate refresh failed" }
} finally {
  Pop-Location
  $env:TEMP = $oldTemp.Temp
  $env:TMP = $oldTemp.Tmp
}
