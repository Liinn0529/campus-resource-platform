#!/usr/bin/env python3
"""Loopback-only teaching console. All business results come from the existing HTTP APIs."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import ipaddress
import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
from urllib.parse import urlencode
from uuid import uuid4
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("shared_smoke", ROOT / "scripts/smoke-phase-3.py")
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)
STATE_FILE = ROOT / ".local/member01-demo-state.json"
LOCK = threading.RLock()


class Demo:
    def __init__(self):
        self.env = shared.private_environment()
        self.public = shared.Platform("http://127.0.0.1:" + self.env.get("WEB_PORT", "18080"), [])
        self.token = None
        self.internal = None
        self.data = json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else None

    def save(self):
        STATE_FILE.parent.mkdir(exist_ok=True)
        temporary = STATE_FILE.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.data, ensure_ascii=False), encoding="utf-8")
        temporary.replace(STATE_FILE)

    def login(self):
        self.token = self.public.login("demo_student_01", self.env["DEV_SEED_PASSWORD"], "STUDENT")

    def call(self, method, path, body=None, module=None):
        if module:
            if self.internal is None:
                container = subprocess.check_output(["docker", "compose", "ps", "-q", "reservation-service"], cwd=ROOT, text=True).strip()
                shared.require(bool(container) and "\n" not in container, "当前项目中没有唯一的 M1 容器")
                address = subprocess.check_output(["docker", "inspect", "--format",
                    "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", container], text=True).strip()
                shared.require(ipaddress.ip_address(address).is_private, "M1 必须位于 Docker 私有网络")
                self.internal = shared.Platform("http://" + address + ":8080", [])
            api = self.internal
            token = self.env["M1_CLASSROOM_TOKEN" if module == "M2" else "M1_MAINTENANCE_TOKEN"]
        else:
            if self.token is None:
                self.login()
            api, token = self.public, self.token
        status, raw, headers = api.raw(method, path, body, token)
        if status == 401 and not module:
            self.login()
            status, raw, headers = api.raw(method, path, body, self.token)
        envelope = json.loads(raw)
        shared.require(envelope.get("requestId") == headers.get("X-Request-ID") and envelope.get("requestId"), "后端回执缺少关联 ID")
        return {"http": status, "method": method, "path": path, "body": envelope}

    def value(self, path):
        response = self.call("GET", path)
        shared.require(response["http"] == 200, "读取真实业务数据失败：HTTP " + str(response["http"]))
        return response["body"]["data"]

    def initialize(self):
        rooms = self.value("/api/v1/reservation/rooms?limit=100")
        room = next((r for r in rooms if r["type"] == "CLASSROOM" and r["enabled"]), None)
        shared.require(room is not None, "没有可演示的启用教室")
        start = datetime.now(ZoneInfo(self.env["CAMPUS_BUSINESS_ZONE"])).replace(minute=0, second=0, microsecond=0) + timedelta(hours=2)
        self.data = {"room": room, "startAt": start.isoformat(), "endAt": (start + timedelta(hours=1)).isoformat(),
                     "prefix": "member01-demo:" + uuid4().hex[:20], "sources": {}, "booking": None, "events": []}
        self.save()

    def state(self):
        if self.data is None:
            self.initialize()
        query = urlencode({"startAt": self.data["startAt"], "endAt": self.data["endAt"]})
        availability = self.value(f"/api/v1/reservation/rooms/{self.data['room']['id']}/availability?{query}")
        if self.data["booking"]:
            self.data["booking"] = self.value("/api/v1/reservations/" + str(self.data["booking"]["id"]))
        return {**self.data, "seats": availability["seats"], "checkedAt": datetime.now().astimezone().isoformat()}

    def block(self, key, module):
        if key not in self.data["sources"]:
            payload = {"roomId": self.data["room"]["id"], "startAt": self.data["startAt"],
                "endAt": self.data["endAt"] if module == "M2" else None, "sourceModule": module,
                "sourceType": "CLASSROOM_BOOKING" if module == "M2" else "MAINTENANCE",
                "sourceRefId": self.data["prefix"] + ":" + key}
            self.data["sources"][key] = {"payload": payload, "receipt": None}
            self.save()  # Keep the source before HTTP so an unknown acknowledgement can be recovered.
        item = self.data["sources"][key]
        result = self.call("POST", "/internal/v1/room-blocks", item["payload"], module)
        if result["http"] in (200, 201):
            item["receipt"] = result["body"]["data"]
        self.save()
        return result

    def release(self, key):
        shared.require(key in self.data["sources"], "请先发起对应的占用请求")
        item = self.data["sources"][key]
        payload = item["payload"]
        result = self.call("DELETE", "/internal/v1/room-blocks/" + payload["sourceModule"] + "/" + payload["sourceRefId"], module=payload["sourceModule"])
        if result["http"] == 200:
            item["receipt"] = result["body"]["data"]
        self.save()
        return result

    def action(self, action, seat):
        current = self.state()
        labels = {"reserve": "学生预约一个座位", "cancel": "取消本次学生预约", "classroom": "教室整房占用",
                  "repeat": "重复发送同一占用", "release_classroom": "释放教室占用", "maintenance": "维修封锁房间",
                  "release_maintenance": "释放维修占用", "race": "六个不同来源并发抢占", "reset": "清理本次占用，开始新一轮"}
        shared.require(action in labels, "未知操作")
        if action == "reset":
            results = []
            for key in list(self.data["sources"]):
                result = self.release(key)
                shared.require(result["http"] in (200, 404), "清理失败，保留本轮记录以便重试")
                results.append(result)
            if self.data["booking"] and self.data["booking"]["status"] == "CREATED":
                result = self.call("POST", f"/api/v1/reservations/{self.data['booking']['id']}/cancel")
                shared.require(result["http"] == 200, "学生预约无法取消，未丢弃本轮记录")
                results.append(result)
            self.initialize()
        elif action == "reserve":
            shared.require(any(s["id"] == seat for s in current["seats"]), "请选择页面上的真实座位")
            shared.require(not self.data["booking"] or self.data["booking"]["status"] != "CREATED", "本轮已有学生预约，请先取消")
            result = self.call("POST", "/api/v1/reservations", {"roomId": self.data["room"]["id"], "seatId": seat,
                        "startAt": self.data["startAt"], "endAt": self.data["endAt"]})
            if result["http"] == 201:
                self.data["booking"] = result["body"]["data"]
            results = [result]
        elif action == "cancel":
            shared.require(self.data["booking"] is not None, "本轮尚未创建学生预约")
            result = self.call("POST", f"/api/v1/reservations/{self.data['booking']['id']}/cancel")
            if result["http"] == 200:
                self.data["booking"] = result["body"]["data"]
            results = [result]
        elif action in ("classroom", "repeat", "maintenance"):
            results = [self.block("maintenance" if action == "maintenance" else "classroom", "M3" if action == "maintenance" else "M2")]
        elif action in ("release_classroom", "release_maintenance"):
            results = [self.release(action.removeprefix("release_"))]
        else:
            # Prepare all recoverable source keys before parallel calls; no shared mutation in workers.
            keys = ["race" + str(i) for i in range(6)]
            for key in keys:
                self.data["sources"][key] = {"payload": {"roomId": self.data["room"]["id"], "startAt": self.data["startAt"],
                    "endAt": self.data["endAt"], "sourceModule": "M2", "sourceType": "CLASSROOM_BOOKING",
                    "sourceRefId": self.data["prefix"] + ":" + key}, "receipt": None}
            self.save()
            with ThreadPoolExecutor(max_workers=6) as pool:
                results = list(pool.map(lambda key: self.call("POST", "/internal/v1/room-blocks", self.data["sources"][key]["payload"], "M2"), keys))
            for key, result in zip(keys, results):
                if result["http"] in (200, 201):
                    self.data["sources"][key]["receipt"] = result["body"]["data"]
        self.data["events"].insert(0, {"label": labels[action], "time": datetime.now().strftime("%H:%M:%S"), "results": results})
        self.data["events"] = self.data["events"][:30]
        self.save()
        return self.state()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def send(self, status, body, kind="application/json; charset=utf-8"):
        encoded = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", kind)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; connect-src 'self'")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def allowed(self):
        return self.headers.get("Host") in ("127.0.0.1:" + str(self.server.server_port), "localhost:" + str(self.server.server_port))

    def do_GET(self):
        if not self.allowed():
            return self.send(403, {"error": "仅允许本机访问"})
        if self.path == "/":
            html = Path(__file__).with_name("index.html").read_text(encoding="utf-8").replace("__CSRF__", self.server.nonce)
            return self.send(200, html.encode(), "text/html; charset=utf-8")
        if self.path != "/api/state":
            return self.send(404, {"error": "不存在"})
        self.handle_call(lambda: self.server.demo.state())

    def do_POST(self):
        origin = self.headers.get("Origin")
        if not self.allowed() or self.headers.get("X-Demo-Nonce") != self.server.nonce or (
                origin and origin != "http://" + self.headers.get("Host", "")):
            return self.send(403, {"error": "请求不是来自本机演示页面"})
        if self.path != "/api/action":
            return self.send(404, {"error": "不存在"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 1024:
                raise ValueError()
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict) or set(body) - {"action", "seatId"}:
                raise ValueError()
        except (ValueError, TypeError):
            return self.send(400, {"error": "请求格式错误"})
        self.handle_call(lambda: self.server.demo.action(body.get("action"), body.get("seatId")))

    def handle_call(self, operation):
        with LOCK:
            try:
                return self.send(200, operation())
            except shared.SmokeFailure as failure:
                return self.send(400, {"error": str(failure)})
            except Exception as failure:
                # Boundary handler: do not expose credentials, response bodies or subprocess diagnostics.
                self.server.demo.internal = None
                return self.send(503, {"error": "真实服务暂不可用或回执未知，请刷新核实；不要当作操作成功。", "type": type(failure).__name__})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=18081)
    args = parser.parse_args()
    os.environ.setdefault("COMPOSE_PROJECT_NAME", "campus-member-01")
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.nonce = secrets.token_urlsafe(32)
    server.demo = Demo()
    print(f"成员一真实接口演示：http://127.0.0.1:{args.port}/", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
