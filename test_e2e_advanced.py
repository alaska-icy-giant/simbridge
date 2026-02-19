"""
Advanced E2E tests: additional scenarios beyond test_e2e.py.

Requires SimBridge container running on localhost:8100.

Run:
    pytest test_e2e_advanced.py -v
"""

import asyncio
import json
import os
import time
import uuid

import httpx
import pytest
import websockets

BASE_URL = "http://localhost:8100"
WS_BASE = "ws://localhost:8100"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class _UserState:
    def __init__(self, role: str, suffix: str = ""):
        tag = suffix or uuid.uuid4().hex[:8]
        self.role = role
        self.username = f"adv_{role}_{tag}"
        self.password = f"{role}pw"
        self.token: str = ""
        self.user_id: int = 0
        self.device_id: int = 0


def _h(state: _UserState) -> dict:
    return {"Authorization": f"Bearer {state.token}"}


def _register_and_login(client: httpx.Client, state: _UserState):
    """Register, login, and populate state with token + user_id."""
    r = client.post("/auth/register", json={
        "username": state.username, "password": state.password,
    })
    assert r.status_code == 200
    state.user_id = r.json()["id"]

    r = client.post("/auth/login", json={
        "username": state.username, "password": state.password,
    })
    assert r.status_code == 200
    state.token = r.json()["token"]


def _create_device(client: httpx.Client, state: _UserState, device_type: str):
    """Create a device and store its ID in state."""
    r = client.post("/devices", json={
        "name": f"Adv-{device_type.title()}", "type": device_type,
    }, headers=_h(state))
    assert r.status_code == 200
    state.device_id = r.json()["id"]


def _pair_devices(
    client: httpx.Client, host: _UserState, cli: _UserState,
) -> int:
    """Generate pairing code on host side and confirm from client side.
    Both users must be the same account for SimBridge pairing to work."""
    r = client.post(
        f"/pair?host_device_id={host.device_id}", headers=_h(host),
    )
    assert r.status_code == 200
    code = r.json()["code"]

    r = client.post("/pair/confirm", json={
        "code": code, "client_device_id": cli.device_id,
    }, headers=_h(cli))
    assert r.status_code == 200
    return r.json()["pairing_id"]


# ---------------------------------------------------------------------------
# Module-scoped fixtures — primary test pair (single user, host + client)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def http():
    with httpx.Client(base_url=BASE_URL, timeout=10) as c:
        yield c


@pytest.fixture(scope="module")
def user_a(http):
    """Single user account with host and client devices, paired."""
    host = _UserState("host", f"a_{uuid.uuid4().hex[:6]}")
    cli = _UserState("client", f"a_{uuid.uuid4().hex[:6]}")
    # Use same username for both so pairing works (same user owns both)
    shared_name = f"adv_user_a_{uuid.uuid4().hex[:6]}"
    host.username = shared_name
    cli.username = shared_name
    host.password = "userapw"
    cli.password = "userapw"

    # Register once
    r = http.post("/auth/register", json={
        "username": shared_name, "password": "userapw",
    })
    assert r.status_code == 200
    uid = r.json()["id"]

    # Login once to get token
    r = http.post("/auth/login", json={
        "username": shared_name, "password": "userapw",
    })
    assert r.status_code == 200
    token = r.json()["token"]

    host.user_id = uid
    host.token = token
    cli.user_id = uid
    cli.token = token

    _create_device(http, host, "host")
    _create_device(http, cli, "client")
    _pair_devices(http, host, cli)

    return {"host": host, "client": cli}


# ---------------------------------------------------------------------------
# TestConcurrentConnections
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestConcurrentConnections:
    """Multiple host+client pairs connected simultaneously;
    messages route to correct pairs only."""

    async def test_messages_route_to_correct_pair(self):
        """Create two independent user accounts, each with a host/client
        pair. Send messages and verify no cross-talk."""
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            # --- Set up Pair A ---
            name_a = f"conc_a_{uuid.uuid4().hex[:6]}"
            r = await ac.post("/auth/register", json={
                "username": name_a, "password": "pw",
            })
            assert r.status_code == 200
            r = await ac.post("/auth/login", json={
                "username": name_a, "password": "pw",
            })
            token_a = r.json()["token"]
            hdrs_a = {"Authorization": f"Bearer {token_a}"}

            r = await ac.post("/devices", json={"name": "H-A", "type": "host"}, headers=hdrs_a)
            host_a_id = r.json()["id"]
            r = await ac.post("/devices", json={"name": "C-A", "type": "client"}, headers=hdrs_a)
            client_a_id = r.json()["id"]

            r = await ac.post(f"/pair?host_device_id={host_a_id}", headers=hdrs_a)
            code_a = r.json()["code"]
            r = await ac.post("/pair/confirm", json={
                "code": code_a, "client_device_id": client_a_id,
            }, headers=hdrs_a)
            assert r.json()["status"] == "paired"

            # --- Set up Pair B ---
            name_b = f"conc_b_{uuid.uuid4().hex[:6]}"
            r = await ac.post("/auth/register", json={
                "username": name_b, "password": "pw",
            })
            assert r.status_code == 200
            r = await ac.post("/auth/login", json={
                "username": name_b, "password": "pw",
            })
            token_b = r.json()["token"]
            hdrs_b = {"Authorization": f"Bearer {token_b}"}

            r = await ac.post("/devices", json={"name": "H-B", "type": "host"}, headers=hdrs_b)
            host_b_id = r.json()["id"]
            r = await ac.post("/devices", json={"name": "C-B", "type": "client"}, headers=hdrs_b)
            client_b_id = r.json()["id"]

            r = await ac.post(f"/pair?host_device_id={host_b_id}", headers=hdrs_b)
            code_b = r.json()["code"]
            r = await ac.post("/pair/confirm", json={
                "code": code_b, "client_device_id": client_b_id,
            }, headers=hdrs_b)
            assert r.json()["status"] == "paired"

        # --- Connect all four WebSockets ---
        ws_host_a_url = f"{WS_BASE}/ws/host/{host_a_id}?token={token_a}"
        ws_client_a_url = f"{WS_BASE}/ws/client/{client_a_id}?token={token_a}"
        ws_host_b_url = f"{WS_BASE}/ws/host/{host_b_id}?token={token_b}"
        ws_client_b_url = f"{WS_BASE}/ws/client/{client_b_id}?token={token_b}"

        async with (
            websockets.connect(ws_host_a_url) as ws_ha,
            websockets.connect(ws_client_a_url) as ws_ca,
            websockets.connect(ws_host_b_url) as ws_hb,
            websockets.connect(ws_client_b_url) as ws_cb,
        ):
            # Drain "connected" messages
            for ws in (ws_ha, ws_ca, ws_hb, ws_cb):
                msg = json.loads(await ws.recv())
                assert msg["type"] == "connected"

            # Client A sends command to Host A
            await ws_ca.send(json.dumps({
                "type": "command", "cmd": "PAIR_A_CMD",
                "to_device_id": host_a_id,
            }))
            msg_ha = json.loads(await ws_ha.recv())
            assert msg_ha["cmd"] == "PAIR_A_CMD"
            assert msg_ha["from_device_id"] == client_a_id

            # Client B sends command to Host B
            await ws_cb.send(json.dumps({
                "type": "command", "cmd": "PAIR_B_CMD",
                "to_device_id": host_b_id,
            }))
            msg_hb = json.loads(await ws_hb.recv())
            assert msg_hb["cmd"] == "PAIR_B_CMD"
            assert msg_hb["from_device_id"] == client_b_id

            # Verify Host B did NOT receive Pair A's message and vice versa.
            # Use a short timeout to confirm no stray messages.
            for ws, label in [(ws_hb, "Host B after A cmd"), (ws_ha, "Host A after B cmd")]:
                try:
                    extra = await asyncio.wait_for(ws.recv(), timeout=0.5)
                    # If we got something, it is a cross-talk bug
                    pytest.fail(f"Unexpected message on {label}: {extra}")
                except (asyncio.TimeoutError, TimeoutError):
                    pass  # Good — no cross-talk


# ---------------------------------------------------------------------------
# TestReconnection
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestReconnection:
    """Force-close a WebSocket from the client side, verify the server
    cleans up the connection (device goes offline)."""

    async def test_host_goes_offline_after_ws_close(self, user_a):
        host = user_a["host"]
        url = f"{WS_BASE}/ws/host/{host.device_id}?token={host.token}"

        # Connect
        ws = await websockets.connect(url)
        msg = json.loads(await ws.recv())
        assert msg["type"] == "connected"

        # Verify online via REST
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            r = await ac.get("/devices", headers=_h(host))
            devices = {d["id"]: d for d in r.json()}
            assert devices[host.device_id]["is_online"] is True

        # Force close from client side
        await ws.close()

        # Small delay for server cleanup
        await asyncio.sleep(0.3)

        # Verify offline via REST
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            r = await ac.get("/devices", headers=_h(host))
            devices = {d["id"]: d for d in r.json()}
            assert devices[host.device_id]["is_online"] is False


# ---------------------------------------------------------------------------
# TestRateLimiting
# ---------------------------------------------------------------------------

class TestRateLimiting:
    """Test that excessive login attempts get HTTP 429.
    Requires the container to be built with rate limiting enabled in main.py."""

    @pytest.mark.skipif(
        not os.environ.get("SIMBRIDGE_RATE_LIMIT_ENABLED"),
        reason="Rate limiting not confirmed on container (set SIMBRIDGE_RATE_LIMIT_ENABLED=1)",
    )
    def test_login_rate_limit(self, http):
        """Hammer the login endpoint with wrong passwords until we get 429."""
        username = f"ratelim_{uuid.uuid4().hex[:8]}"
        # Register the user first so the endpoint processes the attempt
        r = http.post("/auth/register", json={
            "username": username, "password": "correctpw",
        })
        assert r.status_code == 200

        hit_429 = False
        for i in range(10):
            r = http.post("/auth/login", json={
                "username": username, "password": "wrongpw",
            })
            if r.status_code == 429:
                hit_429 = True
                break

        assert hit_429, "Expected HTTP 429 after repeated failed logins"


# ---------------------------------------------------------------------------
# TestLargePayload
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestLargePayload:
    """Send SMS with maximum body length (1600 chars) and verify relay."""

    async def test_sms_max_body_length(self, user_a):
        host = user_a["host"]
        cli = user_a["client"]
        body_1600 = "A" * 1600

        url = f"{WS_BASE}/ws/host/{host.device_id}?token={host.token}"
        async with websockets.connect(url) as ws_host:
            await ws_host.recv()  # connected

            async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
                r = await ac.post("/sms", json={
                    "to_device_id": host.device_id,
                    "sim": 1,
                    "to": "+15551234567",
                    "body": body_1600,
                }, headers=_h(cli))
            assert r.status_code == 200
            assert r.json()["status"] == "sent"

            cmd = json.loads(await ws_host.recv())
            assert cmd["cmd"] == "SEND_SMS"
            assert cmd["body"] == body_1600
            assert len(cmd["body"]) == 1600


# ---------------------------------------------------------------------------
# TestHistoryPagination
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestHistoryPagination:
    """Create many messages and verify the limit parameter works."""

    async def test_history_limit(self, user_a):
        host = user_a["host"]
        cli = user_a["client"]

        # Generate several SMS messages
        url = f"{WS_BASE}/ws/host/{host.device_id}?token={host.token}"
        async with websockets.connect(url) as ws_host:
            await ws_host.recv()  # connected

            for i in range(5):
                async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
                    r = await ac.post("/sms", json={
                        "to_device_id": host.device_id,
                        "sim": 1,
                        "to": f"+1555000{i:04d}",
                        "body": f"Pagination test message {i}",
                    }, headers=_h(cli))
                assert r.status_code == 200
                # Drain the WS message
                await ws_host.recv()

        # Now fetch with limit=2
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            r = await ac.get("/history?limit=2", headers=_h(host))
        assert r.status_code == 200
        logs = r.json()
        assert len(logs) == 2

        # Fetch with limit=200 (max) — should return all
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            r = await ac.get("/history?limit=200", headers=_h(host))
        assert r.status_code == 200
        all_logs = r.json()
        assert len(all_logs) >= 5


# ---------------------------------------------------------------------------
# TestInvalidPairingCode
# ---------------------------------------------------------------------------

class TestInvalidPairingCode:
    """Try to confirm with wrong code and already-used code."""

    def test_wrong_code(self, http, user_a):
        cli = user_a["client"]
        r = http.post("/pair/confirm", json={
            "code": "000000",
            "client_device_id": cli.device_id,
        }, headers=_h(cli))
        assert r.status_code == 400

    def test_already_used_code(self, http, user_a):
        """Generate a code, use it, then try to use the same code again."""
        host = user_a["host"]
        cli = user_a["client"]

        # Generate a new code
        r = http.post(
            f"/pair?host_device_id={host.device_id}", headers=_h(host),
        )
        assert r.status_code == 200
        code = r.json()["code"]

        # Confirm (already paired, so returns "already_paired" — that is fine)
        r = http.post("/pair/confirm", json={
            "code": code, "client_device_id": cli.device_id,
        }, headers=_h(cli))
        assert r.status_code == 200

        # Try the same code again — it was marked used
        r = http.post("/pair/confirm", json={
            "code": code, "client_device_id": cli.device_id,
        }, headers=_h(cli))
        # Should fail because code is now used
        assert r.status_code == 400

    def test_six_zeros_rejected(self, http, user_a):
        """Brute-force-style code should not accidentally match."""
        cli = user_a["client"]
        r = http.post("/pair/confirm", json={
            "code": "999999",
            "client_device_id": cli.device_id,
        }, headers=_h(cli))
        assert r.status_code == 400


# ---------------------------------------------------------------------------
# TestDeviceIsolation
# ---------------------------------------------------------------------------

class TestDeviceIsolation:
    """User A's devices should not be accessible by user B's token."""

    def test_user_b_cannot_see_user_a_devices(self, http, user_a):
        host_a = user_a["host"]

        # Create a completely separate user B
        name_b = f"iso_b_{uuid.uuid4().hex[:6]}"
        r = http.post("/auth/register", json={
            "username": name_b, "password": "bpw",
        })
        assert r.status_code == 200
        r = http.post("/auth/login", json={
            "username": name_b, "password": "bpw",
        })
        token_b = r.json()["token"]
        hdrs_b = {"Authorization": f"Bearer {token_b}"}

        # User B lists devices — should NOT see user A's devices
        r = http.get("/devices", headers=hdrs_b)
        assert r.status_code == 200
        b_device_ids = [d["id"] for d in r.json()]
        assert host_a.device_id not in b_device_ids

    def test_user_b_cannot_send_sms_to_user_a_host(self, http, user_a):
        host_a = user_a["host"]

        # Create user B with a client device
        name_b = f"iso_sms_{uuid.uuid4().hex[:6]}"
        r = http.post("/auth/register", json={
            "username": name_b, "password": "bpw",
        })
        assert r.status_code == 200
        r = http.post("/auth/login", json={
            "username": name_b, "password": "bpw",
        })
        token_b = r.json()["token"]
        hdrs_b = {"Authorization": f"Bearer {token_b}"}

        # Create a client device for user B
        r = http.post("/devices", json={"name": "B-Client", "type": "client"}, headers=hdrs_b)
        assert r.status_code == 200

        # User B tries to SMS user A's host — should be rejected
        r = http.post("/sms", json={
            "to_device_id": host_a.device_id,
            "sim": 1,
            "to": "+15550000000",
            "body": "sneaky",
        }, headers=hdrs_b)
        # Should be 403 (not paired) or similar error, not 200
        assert r.status_code in (403, 400, 404)


# ---------------------------------------------------------------------------
# TestWebSocketAuthFailure
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestWebSocketAuthFailure:
    """Connect with expired/invalid token; verify connection rejected."""

    async def test_invalid_token_rejected(self, user_a):
        host = user_a["host"]
        url = f"{WS_BASE}/ws/host/{host.device_id}?token=invalid.jwt.token"
        with pytest.raises(Exception):
            async with websockets.connect(url) as ws:
                await ws.recv()

    async def test_empty_token_rejected(self, user_a):
        host = user_a["host"]
        url = f"{WS_BASE}/ws/host/{host.device_id}?token="
        with pytest.raises(Exception):
            async with websockets.connect(url) as ws:
                await ws.recv()

    async def test_wrong_device_type_rejected(self, user_a):
        """Try to connect a client device to the /ws/host endpoint."""
        cli = user_a["client"]
        # Client device connecting to host endpoint should fail
        url = f"{WS_BASE}/ws/host/{cli.device_id}?token={cli.token}"
        with pytest.raises(Exception):
            async with websockets.connect(url) as ws:
                await ws.recv()

    async def test_other_users_device_rejected(self):
        """User B should not be able to connect to User A's device WS."""
        async with httpx.AsyncClient(base_url=BASE_URL, timeout=10) as ac:
            # Create user A with a host
            name_a = f"wsauth_a_{uuid.uuid4().hex[:6]}"
            await ac.post("/auth/register", json={
                "username": name_a, "password": "pw",
            })
            r = await ac.post("/auth/login", json={
                "username": name_a, "password": "pw",
            })
            token_a = r.json()["token"]
            r = await ac.post("/devices", json={"name": "H", "type": "host"},
                              headers={"Authorization": f"Bearer {token_a}"})
            device_a = r.json()["id"]

            # Create user B
            name_b = f"wsauth_b_{uuid.uuid4().hex[:6]}"
            await ac.post("/auth/register", json={
                "username": name_b, "password": "pw",
            })
            r = await ac.post("/auth/login", json={
                "username": name_b, "password": "pw",
            })
            token_b = r.json()["token"]

        # User B tries to connect to User A's host device
        url = f"{WS_BASE}/ws/host/{device_a}?token={token_b}"
        with pytest.raises(Exception):
            async with websockets.connect(url) as ws:
                await ws.recv()
