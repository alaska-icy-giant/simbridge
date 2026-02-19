import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from auth import create_token
from main import app, connections, get_db
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
    # Patch SessionLocal so WebSocket handlers (which call SessionLocal() directly) use the test DB
    import main
    original = main.SessionLocal
    main.SessionLocal = sessionmaker(bind=db.get_bind())

    with TestClient(app) as c:
        yield c

    app.dependency_overrides.clear()
    main.SessionLocal = original
    connections.clear()


@pytest.fixture()
def auth_header(client):
    client.post("/auth/register", json={"username": "testuser", "password": "testpass"})
    resp = client.post("/auth/login", json={"username": "testuser", "password": "testpass"})
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
