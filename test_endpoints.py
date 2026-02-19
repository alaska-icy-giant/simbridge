from datetime import datetime, timedelta, timezone

from models import PairingCode


# ---------------------------------------------------------------------------
# Registration
# ---------------------------------------------------------------------------

def test_register_success(client):
    resp = client.post("/auth/register", json={"username": "alice", "password": "pw"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["username"] == "alice"
    assert "id" in data


def test_register_duplicate(client):
    client.post("/auth/register", json={"username": "alice", "password": "pw"})
    resp = client.post("/auth/register", json={"username": "alice", "password": "pw2"})
    assert resp.status_code == 400


# ---------------------------------------------------------------------------
# Login
# ---------------------------------------------------------------------------

def test_login_success(client):
    client.post("/auth/register", json={"username": "bob", "password": "pw"})
    resp = client.post("/auth/login", json={"username": "bob", "password": "pw"})
    assert resp.status_code == 200
    assert "token" in resp.json()


def test_login_wrong_password(client):
    client.post("/auth/register", json={"username": "bob", "password": "pw"})
    resp = client.post("/auth/login", json={"username": "bob", "password": "wrong"})
    assert resp.status_code == 401


def test_login_nonexistent_user(client):
    resp = client.post("/auth/login", json={"username": "nobody", "password": "pw"})
    assert resp.status_code == 401


# ---------------------------------------------------------------------------
# Devices
# ---------------------------------------------------------------------------

def test_create_host_device(client, auth_header):
    resp = client.post("/devices", json={"name": "Phone", "type": "host"}, headers=auth_header)
    assert resp.status_code == 200
    data = resp.json()
    assert data["type"] == "host"
    assert data["name"] == "Phone"


def test_create_client_device(client, auth_header):
    resp = client.post("/devices", json={"name": "Laptop", "type": "client"}, headers=auth_header)
    assert resp.status_code == 200
    assert resp.json()["type"] == "client"


def test_create_device_invalid_type(client, auth_header):
    resp = client.post("/devices", json={"name": "X", "type": "badtype"}, headers=auth_header)
    assert resp.status_code == 400


def test_list_devices(client, auth_header, host_device, client_device):
    resp = client.get("/devices", headers=auth_header)
    assert resp.status_code == 200
    ids = [d["id"] for d in resp.json()]
    assert host_device["id"] in ids
    assert client_device["id"] in ids


# ---------------------------------------------------------------------------
# Pairing
# ---------------------------------------------------------------------------

def test_generate_pairing_code(client, auth_header, host_device):
    resp = client.post(f"/pair?host_device_id={host_device['id']}", headers=auth_header)
    assert resp.status_code == 200
    data = resp.json()
    assert len(data["code"]) == 6
    assert data["expires_in_seconds"] == 600


def test_confirm_pairing(client, auth_header, host_device, client_device):
    code_resp = client.post(f"/pair?host_device_id={host_device['id']}", headers=auth_header)
    code = code_resp.json()["code"]

    resp = client.post(
        "/pair/confirm",
        json={"code": code, "client_device_id": client_device["id"]},
        headers=auth_header,
    )
    assert resp.status_code == 200
    assert resp.json()["status"] == "paired"


def test_confirm_pairing_expired_code(client, auth_header, host_device, client_device, db):
    # Create an already-expired code directly in the DB
    pc = PairingCode(
        user_id=1,
        host_device_id=host_device["id"],
        code="999999",
        expires_at=datetime.now(timezone.utc) - timedelta(minutes=1),
    )
    db.add(pc)
    db.commit()

    resp = client.post(
        "/pair/confirm",
        json={"code": "999999", "client_device_id": client_device["id"]},
        headers=auth_header,
    )
    assert resp.status_code == 400


def test_confirm_pairing_already_paired(client, auth_header, paired_devices):
    host_id = paired_devices["host"]["id"]
    client_id = paired_devices["client"]["id"]

    # Generate a new code and confirm again — should return already_paired
    code_resp = client.post(f"/pair?host_device_id={host_id}", headers=auth_header)
    code = code_resp.json()["code"]

    resp = client.post(
        "/pair/confirm",
        json={"code": code, "client_device_id": client_id},
        headers=auth_header,
    )
    assert resp.status_code == 200
    assert resp.json()["status"] == "already_paired"


# ---------------------------------------------------------------------------
# SMS / Call relay (host offline → 503)
# ---------------------------------------------------------------------------

def test_sms_no_client_device(client, auth_header, host_device):
    resp = client.post(
        "/sms",
        json={"to_device_id": host_device["id"], "sim": 1, "to": "+1234", "body": "hi"},
        headers=auth_header,
    )
    assert resp.status_code == 400


def test_sms_not_paired(client, auth_header, host_device, client_device):
    resp = client.post(
        "/sms",
        json={"to_device_id": host_device["id"], "sim": 1, "to": "+1234", "body": "hi"},
        headers=auth_header,
    )
    assert resp.status_code == 403


def test_sms_host_offline(client, auth_header, paired_devices):
    host_id = paired_devices["host"]["id"]
    resp = client.post(
        "/sms",
        json={"to_device_id": host_id, "sim": 1, "to": "+1234", "body": "hi"},
        headers=auth_header,
    )
    # R-15: Now queues instead of 503
    assert resp.status_code == 200
    assert resp.json()["status"] == "queued"


def test_call_host_offline(client, auth_header, paired_devices):
    host_id = paired_devices["host"]["id"]
    resp = client.post(
        "/call",
        json={"to_device_id": host_id, "sim": 1, "to": "+1234"},
        headers=auth_header,
    )
    # R-15: Now queues instead of 503
    assert resp.status_code == 200
    assert resp.json()["status"] == "queued"


# ---------------------------------------------------------------------------
# History
# ---------------------------------------------------------------------------

def test_history_empty_no_devices(client, auth_header):
    resp = client.get("/history", headers=auth_header)
    assert resp.status_code == 200
    data = resp.json()
    # R-17: Paginated response format
    assert data["items"] == []
    assert data["total"] == 0


def test_history_returns_logs(client, auth_header, paired_devices, db):
    from models import MessageLog
    import json as _json

    host_id = paired_devices["host"]["id"]
    client_id = paired_devices["client"]["id"]

    log = MessageLog(
        from_device_id=client_id,
        to_device_id=host_id,
        msg_type="command",
        payload=_json.dumps({"cmd": "SEND_SMS"}),
    )
    db.add(log)
    db.commit()

    resp = client.get("/history", headers=auth_header)
    assert resp.status_code == 200
    data = resp.json()
    # R-17: Paginated response format
    assert data["total"] == 1
    assert len(data["items"]) == 1
    assert data["items"][0]["msg_type"] == "command"


def test_history_filtered_by_device_id(client, auth_header, paired_devices, db):
    from models import MessageLog
    import json as _json

    host_id = paired_devices["host"]["id"]
    client_id = paired_devices["client"]["id"]

    db.add(MessageLog(from_device_id=client_id, to_device_id=host_id, msg_type="command", payload=_json.dumps({"a": 1})))
    db.add(MessageLog(from_device_id=host_id, to_device_id=client_id, msg_type="event", payload=_json.dumps({"b": 2})))
    db.commit()

    resp = client.get(f"/history?device_id={host_id}", headers=auth_header)
    assert resp.status_code == 200
    data = resp.json()
    # Both logs involve the host, so both should appear
    assert data["total"] == 2
    assert len(data["items"]) == 2
