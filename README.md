# y-live

y-live 是 Y-TVBOX 的**纯直播版**：应用 ID 为 `com.github.ylive.osc`，可以和 Y-TVBOX
（`com.github.ytvbox.osc`）同时安装。它只保留直播（Live TV）功能：

- 启动直接进直播界面；
- 不再加载点播数据源（爬虫 / JS 引擎 / P2P / 嗅探内核 / Room 观看记录都不初始化），
  只保留直播需要的本地 HTTP 服务（直播列表代理 + 手机扫码推送）。

## 直播解码（更多解码器 / 软硬解可选）

直播设置「播放解码」可选 **系统 / ijk软解 / exo软解 / exo硬解**（默认 exo软解）：

| 选项 | 说明 |
| --- | --- |
| 系统 | 系统 MediaPlayer 硬件解码 |
| ijk软解 | IJK（FFmpeg）软件解码，直播参数按低内存调优（关闭 soundtouch、限制输入缓冲） |
| exo软解 | Exo 优先用内置 FFmpeg 软解：视频 H.264 / H.265 / MPEG2 / VP8 / VP9，音频 AC3 / EAC3 / DTS / TrueHD / Opus / FLAC / ALAC 等 |
| exo硬解 | Exo 优先硬件解码（MediaCodec），FFmpeg 软解作为兜底（硬解不支持的编码/音频自动转软解） |

软解渲染器做了省内存处理：FFmpeg 视频软解线程数上限 4、输入/输出缓冲各 2 帧
（默认 4/4，4K 下差 40MB 以上），Exo 直播缓冲区也限制在 2s~12s / 16MB 以内。

## 直播源 / EPG 源配置

- 直播设置「直播配置 → 扫码配置」：显示本机二维码（形如 `http://192.168.x.x:9978`），
  手机扫码即可打开网页填写直播源 / EPG 源并提交，App 立即生效；
- 「直播地址」「EPG地址」直接输入，输入框右侧的「历史」按钮可查看 / 选择 / 删除
  历史记录（各保留最近 20 条，扫码推送与手动输入都会记录）；
- 「直播历史」菜单同样可以切换历史直播源。

## 分组开关 / 频道显示

- 「直播配置 → 分组开关」：只列出分组（带频道数），取消勾选即关闭整个分组，
  例如一键关闭 4K 分组，确认后立即重建频道列表；
- 「直播配置 → 频道显示」：精确到单个频道的显示/隐藏；
- 分组开关只写分组级隐藏项，不影响「频道显示」里单独隐藏的频道。

## GitHub Release

发布工作流只构建 `armeabi-v7a` 与 `arm64-v8a` 两个签名 APK（文件名 `Y-LIVE_...`）。
进入仓库的 Actions 页面，选择 `Y-LIVE`，点击 `Run workflow` 即可构建并上传到 GitHub Release。

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
