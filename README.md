# Y-TVBOX

Y-TVBOX 使用独立应用 ID `com.github.ytvbox.osc`，可以和原版 TVBox 同时安装。通用 Java 版本最低支持 Android 4.4（API 19）；Python 版本因运行时限制仍要求 Android 5.0（API 21）。

## GitHub Release

发布工作流会构建 `armeabi-v7a` 和 `arm64-v8a` 两个签名 APK。进入仓库的 Actions 页面，选择 `Y-TVBOX`，点击 `Run workflow` 即可构建并上传到 GitHub Release。

Android 4.4 兼容分支生成的文件名以 `Y-TVBOX_4.4_` 开头，便于和 `main` 版本区分。

未配置签名时，工作流会生成临时签名以完成构建和发布；不同运行生成的临时签名不同，APK 可能无法直接覆盖升级。正式发布请在仓库的 Actions secrets 中配置：

- `YTVBOX_KEYSTORE_BASE64`：JKS/PKCS12 签名文件的 Base64 内容
- `YTVBOX_KEYSTORE_PASSWORD`：签名文件密码
- `YTVBOX_KEY_ALIAS`：签名别名
- `YTVBOX_KEY_PASSWORD`：签名私钥密码

Android 4.4 设备应安装文件名中带 `armeabi` 的 APK。请长期保存同一套签名密钥，否则已安装版本无法升级。

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
