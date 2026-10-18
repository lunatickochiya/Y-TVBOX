# 2026-10-18 设置与播放修复验证

## 修复范围

参考 kukuqi666/TVboxOSC 的设置快照、页面刷新和真实首帧处理方式，保留本项目的播放器和各分支默认值。
参考版本为 `30ac81a`：<https://github.com/kukuqi666/TVboxOSC/tree/30ac81a>。

- 设置返回使用已保存配置的变化决定是否刷新，并在请求刷新前消费变化。
- 常规设置、直播地址和 DNS 修改不再触发点播首页整体重启；内容、主题、语言使用受控页面重建，API 变化重新加载配置。
- 正常 Home 销毁不退出进程或关闭进程共享的本地 HTTP 服务。页面重建延后到 onResume 返回之后，避免旧 Android 的恢复/暂停时序问题。
- API 变化清理旧站点、分类、扩展缓存、爬虫、IJK 配置和规则。配置/JAR/分类的异步响应必须仍属于当前请求，才允许更新共享状态或缓存。
- 直播维护唯一计时任务，退出后清理并拒绝延迟回调；暂停后恢复准备/缓冲超时。
- Exo 使用真实首帧确认画面输出；每次播放重置首帧和尺寸信息，重播保留已绑定的 Surface。
- 自动回退解码与用户保存的频道配置分开，调整画面比例不再把临时回退保存为默认值。

## main 验证结果

USB 设备为 K-Touch8818，Android 6.1 / API 23，32 位 ARM。没有连接电视调试。

1. 原 APK 修改首页推荐后返回：进程 PID 从 941 变为 1615，复现异常进程重启。
2. 修复版不修改设置、修改推荐、恢复推荐、主题切换及恢复、语言切换及恢复：保持同一进程。最终回归 PID 为 12633。
3. 两个本地测试 API 使用相同站点键和不同分类，切换后正确显示各自分类。每个 API 的配置与分类请求各一次；不改设置再次返回没有额外请求。本地 HTTP 服务在页面重建后仍可访问。
4. 初次集成在 API 23 直接从 onResume 重建页面会停在加载画面，并出现 ActivityThread 的恢复/暂停警告。延后重建后，上述 API 切换通过，未再观察到该警告。
5. 睛彩广场舞有持续变化的画面，AudioFlinger 显示应用的活动双声道音轨。Exo 软解可出画面；用户观察到该旧手机软解卡顿，恢复原 Exo 硬解后，用户确认正常、无卡顿。最终保留硬解，不修改全局解码默认值。
6. 直播退出后等待 8 秒，再进入直播：进程保持不变，重新有画面及活动音轨。

已恢复原 API、主题、语言设置。临时 API 历史记录在收尾时删除。

Java 8 兼容的独立 JVM 回归：

- HomeSettings：38 项检查通过。
- LatestRequest：8 项检查通过。
- PlaybackTaskQueue、PlaybackRecovery、LivePlayerManager、PlaybackOutputState：4 组通过。

可使用 JDK 17 运行 `bash tests/settings/run.sh` 和 `bash tests/playback/run.sh`。
`JAVAC`、`JAVA` 可指定 JDK 路径，播放测试也接受 `JSON_JAR`。

main 的 armeabi-v7a 与 arm64 normal release APK 已完成签名构建。USB 安装使用原发布签名，保留应用数据。

## 分支移植

| 分支 | 代码提交 | 范围 |
| --- | --- | --- |
| main | 3f1eadf、3eb552f、1f9fbc4 | 设置与播放完整修复 |
| dev | 0aa8e59、b6a81bb、d219086、0c09d88 | 完整修复，保留 DeviceCapability 调优 |
| live | c233595 | 共享播放修复，保留扫码、备份、默认值及独立直播配置 |
| y-tvbox-android-4.4 | 8498b6d、60ed630、e90cbcf、57eedf4 | 完整修复，保留 API 19 与原依赖配置 |

`live-slim` 按要求排除，仍为 `a4a3e39`。不替换各产品的 build.gradle、settings.gradle 或平台差异代码。
按用户最后要求，后续只在 main 验证；其他分支移植提交后由用户自行编译验收。

## 验证边界

这部 USB 手机不能验证 Android 17 vivo 或 API 19 设备行为。没有进行现代 arm64 实机测试，也没有测量解码帧率或功耗提升。
本轮可确认的性能改进来自去除重复任务、减少无效重启和保留重播 Surface；软解仍取决于设备 CPU 能力。
独立 JVM 测试覆盖决策与请求所有权，不能代替 Android、Surface 和原生解码器实机测试。
