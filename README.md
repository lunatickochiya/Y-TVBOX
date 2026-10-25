# y-live

y-live 是 Y-TVBOX 的**纯直播版**：应用 ID 为 `com.github.ylive.osc`，可以和 Y-TVBOX
（`com.github.ytvbox.osc`）同时安装。它只保留直播（Live TV）功能：

> **Android 版本要求**：当前 `live` / `main` 分支的 exo 软解升级到 nextlib 1.11.1，
> 该库最低要求 Android 6.0（API 23）。Android 5.0/5.1 设备请使用
> `y-tvbox-android-5` 分支（保留旧解码栈，minSdk 21）。

- 启动直接进直播界面；
- 不再加载点播数据源：爬虫 / JS 引擎（QuickJS）/ P2P / 嗅探内核都不初始化，
  观看记录数据库也已移除；本地 HTTP 服务仅提供手机扫码配置与 IJK 安全 DNS 转发。
- 点播相关代码与依赖已全部移除（阿里播放器 / P2P / 迅雷 / QuickJS / Python / XWalk /
  Room / 弹幕 / 网盘 等），APK 从 ~28MB 精简到 ~16MB（安装后占用更小、启动更快）。

## 直播解码（更多解码器 / 软硬解可选）

直播设置「播放解码」可选 **系统 / ijk硬解 / ijk软解 / exo软解 / exo硬解**，
MPV 版 APK 还会多出 **mpv硬解 / mpv软解**（默认 exo软解）：

| 选项 | 说明 |
| --- | --- |
| 系统 | 系统 MediaPlayer 硬件解码 |
| ijk硬解 | IJK 走 MediaCodec 硬件解码 |
| ijk软解 | IJK（FFmpeg）软件解码，直播参数按低内存调优（关闭 soundtouch、限制输入缓冲） |
| exo软解 | Exo 优先用内置 FFmpeg 软解：视频 H.264 / H.265 / MPEG2 / VP8 / VP9，音频 AC3 / EAC3 / DTS / TrueHD / Opus / FLAC / ALAC 等 |
| exo硬解 | Exo 优先硬件解码（MediaCodec），FFmpeg 软解作为兜底（硬解不支持的编码/音频自动转软解） |
| mpv硬解 | libmpv 用 MediaCodec 解码后拷贝到 GPU（`mediacodec-copy`），兼容性优先 |
| mpv软解 | libmpv 纯 FFmpeg 软解，适合硬解花屏/绿屏的频道，CPU 占用较高 |

exo 软解/硬解共用同一套渲染器（系统硬解 + FFmpeg 软解），区别只在软解优先级：
软解 = FFmpeg 优先，硬解 = MediaCodec 优先、FFmpeg 兜底。软解渲染器做了省内存处理：
FFmpeg 视频软解线程数上限 4、输入/输出缓冲各 2 帧（默认 4/4，4K 下差 40MB 以上），
Exo 直播缓冲区也限制在 2s~20s / 24MB 以内并关闭回看缓冲。

Exo 软解已升级到 nextlib 1.11.1，修复了旧版 FFmpeg 软解的 Surface 颜色（YV12/绿屏/花屏）、
Surface CPU 连接未释放和输出帧清理问题；同时保留硬解失败自动转 FFmpeg 的兜底逻辑。
如果某个频道在 exo/系统硬解下仍然花屏，可直接切到「mpv软解」对比。

手动切换解码后会作为默认沿用，切台不会跳回 exo软解；为单个频道单独选过解码的，
仍优先使用该频道的记忆。软解失败时的自动回退（软解 → 硬解）只是临时生效，不会影响其他频道。

### 标准版 / MPV 版 APK

- **标准版**（文件名不带 `-mpv`）：保持约 17MB，适合大多数设备，内置 exo/ijk/系统播放器。
- **MPV 版**（文件名带 `-mpv`）：额外内置 libmpv 及其 FFmpeg 原生库，每个 ABI 约 +18MB
  （arm64 安装包约 35MB），多出「mpv硬解 / mpv软解」两个选项。
- MPV 版与标准版**同包名、同签名**，可以直接覆盖安装并保留直播源/设置；不想用时再装回标准版即可。
- MPV 版构建时会用 `scripts/patch-mpv-libs.py` 把 libmpv 自带的 FFmpeg 库改名，
  避免与 nextlib 的 `libavcodec.so` 等重名（需要 `patchelf`）。

## 直播源 / EPG 源配置（扫码 + 历史记录）

- 直播设置「直播配置 → 扫码配置」：显示本机二维码（形如 `http://192.168.x.x:9978`），
  手机扫码直接进入纯直播配置页，读取当前直播源 / EPG，分别保存后 App 立即加载；
- 网页提供连接状态、提交中、保存结果和断线 / 超时提示；电视端同步显示收到配置的状态。
  直播源必须填写完整 HTTP(S) 列表地址，EPG 可留空保存以停用自定义节目单；
- 「直播地址」「EPG地址」可以直接在电视上输入，输入框下方的「历史」按钮可查看 /
  选择 / 删除历史记录（各保留最近 20 条；扫码推送、网页推送、手动输入都会记录）；
- 扫码配置对话框里也有「直播源历史 / EPG源历史」按钮；
- 「直播历史」菜单同样可以切换历史直播源；
- 首次启动未配置直播源时会自动弹出扫码配置对话框。

## 分组开关 / 频道显示

- 「直播配置 → 分组开关」：只列出分组（带频道数），取消勾选即关闭整个分组，
  例如一键关闭 4K 分组，确认后立即重建频道列表；
- 「直播配置 → 频道显示」：精确到单个频道的显示/隐藏；
- 分组开关只写分组级隐藏项，不影响「频道显示」里单独隐藏的频道。

## 备份 / 恢复设置

「直播配置 → 备份设置」可以一键备份全部设置（直播源、EPG 源、历史记录、分组开关、
播放解码、每个频道的记忆等），恢复后自动重启应用生效。

- 备份目录：`/sdcard/ylive_backup/`，文件名 `ylive-backup-yyyyMMdd-HHmmss.json`
  （与 Y-TVBOX 的 `/sdcard/tvbox_backup/` **目录和文件名都不重名**，可以放在一起互不影响）；
- 没有存储权限时会自动退到应用私有目录 `Android/data/com.github.ylive.osc/files/ylive_backup/`，
  对话框里会显示实际路径，点「存储权限」可申请；
- 默认保留最近 10 个备份；文件格式与 Y-TVBOX 的 hawk 备份一致，把 Y-TVBOX 备份里的
  `hawk` 文件复制过来改名成 `*.json` 放进目录也能恢复。
- 外置存储不可用时继续回退到应用内部文件目录；无论授权状态如何，列表会合并显示
  当前可读的公共、私有外置和内部目录备份，每个文件显示实际路径，授权后不会丢失旧列表。
- Android 10 及以下申请读写权限；Android 11 及以上仅申请“所有文件访问”。拒绝授权不影响
  直播、扫码配置或私有目录备份，系统授权页不可用时会给出提示。

## GitHub Release

发布工作流构建 `armeabi-v7a` / `arm64-v8a` 的标准版与 MPV 版共 4 个签名 APK
（文件名 `Y-LIVE_...`，MPV 版带 `-mpv`）。进入仓库的 Actions 页面，选择 `Y-LIVE`，
点击 `Run workflow` 即可构建并上传到 GitHub Release。工作流会先安装 `patchelf`
用于 MPV 原生库改名。

未配置签名时，工作流会生成临时签名以完成构建和发布；不同运行生成的临时签名不同，APK 可能无法直接覆盖升级。正式发布请在仓库的 Actions secrets 中配置：

- `YTVBOX_KEYSTORE_BASE64`：JKS/PKCS12 签名文件的 Base64 内容
- `YTVBOX_KEYSTORE_PASSWORD`：签名文件密码
- `YTVBOX_KEY_ALIAS`：签名别名
- `YTVBOX_KEY_PASSWORD`：签名私钥密码

请长期保存同一套签名密钥（和 Y-TVBOX 共用一套也可以），否则已安装版本无法升级。

## IPTV / FCC

y-live 内置原生 FCC（快速换台）与组播播放：带 `?fcc=` 的频道由 App 自己完成
FCC 单播突发、追平后无缝切组播，再经 `127.0.0.1` 本地 HTTP 中继喂给播放器。
直播界面「偏好设置 → FCC快速换台」可开关。

URL 示例、网络前提（组播转发 / FCC 单播 NAT）与日志排查见
[docs/FCC.md](docs/FCC.md)。

---

原项目：https://github.com/takagen99/Box
