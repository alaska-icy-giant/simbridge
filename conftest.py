import os

# Set JWT_SECRET before importing auth/main (required since R-01 fix)
os.environ.setdefault("JWT_SECRET", "test-secret-for-pytest")

import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from auth import create_token
from main import app, connections, get_db, _auth_attempts
from models import Base, Device, Pairing, PairingCode

from datetime import datetime, timedelta, timezone
from fastapi.testclient import TestClient


@pytest.fixture()
def db():
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    TestingSession = sessionmaker(bind=engine)
    session = TestingSession()
    try:
        yield session
    finally:
        session.close()
        engine.dispose()


@pytest.fixture()
def client(db):
    def _override_get_db():
        yield db

    app.dependency_overrides[get_db] = _override_get_db
    import main

    with TestClient(app) as c:
        # Patch SessionLocal AFTER TestClient enters context, because the lifespan
        # event overwrites main.SessionLocal with a file-backed engine.
        original = main.SessionLocal
        main.SessionLocal = sessionmaker(bind=db.get_bind())
        yield c
        main.SessionLocal = original

    app.dependency_overrides.clear()
    connections.clear()
    _auth_attempts.clear()


@pytest.fixture()
def auth_header(client):
    client.post("/auth/register", json={"username": "testuser", "password": "testpass"})
    resp = client.post("/auth/login", json={"username": "testuser", "password": "testpass"})
    token = resp.json()["token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def mock_google_verify(monkeypatch):
    """Fixture that patches verify_google_token to return a controlled payload."""
    _payload = {
        "sub": "google-uid-123",
        "email": "googleuser@gmail.com",
    }

    async def _fake_verify(id_token: str) -> dict:
        if id_token == "invalid":
            from fastapi import HTTPException
            raise HTTPException(status_code=401, detail="Invalid Google token: bad")
        return _payload

    import auth
    monkeypatch.setattr(auth, "verify_google_token", _fake_verify)
    # Also patch the reference in main module
    import main
    monkeypatch.setattr(main, "verify_google_token", _fake_verify)
    return _payload


@pytest.fixture()
def google_auth_header(client, mock_google_verify):
    """Authenticate via Google and return the Authorization header."""
    resp = client.post("/auth/google", json={"id_token": "valid-google-token"})
    assert resp.status_code == 200
    token = resp.json()["token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def host_device(client, auth_header, db):
    resp = client.post("/devices", json={"name": "MyHost", "type": "host"}, headers=auth_header)
    return resp.json()


@pytest.fixture()
def client_device(client, auth_header, db):
    resp = client.post("/devices", json={"name": "MyClient", "type": "client"}, headers=auth_header)
    return resp.json()


@pytest.fixture()
def paired_devices(client, auth_header, host_device, client_device, db):
    """Create a host and client device that are already paired."""
    # Create pairing code
    resp = client.post(f"/pair?host_device_id={host_device['id']}", headers=auth_header)
    code = resp.json()["code"]

    # Confirm pairing
    resp = client.post(
        "/pair/confirm",
        json={"code": code, "client_device_id": client_device["id"]},
        headers=auth_header,
    )
    pairing = resp.json()
    return {
        "host": host_device,
        "client": client_device,
        "pairing_id": pairing["pairing_id"],
    }
