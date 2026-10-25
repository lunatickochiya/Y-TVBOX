# Y-TVBOX

Y-TVBOX 使用独立应用 ID `com.github.ytvbox.osc`，可以和原版 TVBox 同时安装。

> **Android 版本要求**：`main` 分支保持 Android 5.0（API 21）兼容，使用
> media3 1.3.1 + nextlib 0.7.1 的旧 exo 解码栈；花屏频道可切「mpv软解」。
> 需要 nextlib 1.11 新 exo 软解（要求 Android 6.0 / API 23）的设备请使用
> `live` 分支；Android 5 专用精简版是 `live-android-5`。

## 解码器（Android 5 兼容 / MPV）

- **exo 软解/硬解**：`main` 使用 Android 5 兼容的 media3 1.3.1 + nextlib 0.7.1；
  硬解不支持的编码/音频会回退 FFmpeg。nextlib 1.11 的新 exo 花屏修复要求 API 23，
  因此只放在 `live` 分支。
- **音频兜底**：系统 MediaCodec 不支持 AC3 / EAC3 / DTS / TrueHD 时，用 nextlib 的
  FFmpeg 音频解码（点播默认 Exo 也保留该兜底）。
- **FongMi/WeX csp 点播源兼容**：按 FongMi/TV 的 catvod 接口补齐了
  `Spider.siteKey/net/local/js/dialog`、`getProxyUrl()`、`Proxy/Runtime/Local/Net`
  等主机接口，`JarLoader` 会初始化站点上下文；`newwex.json` 的 Wex/GoProxy 已能在
  本 App 启动（日志 `signature validation succeeded` / `health ok`），不再依赖播放器
  自己启动 GoProxy。少数站点（如 AppV7/ManJu）仍会走它们自己的远程配置，若超时属于
  站点侧问题，可在搜索源里关闭这些站点。
- **MPV 版 APK**：文件名带 `-mpv`，额外内置 libmpv，直播「播放解码」会多出
  **mpv硬解 / mpv软解**。mpv 硬解使用 `mediacodec-copy`（兼容性优先），mpv 软解是纯
  FFmpeg；遇到 exo/系统硬解花屏的频道可直接切 MPV 对比。
- MPV 原生库同时支持 **armeabi-v7a** 与 **arm64-v8a**；发布工作流两个 ABI 的
  MPV 版都会构建（armeabi 约 43MB，arm64 约 47MB）。
- 标准版与 MPV 版**同包名、同签名**，可直接覆盖安装并保留设置；标准版安装包约
  26MB（armeabi）/ 29MB（arm64），MPV 版约 43MB / 47MB。

## GitHub Release

发布工作流会构建 `armeabi-v7a` 和 `arm64-v8a` 两个标准版 APK、两个 MPV 版 APK，
另外还会构建两个 X5 内核版（文件名带 `x5`）和两个 Gecko 内核版（文件名带 `gecko`），
共 8 个 APK。进入仓库的 Actions 页面，选择 `Y-TVBOX`，点击 `Run workflow` 即可构建
并上传到 GitHub Release。Release 标签包含分支名和运行编号
（例如 `Y-TVBOX-main-...`），不同分支构建不会互相覆盖。工作流会先安装 `patchelf` 用于 MPV 原生库改名。

`main` 分支生成的文件名以 `Y-TVBOX_main_` 开头，便于和 Android 4.4 兼容版区分。

未配置签名时，工作流会生成临时签名以完成构建和发布；不同运行生成的临时签名不同，APK 可能无法直接覆盖升级。正式发布请在仓库的 Actions secrets 中配置：

- `YTVBOX_KEYSTORE_BASE64`：JKS/PKCS12 签名文件的 Base64 内容
- `YTVBOX_KEYSTORE_PASSWORD`：签名文件密码
- `YTVBOX_KEY_ALIAS`：签名别名
- `YTVBOX_KEY_PASSWORD`：签名私钥密码

请长期保存同一套签名密钥，否则已安装版本无法升级。

## IPTV / FCC

Y-TVBOX 内置原生 FCC（快速换台）与组播播放：带 `?fcc=` 的频道由 App 自己完成
FCC 单播突发、追平后无缝切组播，再经 `127.0.0.1` 本地 HTTP 中继喂给播放器。
直播界面「偏好设置 → FCC快速换台」可开关。

URL 示例、网络前提（组播转发 / FCC 单播 NAT）与日志排查见
[docs/FCC.md](docs/FCC.md)。

## X5 内核（可选版本）

文件名带 `x5` 的 APK 内置腾讯 TBS/X5 SDK 与官方 X5 内核（按 ABI 打包，arm64 约 +53MB /
armeabi 约 +45MB），用于替代老旧系统 WebView 做嗅探/解析：设置里把「嗅探Webview」
切换为「X5内核」，首次选择会把内置内核安装到应用私有目录，**重启应用后生效**；
之后设置页会显示内核版本号。整个过程不联网下载内核。

## Gecko 内核（可选版本）

文件名带 `gecko` 的 APK 内置 Mozilla GeckoView 144（按 ABI 打包，约 +80MB），用现代
Firefox 内核替代老旧系统 WebView 做嗅探/解析：设置里把「嗅探Webview」切换为
「Gecko内核」即可，无需下载、无需重启。

请求嗅探通过内置 WebExtension（`assets/extensions/sniffer`）的 `webRequest` 上报完成，
点击选择器（evaluateScript）通过扩展的 native 端口在页面里执行 JS，与系统/X5 内核行为一致。

=== Source Code - Editing the app default settings ===
/src/main/java/com/github/tvbox/osc/base/App.java

    private void initParams() { 

        putDefault(HawkConfig.HOME_REC, 2);       // Home Rec 0=豆瓣, 1=推荐, 2=历史
        putDefault(HawkConfig.PLAY_TYPE, 1);      // Player   0=系统, 1=IJK, 2=Exo
        putDefault(HawkConfig.IJK_CODEC, "硬解码");// IJK Render 软解码, 硬解码
        putDefault(HawkConfig.HOME_SHOW_SOURCE, true);  // true=Show, false=Not show
        putDefault(HawkConfig.HOME_NUM, 2);       // History Number
        putDefault(HawkConfig.DOH_URL, 2);        // DNS
        putDefault(HawkConfig.SEARCH_VIEW, 2);    // Text or Picture

    }


原项目：https://github.com/takagen99/Box