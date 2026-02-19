"""
End-to-End tests: Host App ↔ SimBridge ↔ Client App

Simulates a two-phone scenario with independent host and client users.
Requires SimBridge container running on localhost:8100.

Run:
    pytest test_e2e.py -v
"""

import asyncio
import json
import uuid

import httpx
import pytest
import websockets

BASE_URL = "http://localhost:8100"
WS_BASE = "ws://localhost:8100"


# ---------------------------------------------------------------------------
# Shared state for the two independent users
# ---------------------------------------------------------------------------

class _UserState:
    def __init__(self, role: str):
        self.role = role
        self.username = f"e2e_{role}_{uuid.uuid4().hex[:8]}"
        self.token: str = ""
        self.user_id: int = 0
        self.device_id: int = 0


class _E2EState:
    def __init__(self):
        self.host = _UserState("host")
        self.client = _UserState("client")
        self.pairing_id: int = 0


@pytest.fixture(scope="module")
def e2e():
    return _E2EState()


@pytest.fixture(scope="module")
def http():
    with httpx.Client(base_url=BASE_URL, timeout=10) as c:
        yield c


def _h(state: _UserState) -> dict:
    return {"Authorization": f"Bearer {state.token}"}


# ---------------------------------------------------------------------------
# Phase 1: Setup — register, login, create devices, pair
# ---------------------------------------------------------------------------

class TestSetup:
    def test_register_host_user(self, http, e2e):
        r = http.post("/auth/register", json={"username": e2e.host.username, "password": "hostpw"})
        assert r.status_code == 200
        e2e.host.user_id = r.json()["id"]

    def test_register_client_user(self, http, e2e):
        r = http.post("/auth/register", json={"username": e2e.client.username, "password": "clientpw"})
        assert r.status_code == 200
        e2e.client.user_id = r.json()["id"]

    def test_login_host(self, http, e2e):
        r = http.post("/auth/login", json={"username": e2e.host.username, "password": "hostpw"})
        assert r.status_code == 200
        e2e.host.token = r.json()["token"]

    def test_login_client(self, http, e2e):
        r = http.post("/auth/login", json={"username": e2e.client.username, "password": "clientpw"})
        assert r.status_code == 200
        e2e.client.token = r.json()["token"]

    def test_create_host_device(self, http, e2e):
        r = http.post("/devices", json={"name": "E2E-HostPhone", "type": "host"}, headers=_h(e2e.host))
        assert r.status_code == 200
        assert r.json()["type"] == "host"
        e2e.host.device_id = r.json()["id"]

    def test_create_client_device(self, http, e2e):
        r = http.post("/devices", json={"name": "E2E-ClientPhone", "type": "client"}, headers=_h(e2e.client))
        assert r.status_code == 200
        assert r.json()["type"] == "client"
        e2e.client.device_id = r.json()["id"]

    def test_pair_devices(self, http, e2e):
        # Host generates pairing code
        r = http.post(f"/pair?host_device_id={e2e.host.device_id}", headers=_h(e2e.host))
        assert r.status_code == 200
        code = r.json()["code"]
        assert len(code) == 6

        # Client confirms pairing
        r = http.post("/pair/confirm", json={
            "code": code, "client_device_id": e2e.client.device_id,
        }, headers=_h(e2e.client))
        assert r.status_code == 200
        assert r.json()["status"] == "paired"
        e2e.pairing_id = r.json()["pairing_id"]


# ---------------------------------------------------------------------------
# Phase 2: SMS relay via REST
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestSmsRelay:
    async def test_sms_delivered_to_host(self, e2e):
        """Client POSTs /sms while host is connected via WebSocket.
        Host should receive the SEND_SMS command."""
        host_url = f"{WS_BASE}/ws/host/{e2e.host.device_id}?token={e2e.host.token}"
        async with websockets.connect(host_url) as ws_host:
            msg = json.loads(await ws_host.recv())
            assert msg["type"] == "connected"

            # Client sends SMS via REST
            async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
                r = await ac.post("/sms", json={
                    "to_device_id": e2e.host.device_id,
                    "sim": 1, "to": "+15550001111", "body": "E2E test message",
                }, headers=_h(e2e.client))
            assert r.status_code == 200
            assert r.json()["status"] == "sent"

            # Host receives command
            cmd = json.loads(await ws_host.recv())
            assert cmd["cmd"] == "SEND_SMS"
            assert cmd["to"] == "+15550001111"
            assert cmd["body"] == "E2E test message"
            assert cmd["sim"] == 1

    async def test_sms_fails_host_offline(self, e2e):
        """POST /sms returns 503 when host has no WebSocket connection."""
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            r = await ac.post("/sms", json={
                "to_device_id": e2e.host.device_id,
                "sim": 1, "to": "+15550002222", "body": "offline test",
            }, headers=_h(e2e.client))
        assert r.status_code == 503


# ---------------------------------------------------------------------------
# Phase 3: Call relay via REST
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestCallRelay:
    async def test_call_delivered_to_host(self, e2e):
        host_url = f"{WS_BASE}/ws/host/{e2e.host.device_id}?token={e2e.host.token}"
        async with websockets.connect(host_url) as ws_host:
            await ws_host.recv()  # connected

            async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
                r = await ac.post("/call", json={
                    "to_device_id": e2e.host.device_id,
                    "sim": 2, "to": "+15550003333",
                }, headers=_h(e2e.client))
            assert r.status_code == 200
            assert r.json()["status"] == "sent"

            cmd = json.loads(await ws_host.recv())
            assert cmd["cmd"] == "MAKE_CALL"
            assert cmd["to"] == "+15550003333"
            assert cmd["sim"] == 2


# ---------------------------------------------------------------------------
# Phase 4: Bidirectional WebSocket relay
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestWebSocketRelay:
    async def test_client_to_host_ws(self, e2e):
        """Client sends a command over WS, host receives it."""
        host_url = f"{WS_BASE}/ws/host/{e2e.host.device_id}?token={e2e.host.token}"
        client_url = f"{WS_BASE}/ws/client/{e2e.client.device_id}?token={e2e.client.token}"

        async with websockets.connect(host_url) as ws_host, \
                   websockets.connect(client_url) as ws_client:
            await ws_host.recv()    # connected
            await ws_client.recv()  # connected

            await ws_client.send(json.dumps({
                "type": "command", "cmd": "GET_SIMS",
                "to_device_id": e2e.host.device_id,
            }))
            msg = json.loads(await ws_host.recv())
            assert msg["type"] == "command"
            assert msg["cmd"] == "GET_SIMS"
            assert msg["from_device_id"] == e2e.client.device_id

    async def test_host_to_client_ws(self, e2e):
        """Host sends an event over WS, client receives it."""
        host_url = f"{WS_BASE}/ws/host/{e2e.host.device_id}?token={e2e.host.token}"
        client_url = f"{WS_BASE}/ws/client/{e2e.client.device_id}?token={e2e.client.token}"

        async with websockets.connect(host_url) as ws_host, \
                   websockets.connect(client_url) as ws_client:
            await ws_host.recv()
            await ws_client.recv()

            await ws_host.send(json.dumps({
                "type": "event", "event": "INCOMING_SMS",
                "from": "+15559999999", "body": "Hello from host",
                "to_device_id": e2e.client.device_id,
            }))
            msg = json.loads(await ws_client.recv())
            assert msg["type"] == "event"
            assert msg["event"] == "INCOMING_SMS"
            assert msg["from_device_id"] == e2e.host.device_id

    async def test_target_offline_error(self, e2e):
        """Client sends WS message when host is not connected."""
        client_url = f"{WS_BASE}/ws/client/{e2e.client.device_id}?token={e2e.client.token}"
        async with websockets.connect(client_url) as ws_client:
            await ws_client.recv()  # connected
            await ws_client.send(json.dumps({
                "type": "command", "cmd": "GET_SIMS",
            }))
            msg = json.loads(await ws_client.recv())
            assert msg["error"] == "target_offline"


# ---------------------------------------------------------------------------
# Phase 5: Ping / Pong
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestPingPong:
    async def test_host_ping(self, e2e):
        url = f"{WS_BASE}/ws/host/{e2e.host.device_id}?token={e2e.host.token}"
        async with websockets.connect(url) as ws:
            await ws.recv()
            await ws.send(json.dumps({"type": "ping"}))
            msg = json.loads(await ws.recv())
            assert msg["type"] == "pong"

    async def test_client_ping(self, e2e):
        url = f"{WS_BASE}/ws/client/{e2e.client.device_id}?token={e2e.client.token}"
        async with websockets.connect(url) as ws:
            await ws.recv()
            await ws.send(json.dumps({"type": "ping"}))
            msg = json.loads(await ws.recv())
            assert msg["type"] == "pong"


# ---------------------------------------------------------------------------
# Phase 6: History
# ---------------------------------------------------------------------------

class TestHistory:
    def test_host_user_sees_history(self, http, e2e):
        r = http.get("/history", headers=_h(e2e.host))
        assert r.status_code == 200
        logs = r.json()
        assert len(logs) > 0
        # All entries should involve the host device
        for log in logs:
            assert e2e.host.device_id in (log["from_device_id"], log["to_device_id"])

    def test_client_user_sees_history(self, http, e2e):
        r = http.get("/history", headers=_h(e2e.client))
        assert r.status_code == 200
        logs = r.json()
        assert len(logs) > 0
        for log in logs:
            assert e2e.client.device_id in (log["from_device_id"], log["to_device_id"])

    def test_history_filter_by_device(self, http, e2e):
        r = http.get(f"/history?device_id={e2e.host.device_id}", headers=_h(e2e.host))
        assert r.status_code == 200
        for log in r.json():
            assert e2e.host.device_id in (log["from_device_id"], log["to_device_id"])

    def test_history_entries_have_payload(self, http, e2e):
        r = http.get("/history?limit=1", headers=_h(e2e.host))
        assert r.status_code == 200
        logs = r.json()
        assert len(logs) >= 1
        entry = logs[0]
        assert "msg_type" in entry
        assert "payload" in entry
        assert "created_at" in entry


# ---------------------------------------------------------------------------
# Phase 7: Device status after disconnect
# ---------------------------------------------------------------------------

class TestDeviceStatus:
    def test_host_offline_after_ws_close(self, http, e2e):
        r = http.get("/devices", headers=_h(e2e.host))
        devices = {d["id"]: d for d in r.json()}
        assert devices[e2e.host.device_id]["is_online"] is False

    def test_client_offline_after_ws_close(self, http, e2e):
        r = http.get("/devices", headers=_h(e2e.client))
        devices = {d["id"]: d for d in r.json()}
        assert devices[e2e.client.device_id]["is_online"] is False
