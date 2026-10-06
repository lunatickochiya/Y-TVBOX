# Y-TVBOX

Y-TVBOX 使用独立应用 ID `com.github.ytvbox.osc`，可以和原版 TVBox 同时安装。通用 Java 版本最低支持 Android 4.4（API 19）；Python 版本因运行时限制仍要求 Android 5.0（API 21）。

## GitHub Release

发布工作流会构建 `armeabi-v7a` 和 `arm64-v8a` 两个签名 APK，另外还会构建两个 X5 内核版（文件名带 `x5`）和两个 Gecko 内核版（文件名带 `gecko`），共 6 个 APK。进入仓库的 Actions 页面，选择 `Y-TVBOX`，点击 `Run workflow` 即可构建并上传到 GitHub Release。

Android 4.4 兼容分支生成的文件名以 `Y-TVBOX_4.4_` 开头，便于和 `main` 版本区分。

未配置签名时，工作流会生成临时签名以完成构建和发布；不同运行生成的临时签名不同，APK 可能无法直接覆盖升级。正式发布请在仓库的 Actions secrets 中配置：

- `YTVBOX_KEYSTORE_BASE64`：JKS/PKCS12 签名文件的 Base64 内容
- `YTVBOX_KEYSTORE_PASSWORD`：签名文件密码
- `YTVBOX_KEY_ALIAS`：签名别名
- `YTVBOX_KEY_PASSWORD`：签名私钥密码

Android 4.4 设备应安装文件名中带 `armeabi` 的 APK。请长期保存同一套签名密钥，否则已安装版本无法升级。

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

> X5 内核 native 引擎（`libmttwebview.so`）按 API 23 构建、并依赖 API 21+ 符号，
> 最低需要 **Android 5.0**（腾讯官方支持 Android 5-13）；Android 4.4 设备请安装
> 普通版或 `gecko` 版。

## Gecko 内核（可选版本）

文件名带 `gecko` 的 APK 内置 Mozilla GeckoView **118**（按 ABI 打包，约 +60MB），用现代
Firefox 内核替代老旧系统 WebView 做嗅探/解析：设置里把「嗅探Webview」切换为「Gecko内核」
即可，无需下载、无需重启。

> 4.4 分支的 gecko 版特意选用 GeckoView 118：它是最后一个支持 Android 4.1+（API 16）的
> 版本，armeabi-v7a 的 native 库（libxul.so 等）minAPI=16，**Android 4.4 设备可直接使用**；
> 119 及以上要求 Android 5.0+，仅在 main / dev 分支使用 GeckoView 144。

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
