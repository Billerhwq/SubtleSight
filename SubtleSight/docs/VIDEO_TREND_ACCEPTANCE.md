# 视频热榜真实采集与播放链路验收

验收时间：2026-07-18 15:50（Asia/Shanghai）

## 真实信源

- Internet Archive Public Video Recent
- 搜索入口：`https://archive.org/advancedsearch.php`
- 元数据入口：`https://archive.org/metadata/{identifier}`
- 下载入口：`https://archive.org/download/{identifier}/{file}`

说明：本轮默认运行的信源只接入公开可下载视频，不绕过 B站、抖音、YouTube 等平台的登录、版权、反爬或播放限制。

## OpenCLI 参考实现

已按 OpenCLI 的 GitHub 文档补充可选桥接：

- endpoint 格式：`opencli://bilibili/hot?limit=10&download=true`
- 列表命令：`opencli bilibili hot --limit 10 -f json`
- 下载命令：`opencli bilibili download <BV/URL> --output <dataDir>/opencli-downloads/...`
- 下载后的本地视频会进入同一套 blob 存储，再通过 `/api/v1/media/{hash}` 给前端播放器播放。

当前本机状态：

- `opencli`：未安装
- `yt-dlp`：未安装
- C 盘可用空间：`0`

因此当前运行服务里 `SUBTLESIGHT_OPENCLI_VIDEO_ENABLED=false`，避免把不可用的 OpenCLI 源混入真实采集结果。安装 OpenCLI、Browser Bridge、yt-dlp 并释放磁盘空间后，将 `SUBTLESIGHT_OPENCLI_VIDEO_ENABLED=true` 即可启用 B站热门视频桥接源。

## 本轮真实联调结果

- 注册视频源：`Internet Archive Public Video Recent`
- 实际采集：2 条
- 入库标题：
  - `Too Many Klasky Csupo`
  - `20260718 0704 04.1275366`
- 第一条媒体：
  - mediaUrl：`/api/v1/media/9378388ef1d144200cc55d2ecd02bd0d1b7ef23ae5541b17b779a3f4ce60b39d?type=video%2Fmp4`
  - mediaType：`video/mp4`
  - contentLength：`8220026`

## 验收命令与结果

- `mvn -q -pl source-connectors -am test`：通过
- `mvn -q -pl server-app -am test '-Dskip.web=true'`：通过
- `pnpm --dir G:\SubtleSight\web-ui test`：12 tests passed
- `pnpm --dir G:\SubtleSight\web-ui build`：通过
- `mvn -q -pl server-app -am package '-DskipTests'`：通过
- OpenCLI 桥接单测：通过，使用本地 fake `opencli.cmd` 覆盖 `hot -f json` 与 `download --output` 链路
- 后端媒体接口：`200 video/mp4`
- 前端代理媒体接口：`200 video/mp4`

## 页面证据

- 截图：`G:\SubtleSight\build\screenshots\discover-video-live-20260718-ready.png`
