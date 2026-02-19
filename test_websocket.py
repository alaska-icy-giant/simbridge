import json

from auth import create_token
from main import connections


# ---------------------------------------------------------------------------
# Connection
# ---------------------------------------------------------------------------

def test_host_connects(client, auth_header, host_device):
    token = auth_header["Authorization"].split(" ")[1]
    with client.websocket_connect(f"/ws/host/{host_device['id']}?token={token}") as ws:
        msg = ws.receive_json()
        assert msg["type"] == "connected"
        assert msg["device_id"] == host_device["id"]


def test_client_connects(client, auth_header, client_device):
    token = auth_header["Authorization"].split(" ")[1]
    with client.websocket_connect(f"/ws/client/{client_device['id']}?token={token}") as ws:
        msg = ws.receive_json()
        assert msg["type"] == "connected"
        assert msg["device_id"] == client_device["id"]


def test_ws_invalid_token(client, host_device):
    try:
        with client.websocket_connect(f"/ws/host/{host_device['id']}?token=bad.token.here") as ws:
            ws.receive_json()
            assert False, "Should not reach here"
    except Exception:
        pass  # Connection should fail


# ---------------------------------------------------------------------------
# Ping / pong
# ---------------------------------------------------------------------------

def test_ping_pong(client, auth_header, host_device):
    token = auth_header["Authorization"].split(" ")[1]
    with client.websocket_connect(f"/ws/host/{host_device['id']}?token={token}") as ws:
        ws.receive_json()  # connected msg
        ws.send_json({"type": "ping"})
        msg = ws.receive_json()
        assert msg["type"] == "pong"


# ---------------------------------------------------------------------------
# Message relay between paired devices
# ---------------------------------------------------------------------------

def test_relay_host_to_client(client, auth_header, paired_devices):
    token = auth_header["Authorization"].split(" ")[1]
    host_id = paired_devices["host"]["id"]
    client_id = paired_devices["client"]["id"]

    with client.websocket_connect(f"/ws/client/{client_id}?token={token}") as ws_client:
        ws_client.receive_json()  # connected
        with client.websocket_connect(f"/ws/host/{host_id}?token={token}") as ws_host:
            ws_host.receive_json()  # connected

            # Host sends a message to client
            ws_host.send_json({"type": "event", "data": "incoming_sms", "to_device_id": client_id})
            msg = ws_client.receive_json()
            assert msg["type"] == "event"
            assert msg["from_device_id"] == host_id


def test_relay_client_to_host(client, auth_header, paired_devices):
    token = auth_header["Authorization"].split(" ")[1]
    host_id = paired_devices["host"]["id"]
    client_id = paired_devices["client"]["id"]

    with client.websocket_connect(f"/ws/host/{host_id}?token={token}") as ws_host:
        ws_host.receive_json()  # connected
        with client.websocket_connect(f"/ws/client/{client_id}?token={token}") as ws_client:
            ws_client.receive_json()  # connected

            # Client sends command to host
            ws_client.send_json({"type": "command", "cmd": "SEND_SMS", "to_device_id": host_id})
            msg = ws_host.receive_json()
            assert msg["type"] == "command"
            assert msg["from_device_id"] == client_id


# ---------------------------------------------------------------------------
# Target offline
# ---------------------------------------------------------------------------

def test_target_offline_returns_error(client, auth_header, paired_devices):
    token = auth_header["Authorization"].split(" ")[1]
    client_id = paired_devices["client"]["id"]

    with client.websocket_connect(f"/ws/client/{client_id}?token={token}") as ws:
        ws.receive_json()  # connected

        # Send message to host that is NOT connected via WebSocket
        ws.send_json({"type": "command", "cmd": "GET_SIMS"})
        msg = ws.receive_json()
        assert msg["error"] == "target_offline"
