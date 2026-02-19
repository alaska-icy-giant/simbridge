"""
Integration tests that run against the live SimBridge Docker container.

Prerequisites:
    docker run -d --name simbridge -p 8100:8100 \
        -e JWT_SECRET=... -v simbridge-data:/app/data simbridge

Run:
    pytest test_container.py -v

These tests execute sequentially and build on each other's state (register →
login → create devices → pair → relay → history).  A module-scoped session
keeps the auth token and device IDs available across all tests.
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
# Module-scoped shared state
# ---------------------------------------------------------------------------

class _State:
    """Mutable bag carried across tests in this module."""
    token: str = ""
    user_id: int = 0
    host_id: int = 0
    client_id: int = 0
    pairing_code: str = ""
    pairing_id: int = 0
    username: str = ""


@pytest.fixture(scope="module")
def state():
    s = _State()
    s.username = f"integ_{uuid.uuid4().hex[:8]}"
    return s


@pytest.fixture(scope="module")
def http():
    with httpx.Client(base_url=BASE_URL, timeout=10) as c:
        yield c


def _headers(state: _State) -> dict:
    return {"Authorization": f"Bearer {state.token}"}


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

class TestAuth:
    def test_register(self, http: httpx.Client, state: _State):
        resp = http.post("/auth/register", json={
            "username": state.username, "password": "integpass",
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["username"] == state.username
        state.user_id = data["id"]

    def test_register_duplicate(self, http: httpx.Client, state: _State):
        resp = http.post("/auth/register", json={
            "username": state.username, "password": "other",
        })
        assert resp.status_code == 400

    def test_login(self, http: httpx.Client, state: _State):
        resp = http.post("/auth/login", json={
            "username": state.username, "password": "integpass",
        })
        assert resp.status_code == 200
        data = resp.json()
        assert "token" in data
        state.token = data["token"]

    def test_login_wrong_password(self, http: httpx.Client, state: _State):
        resp = http.post("/auth/login", json={
            "username": state.username, "password": "wrong",
        })
        assert resp.status_code == 401

    def test_protected_endpoint_no_token(self, http: httpx.Client):
        resp = http.get("/devices")
        assert resp.status_code in (401, 403)


# ---------------------------------------------------------------------------
# Devices
# ---------------------------------------------------------------------------

class TestDevices:
    def test_create_host(self, http: httpx.Client, state: _State):
        resp = http.post("/devices", json={"name": "IntegHost", "type": "host"},
                         headers=_headers(state))
        assert resp.status_code == 200
        data = resp.json()
        assert data["type"] == "host"
        state.host_id = data["id"]

    def test_create_client(self, http: httpx.Client, state: _State):
        resp = http.post("/devices", json={"name": "IntegClient", "type": "client"},
                         headers=_headers(state))
        assert resp.status_code == 200
        data = resp.json()
        assert data["type"] == "client"
        state.client_id = data["id"]

    def test_create_invalid_type(self, http: httpx.Client, state: _State):
        resp = http.post("/devices", json={"name": "X", "type": "bad"},
                         headers=_headers(state))
        assert resp.status_code == 400

    def test_list_devices(self, http: httpx.Client, state: _State):
        resp = http.get("/devices", headers=_headers(state))
        assert resp.status_code == 200
        ids = [d["id"] for d in resp.json()]
        assert state.host_id in ids
        assert state.client_id in ids


# ---------------------------------------------------------------------------
# Pairing
# ---------------------------------------------------------------------------

class TestPairing:
    def test_generate_code(self, http: httpx.Client, state: _State):
        resp = http.post(f"/pair?host_device_id={state.host_id}",
                         headers=_headers(state))
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["code"]) == 6
        state.pairing_code = data["code"]

    def test_confirm_pairing(self, http: httpx.Client, state: _State):
        resp = http.post("/pair/confirm", json={
            "code": state.pairing_code,
            "client_device_id": state.client_id,
        }, headers=_headers(state))
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "paired"
        state.pairing_id = data["pairing_id"]

    def test_already_paired(self, http: httpx.Client, state: _State):
        # Generate a new code and confirm again
        code_resp = http.post(f"/pair?host_device_id={state.host_id}",
                              headers=_headers(state))
        code = code_resp.json()["code"]
        resp = http.post("/pair/confirm", json={
            "code": code, "client_device_id": state.client_id,
        }, headers=_headers(state))
        assert resp.status_code == 200
        assert resp.json()["status"] == "already_paired"

    def test_invalid_code(self, http: httpx.Client, state: _State):
        resp = http.post("/pair/confirm", json={
            "code": "000000", "client_device_id": state.client_id,
        }, headers=_headers(state))
        assert resp.status_code == 400


# ---------------------------------------------------------------------------
# SMS / Call relay — host offline
# ---------------------------------------------------------------------------

class TestRelayOffline:
    def test_sms_host_offline(self, http: httpx.Client, state: _State):
        resp = http.post("/sms", json={
            "to_device_id": state.host_id, "sim": 1, "to": "+155512345", "body": "hi",
        }, headers=_headers(state))
        assert resp.status_code == 503

    def test_call_host_offline(self, http: httpx.Client, state: _State):
        resp = http.post("/call", json={
            "to_device_id": state.host_id, "sim": 1, "to": "+155512345",
        }, headers=_headers(state))
        assert resp.status_code == 503


# ---------------------------------------------------------------------------
# WebSocket
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestWebSocket:
    async def test_host_connects(self, state: _State):
        url = f"{WS_BASE}/ws/host/{state.host_id}?token={state.token}"
        async with websockets.connect(url) as ws:
            msg = json.loads(await ws.recv())
            assert msg["type"] == "connected"
            assert msg["device_id"] == state.host_id

    async def test_client_connects(self, state: _State):
        url = f"{WS_BASE}/ws/client/{state.client_id}?token={state.token}"
        async with websockets.connect(url) as ws:
            msg = json.loads(await ws.recv())
            assert msg["type"] == "connected"
            assert msg["device_id"] == state.client_id

    async def test_invalid_token_rejected(self, state: _State):
        url = f"{WS_BASE}/ws/host/{state.host_id}?token=bad.token.value"
        with pytest.raises(Exception):
            async with websockets.connect(url) as ws:
                await ws.recv()

    async def test_ping_pong(self, state: _State):
        url = f"{WS_BASE}/ws/host/{state.host_id}?token={state.token}"
        async with websockets.connect(url) as ws:
            await ws.recv()  # connected
            await ws.send(json.dumps({"type": "ping"}))
            msg = json.loads(await ws.recv())
            assert msg["type"] == "pong"

    async def test_relay_client_to_host(self, state: _State):
        host_url = f"{WS_BASE}/ws/host/{state.host_id}?token={state.token}"
        client_url = f"{WS_BASE}/ws/client/{state.client_id}?token={state.token}"

        async with websockets.connect(host_url) as ws_host, \
                   websockets.connect(client_url) as ws_client:
            await ws_host.recv()   # connected
            await ws_client.recv() # connected

            await ws_client.send(json.dumps({
                "type": "command", "cmd": "SEND_SMS", "to_device_id": state.host_id,
            }))
            msg = json.loads(await ws_host.recv())
            assert msg["type"] == "command"
            assert msg["cmd"] == "SEND_SMS"
            assert msg["from_device_id"] == state.client_id

    async def test_relay_host_to_client(self, state: _State):
        host_url = f"{WS_BASE}/ws/host/{state.host_id}?token={state.token}"
        client_url = f"{WS_BASE}/ws/client/{state.client_id}?token={state.token}"

        async with websockets.connect(host_url) as ws_host, \
                   websockets.connect(client_url) as ws_client:
            await ws_host.recv()   # connected
            await ws_client.recv() # connected

            await ws_host.send(json.dumps({
                "type": "event", "data": "incoming_sms", "to_device_id": state.client_id,
            }))
            msg = json.loads(await ws_client.recv())
            assert msg["type"] == "event"
            assert msg["from_device_id"] == state.host_id

    async def test_target_offline(self, state: _State):
        client_url = f"{WS_BASE}/ws/client/{state.client_id}?token={state.token}"
        async with websockets.connect(client_url) as ws:
            await ws.recv()  # connected
            await ws.send(json.dumps({"type": "command", "cmd": "GET_SIMS"}))
            msg = json.loads(await ws.recv())
            assert msg["error"] == "target_offline"


# ---------------------------------------------------------------------------
# SMS relay with host online (via WebSocket)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestRelayOnline:
    async def test_sms_relayed_to_host(self, state: _State):
        """Connect host via WebSocket, then POST /sms — host should receive
        the command over the socket and the REST call should return 200."""
        host_url = f"{WS_BASE}/ws/host/{state.host_id}?token={state.token}"
        async with websockets.connect(host_url) as ws_host:
            await ws_host.recv()  # connected

            async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
                resp = await ac.post("/sms", json={
                    "to_device_id": state.host_id, "sim": 1,
                    "to": "+155500000", "body": "integration test",
                }, headers=_headers(state))

            assert resp.status_code == 200
            data = resp.json()
            assert data["status"] == "sent"
            assert "req_id" in data

            msg = json.loads(await ws_host.recv())
            assert msg["cmd"] == "SEND_SMS"
            assert msg["body"] == "integration test"

    async def test_call_relayed_to_host(self, state: _State):
        host_url = f"{WS_BASE}/ws/host/{state.host_id}?token={state.token}"
        async with websockets.connect(host_url) as ws_host:
            await ws_host.recv()  # connected

            async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
                resp = await ac.post("/call", json={
                    "to_device_id": state.host_id, "sim": 2,
                    "to": "+155500001",
                }, headers=_headers(state))

            assert resp.status_code == 200
            assert resp.json()["status"] == "sent"

            msg = json.loads(await ws_host.recv())
            assert msg["cmd"] == "MAKE_CALL"
            assert msg["to"] == "+155500001"


# ---------------------------------------------------------------------------
# History (runs last — verifies logs from relay tests above)
# ---------------------------------------------------------------------------

class TestHistory:
    def test_history_has_entries(self, http: httpx.Client, state: _State):
        resp = http.get("/history", headers=_headers(state))
        assert resp.status_code == 200
        logs = resp.json()
        assert len(logs) > 0

    def test_history_filter_by_device(self, http: httpx.Client, state: _State):
        resp = http.get(f"/history?device_id={state.host_id}",
                        headers=_headers(state))
        assert resp.status_code == 200
        logs = resp.json()
        for log in logs:
            assert state.host_id in (log["from_device_id"], log["to_device_id"])

    def test_device_shows_online_false_after_disconnect(self, http: httpx.Client, state: _State):
        """After all WebSockets have closed, devices should show offline."""
        resp = http.get("/devices", headers=_headers(state))
        devices = {d["id"]: d for d in resp.json()}
        assert devices[state.host_id]["is_online"] is False
        assert devices[state.client_id]["is_online"] is False
