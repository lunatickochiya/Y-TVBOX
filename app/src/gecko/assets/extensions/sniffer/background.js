// Y-TVBOX Gecko 嗅探扩展:
// 1) 把每个请求的 URL 与关键请求头上报给 App(立即放行, 不阻塞加载)
// 2) 接收 App 的 eval 指令, 在当前页面执行 JS(播放源点击选择器等)

browser.webRequest.onBeforeSendHeaders.addListener(
  function (details) {
    try {
      var headers = {};
      if (details.requestHeaders) {
        for (var i = 0; i < details.requestHeaders.length; i++) {
          var h = details.requestHeaders[i];
          if (h && h.name) {
            headers[h.name.toLowerCase()] = h.value;
          }
        }
      }
      browser.runtime.sendNativeMessage("browser", {
        type: "request",
        url: details.url,
        headers: headers,
        requestType: details.type
      });
    } catch (e) {
      // 上报失败不影响请求
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking", "requestHeaders"]
);

// 与 App 建立 native 端口, 接收 eval 指令
var port = browser.runtime.connectNative("browser");
port.onMessage.addListener(function (msg) {
  if (msg && msg.type === "eval" && msg.code) {
    try {
      var p = browser.tabs.executeScript({ code: msg.code });
      if (p && p.catch) {
        p.catch(function () {});
      }
    } catch (e) {
      // 执行失败忽略
    }
  }
});
