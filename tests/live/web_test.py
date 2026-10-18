#!/usr/bin/env python3
"""Exercise the shipped QR page in Chromium against a local HTTP fixture.

Requires Python Playwright and its Chromium headless shell. The Java handler is
covered separately by run.sh; this fixture controls slow/error HTTP responses.
"""
import json
import pathlib
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

from playwright.sync_api import sync_playwright, expect


ROOT = pathlib.Path(__file__).resolve().parents[2]
RAW = ROOT / "app/src/main/res/raw"
OUT = ROOT / "artifacts/live-cleanup"


def main():
    state = {"liveUrl": "http://example.com/old.m3u", "epgUrl": "http://example.com/old.xml"}
    posts = []
    mode = {"value": "ok"}
    timeout_seen = threading.Event()
    timeout_release = threading.Event()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def respond(self, status, content_type, body):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.end_headers()
            try:
                self.wfile.write(body.encode("utf-8"))
            except BrokenPipeError:
                pass  # A timeout case intentionally closes the client first.

        def do_GET(self):
            if self.path == "/api/config":
                self.respond(200, "application/json", json.dumps(state))
                return
            paths = {"/": ("index.html", "text/html"), "/style.css": ("style.css", "text/css"),
                     "/script.js": ("script.js", "application/javascript")}
            if self.path not in paths:
                self.respond(404, "text/plain", "missing")
                return
            name, mime = paths[self.path]
            self.respond(200, mime + "; charset=utf-8", (RAW / name).read_text())

        def do_POST(self):
            payload = parse_qs(self.rfile.read(int(self.headers["Content-Length"])).decode(), keep_blank_values=True)
            posts.append(payload)
            if mode["value"] == "timeout":
                timeout_seen.set()
                timeout_release.wait(15)
                self.respond(200, "application/json", json.dumps({"ok": True, "message": "late"}))
                return
            if mode["value"] == "invalid-json":
                self.respond(200, "text/html", "unexpected response")
            elif mode["value"] == "error":
                self.respond(503, "application/json", json.dumps({"ok": False, "message": "电视端尚未就绪"}))
            else:
                state[payload["do"][0] + "Url"] = payload["url"][0]
                self.respond(200, "application/json", json.dumps({"ok": True, "message": "已保存到电视，请在电视上查看加载结果"}))

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=1)
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.goto(f"http://127.0.0.1:{server.server_port}/")
            expect(page.locator("#connection")).to_contain_text("已连接电视")
            expect(page.locator("#live-url")).to_have_value("http://example.com/old.m3u")
            page.locator("#live-url").fill("")
            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-result")).to_contain_text("请输入完整")
            assert not posts, "Blank live URL reached the server"

            source = "https://example.com/直播.m3u?token=a&name=中文"
            page.locator("#live-url").fill(" " + source + " ")
            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-result")).to_contain_text("已保存到电视")
            assert posts[-1] == {"do": ["live"], "url": [source]}
            expect(page.locator("#live-current")).to_contain_text(source)

            page.locator("#epg-url").fill("")
            page.get_by_role("button", name="保存节目单", exact=True).click()
            expect(page.locator("#epg-result")).to_contain_text("已保存到电视")
            assert posts[-1] == {"do": ["epg"], "url": [""]}
            expect(page.locator("#epg-current")).to_contain_text("未设置")

            mode["value"] = "error"
            page.locator("#live-url").fill("http://example.com/failure.m3u")
            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-result")).to_contain_text("电视端尚未就绪")
            expect(page.locator("#live-current")).to_contain_text(source)
            expect(page.get_by_role("button", name="保存直播源", exact=True)).to_be_enabled()

            mode["value"] = "invalid-json"
            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-result")).to_contain_text("无法识别")
            mode["value"] = "ok"

            # Keep a pending GET from replacing a URL entered while it was in flight.
            held = []
            page.route("**/api/config", lambda route: held.append(route))
            page.locator("#refresh").click()
            page.locator("#live-url").fill("http://example.com/editing.m3u")
            page.wait_for_function("document.querySelector('#refresh').disabled")
            assert held, "Config GET was not intercepted"
            held.pop().fulfill(status=200, content_type="application/json", body=json.dumps(state))
            expect(page.locator("#refresh")).to_be_enabled()
            expect(page.locator("#live-url")).to_have_value("http://example.com/editing.m3u")
            page.unroute("**/api/config")

            page.route("**/action", lambda route: route.abort())
            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-result")).to_contain_text("无法连接电视")
            page.unroute("**/action")

            mode["value"] = "timeout"
            before_timeout = len(posts)
            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-form button")).to_be_disabled()
            assert timeout_seen.wait(2), "Timeout request did not reach the fixture"
            page.locator("#live-form").dispatch_event("submit")
            page.wait_for_timeout(100)
            assert len(posts) == before_timeout + 1, "Repeated submit issued another pending request"
            expect(page.locator("#live-result")).to_contain_text("连接超时", timeout=12000)
            expect(page.locator("#live-form button")).to_be_enabled()
            mode["value"] = "ok"
            timeout_release.set()

            page.get_by_role("button", name="保存直播源", exact=True).click()
            expect(page.locator("#live-result")).to_contain_text("已保存到电视")
            page.screenshot(path=str(OUT / "qr-mobile.png"), full_page=True)
            for width, height in [(320, 740), (390, 844), (1280, 900)]:
                page.set_viewport_size({"width": width, "height": height})
                assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), f"Horizontal overflow at {width}px"
            page.screenshot(path=str(OUT / "qr-desktop.png"), full_page=True)
            page.emulate_media(color_scheme="dark")
            page.set_viewport_size({"width": 390, "height": 844})
            page.screenshot(path=str(OUT / "qr-mobile-dark.png"), full_page=True)
            assert not errors, errors
            browser.close()
        print("PASS: QR page current config, validation, Unicode submission, EPG clear, error/timeout recovery, duplicate-submit guard, slow GET edit preservation, mobile/desktop layouts")
    finally:
        timeout_release.set()
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
