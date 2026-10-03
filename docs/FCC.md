# 原生 FCC（快速换台）说明

Y-TVBOX 内置了运营商级 FCC（Fast Channel Change）客户端，逻辑参考
[rtp2httpd](https://github.com/stackia/rtp2httpd) 的 `fcc` 模块移植：

- 电信 / 中兴 / 烽火协议（RTCP FMT 2/3/4/5，默认）
- 华为协议（RTCP FMT 5/6/8/9，含 FMT 12 NAT 穿透）
- RTP 乱序重排、初始 8 包收集（防止首包乱序破坏 TS）
- 单播突发追平组播后无缝切流

播放器不需要改动：FCC 与组播流在 App 内合并成 MPEG-TS，由
`127.0.0.1` 上的本地 NanoHTTPD 中继提供，IJK / EXO / 系统播放器直接播放。

## 支持的 URL

```text
rtp://239.11.0.61:5140?fcc=183.221.1.148:8027
udp://@239.11.0.61:5140?fcc=183.221.1.148:8027
igmp://239.11.0.61:5140?fcc=183.221.1.148:8027
http://192.168.7.1:5140/udp/239.11.0.61:5140?fcc=183.221.1.148:8027   # rtp2httpd 兼容写法，App 会绕开代理自己换台
```

- `fcc-type=huawei` 可强制华为协议；缺省为电信协议（与 rtp2httpd 一致）。
- 没有 `fcc=` 参数的 URL 不受影响，继续交给原播放链路。
- 回看 / 时移（`?playseek=`）不会走 FCC。

直播界面「偏好设置 → FCC快速换台」可随时开关：

- 开：带 FCC 信息的频道由 App 自己完成 FCC + 组播，播放器读本地中继；
- 关：完全走原始 URL（例如继续让路由器上的 rtp2httpd 做 FCC）。

任何一步失败都会自动降级：FCC 信令 80ms 超时、单播中断都会回退组播；
连组播也收不到（8s 无数据）时会通知界面回退到原始 URL。

## 网络前提

### 1. 组播可达（必须）

App 要能直接收到 `239.x` 组播，或通过上级的 IGMP 代理收到。本环境
（OpenWrt，`omcproxy br-iptv br-lan`）已满足；普通家用路由器需要在
上级路由器配置 IGMP Proxy（igmpproxy / omcproxy）把 IPTV 组播转发到
播放器所在网段。

### 2. FCC 单播可达（想要加速才需要）

运营商对 IPTV 单播做 IP 源验证，只有拿到 IPTV 租约的地址（`forward`
模式下路由器的 `br-iptv`）才能访问 FCC 服务器。LAN 上的手机 / 盒子想
自己发 FCC 请求，需要在路由器上放行并 SNAT：

```sh
# 放行 LAN -> IPTV 的 FCC 信令（只放 UDP 8027，不需要放开其它单播）
nft insert rule inet fw4 forward \
  iifname "br-lan" oifname "br-iptv" udp dport 8027 counter accept

# 把源地址伪装成 br-iptv 的合法租约地址；daddr 用实际回包的 FCC 地址
nft insert rule inet fw4 srcnat \
  oifname "br-iptv" ip daddr 183.221.1.148 udp dport 8027 counter masquerade
```

> ⚠️ `?fcc=` 必须填**实际回包**的 FCC 服务器 IP。运营商 API / 列表里的
> `ChannelFCCIP` 可能只是负载均衡入口，实际突发来自另一台服务器；回包
> 源 IP 与请求目标不一致时 conntrack 无法把单播流转发回 LAN 客户端。

不做第 2 步时：FCC 信令会被丢，App 在 80ms 后自动回退纯组播，仍能看，
只是失去快速起播。

### 3. WiFi

Android 默认会丢弃组播包，App 已自动申请 `MulticastLock`
（`AndroidManifest.xml` 中的 `CHANGE_WIFI_MULTICAST_STATE`）。

## 日志

```sh
adb logcat -s FccController
```

关键日志：

- `FCC: Unicast stream started successfully` — 单播突发已开始
- `FCC: Switching to multicast stream (reached termination sequence)` — 已切组播
- `FCC: Server response timeout (80 ms), falling back to multicast` — 信令不可达
- `FCC fatal: timeout waiting for FCC/multicast stream` — 组播也不可达，界面回退原始 URL

## 自测

`app/src/test/java/com/github/tvbox/osc/fcc/FccCoreTest.java` 是纯 Java 自测，
覆盖 URL 解析、电信 / 华为协议包编解码、RTP 重排与回绕、裸 TS 透传，以及
本地假 FCC 服务器的完整端到端流程（请求 → 单播突发 → sync → 组播切换 →
终止包）。无 Android 依赖，可把 `com/github/tvbox/osc/fcc` 下的源码与测试
一起用 `javac` 编译后运行 `com.github.tvbox.osc.fcc.FccCoreTest`。
