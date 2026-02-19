import json
import os
import random
import string
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, HTTPException, Query, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import (
    create_token,
    decode_token,
    get_current_user_id,
    hash_password,
    verify_password,
)
from models import Device, MessageLog, Pairing, PairingCode, User, init_db

load_dotenv()

DB_PATH = os.getenv("DB_PATH", "simbridge.db")

SessionLocal = None

# In-memory WebSocket connections: device_id -> WebSocket
connections: dict[int, WebSocket] = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    global SessionLocal
    SessionLocal = init_db(DB_PATH)
    yield


app = FastAPI(title="SimBridge Relay", version="0.1.0", lifespan=lifespan)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# ---------------------------------------------------------------------------
# Pydantic schemas
# ---------------------------------------------------------------------------

class AuthRequest(BaseModel):
    username: str
    password: str


class DeviceCreate(BaseModel):
    name: str
    type: str  # "host" or "client"


class PairConfirm(BaseModel):
    code: str
    client_device_id: int


class SmsCommand(BaseModel):
    to_device_id: int  # host device to send through
    sim: int  # SIM slot (1 or 2)
    to: str  # phone number
    body: str


class CallCommand(BaseModel):
    to_device_id: int
    sim: int
    to: str


# ---------------------------------------------------------------------------
# Auth endpoints
# ---------------------------------------------------------------------------

@app.post("/auth/register")
def register(req: AuthRequest, db: Session = Depends(get_db)):
    if db.query(User).filter(User.username == req.username).first():
        raise HTTPException(400, "Username already taken")
    user = User(username=req.username, password_hash=hash_password(req.password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return {"id": user.id, "username": user.username}


@app.post("/auth/login")
def login(req: AuthRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == req.username).first()
    if not user or not verify_password(req.password, user.password_hash):
        raise HTTPException(401, "Invalid credentials")
    token = create_token(user.id)
    return {"token": token, "user_id": user.id}


# ---------------------------------------------------------------------------
# Device endpoints
# ---------------------------------------------------------------------------

@app.post("/devices")
def create_device(
    req: DeviceCreate,
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    if req.type not in ("host", "client"):
        raise HTTPException(400, "type must be 'host' or 'client'")
    device = Device(user_id=user_id, name=req.name, device_type=req.type)
    db.add(device)
    db.commit()
    db.refresh(device)
    return {
        "id": device.id,
        "name": device.name,
        "type": device.device_type,
        "is_online": device.is_online,
    }


@app.get("/devices")
def list_devices(
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    devices = db.query(Device).filter(Device.user_id == user_id).all()
    return [
        {
            "id": d.id,
            "name": d.name,
            "type": d.device_type,
            "is_online": d.id in connections,
            "last_seen": d.last_seen.isoformat() if d.last_seen else None,
        }
        for d in devices
    ]


# ---------------------------------------------------------------------------
# Pairing endpoints
# ---------------------------------------------------------------------------

def _generate_code() -> str:
    return "".join(random.choices(string.digits, k=6))


@app.post("/pair")
def create_pairing_code(
    host_device_id: int = Query(...),
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    device = db.query(Device).filter(
        Device.id == host_device_id, Device.user_id == user_id, Device.device_type == "host"
    ).first()
    if not device:
        raise HTTPException(404, "Host device not found")

    code = _generate_code()
    pc = PairingCode(
        user_id=user_id,
        host_device_id=host_device_id,
        code=code,
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=10),
    )
    db.add(pc)
    db.commit()
    return {"code": code, "expires_in_seconds": 600}


@app.post("/pair/confirm")
def confirm_pairing(
    req: PairConfirm,
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    # Validate client device belongs to user
    client_device = db.query(Device).filter(
        Device.id == req.client_device_id,
        Device.user_id == user_id,
        Device.device_type == "client",
    ).first()
    if not client_device:
        raise HTTPException(404, "Client device not found")

    # Find valid pairing code
    pc = db.query(PairingCode).filter(
        PairingCode.code == req.code,
        PairingCode.used == False,
        PairingCode.expires_at > datetime.now(timezone.utc),
    ).first()
    if not pc:
        raise HTTPException(400, "Invalid or expired pairing code")

    # Check if pairing already exists
    existing = db.query(Pairing).filter(
        Pairing.host_device_id == pc.host_device_id,
        Pairing.client_device_id == req.client_device_id,
    ).first()
    if existing:
        pc.used = True
        db.commit()
        return {"status": "already_paired", "pairing_id": existing.id}

    # Create pairing
    pairing = Pairing(
        host_device_id=pc.host_device_id,
        client_device_id=req.client_device_id,
    )
    pc.used = True
    db.add(pairing)
    db.commit()
    db.refresh(pairing)
    return {"status": "paired", "pairing_id": pairing.id, "host_device_id": pc.host_device_id}


# ---------------------------------------------------------------------------
# Command relay (REST fallback)
# ---------------------------------------------------------------------------

def _get_paired_host(client_device_id: int, host_device_id: int, user_id: int, db: Session) -> Device:
    """Verify the client is paired with the host and both belong to the user."""
    pairing = db.query(Pairing).filter(
        Pairing.host_device_id == host_device_id,
        Pairing.client_device_id == client_device_id,
    ).first()
    if not pairing:
        raise HTTPException(403, "Devices are not paired")
    host = db.query(Device).filter(Device.id == host_device_id).first()
    if not host:
        raise HTTPException(404, "Host device not found")
    return host


async def _relay_command(
    host_device_id: int, msg: dict, from_device_id: int, db: Session
) -> dict:
    """Send a command to the host via WebSocket and log it."""
    ws = connections.get(host_device_id)
    if not ws:
        raise HTTPException(503, "Host device is offline")

    req_id = msg.get("req_id") or str(uuid.uuid4())
    msg["req_id"] = req_id

    log = MessageLog(
        from_device_id=from_device_id,
        to_device_id=host_device_id,
        msg_type="command",
        payload=json.dumps(msg),
    )
    db.add(log)
    db.commit()

    await ws.send_json(msg)
    return {"status": "sent", "req_id": req_id}


@app.post("/sms")
async def send_sms(
    req: SmsCommand,
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    # Find a client device for this user (any)
    client = db.query(Device).filter(
        Device.user_id == user_id, Device.device_type == "client"
    ).first()
    if not client:
        raise HTTPException(400, "No client device registered")

    _get_paired_host(client.id, req.to_device_id, user_id, db)

    msg = {
        "type": "command",
        "cmd": "SEND_SMS",
        "sim": req.sim,
        "to": req.to,
        "body": req.body,
    }
    return await _relay_command(req.to_device_id, msg, client.id, db)


@app.post("/call")
async def make_call(
    req: CallCommand,
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    client = db.query(Device).filter(
        Device.user_id == user_id, Device.device_type == "client"
    ).first()
    if not client:
        raise HTTPException(400, "No client device registered")

    _get_paired_host(client.id, req.to_device_id, user_id, db)

    msg = {
        "type": "command",
        "cmd": "MAKE_CALL",
        "sim": req.sim,
        "to": req.to,
    }
    return await _relay_command(req.to_device_id, msg, client.id, db)


@app.get("/sims")
async def get_sims(
    host_device_id: int = Query(...),
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    client = db.query(Device).filter(
        Device.user_id == user_id, Device.device_type == "client"
    ).first()
    if not client:
        raise HTTPException(400, "No client device registered")

    _get_paired_host(client.id, host_device_id, user_id, db)

    msg = {"type": "command", "cmd": "GET_SIMS"}
    return await _relay_command(host_device_id, msg, client.id, db)


# ---------------------------------------------------------------------------
# Message history
# ---------------------------------------------------------------------------

@app.get("/history")
def get_history(
    device_id: int = Query(None),
    limit: int = Query(50, le=200),
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    # Get all device IDs for this user
    user_device_ids = [
        d.id for d in db.query(Device).filter(Device.user_id == user_id).all()
    ]
    if not user_device_ids:
        return []

    q = db.query(MessageLog).filter(
        (MessageLog.from_device_id.in_(user_device_ids))
        | (MessageLog.to_device_id.in_(user_device_ids))
    )
    if device_id and device_id in user_device_ids:
        q = q.filter(
            (MessageLog.from_device_id == device_id)
            | (MessageLog.to_device_id == device_id)
        )

    logs = q.order_by(MessageLog.created_at.desc()).limit(limit).all()
    return [
        {
            "id": log.id,
            "from_device_id": log.from_device_id,
            "to_device_id": log.to_device_id,
            "msg_type": log.msg_type,
            "payload": json.loads(log.payload),
            "created_at": log.created_at.isoformat() if log.created_at else None,
        }
        for log in logs
    ]


# ---------------------------------------------------------------------------
# WebSocket endpoints
# ---------------------------------------------------------------------------

def _ws_auth(token: str) -> int:
    """Authenticate a WebSocket connection via query param token. Returns user_id."""
    payload = decode_token(token)
    return payload["user_id"]


def _verify_device_ownership(device_id: int, user_id: int, expected_type: str, db: Session) -> Device:
    device = db.query(Device).filter(
        Device.id == device_id,
        Device.user_id == user_id,
        Device.device_type == expected_type,
    ).first()
    if not device:
        raise HTTPException(403, "Device not found or not yours")
    return device


async def _ws_loop(ws: WebSocket, device_id: int, device_type: str, db: Session):
    """Shared WebSocket read loop for host and client."""
    try:
        while True:
            raw = await ws.receive_text()
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                await ws.send_json({"error": "invalid JSON"})
                continue

            msg_type = msg.get("type")

            if msg_type == "ping":
                await ws.send_json({"type": "pong"})
                continue

            # Determine target device(s) based on pairings
            if device_type == "client":
                # Client sends commands to host
                target_id = msg.get("to_device_id")
                if not target_id:
                    # Find first paired host
                    pairing = db.query(Pairing).filter(
                        Pairing.client_device_id == device_id
                    ).first()
                    if not pairing:
                        await ws.send_json({"error": "no paired host"})
                        continue
                    target_id = pairing.host_device_id

            elif device_type == "host":
                # Host sends events to paired client(s)
                target_id = msg.get("to_device_id")
                if not target_id:
                    # Find first paired client
                    pairing = db.query(Pairing).filter(
                        Pairing.host_device_id == device_id
                    ).first()
                    if not pairing:
                        await ws.send_json({"error": "no paired client"})
                        continue
                    target_id = pairing.client_device_id
            else:
                await ws.send_json({"error": "unknown device type"})
                continue

            # Log message
            log = MessageLog(
                from_device_id=device_id,
                to_device_id=target_id,
                msg_type=msg_type or "unknown",
                payload=raw,
            )
            db.add(log)
            db.commit()

            # Forward to target
            target_ws = connections.get(target_id)
            if target_ws:
                msg["from_device_id"] = device_id
                await target_ws.send_json(msg)
            else:
                await ws.send_json({
                    "error": "target_offline",
                    "target_device_id": target_id,
                    "req_id": msg.get("req_id"),
                })

    except WebSocketDisconnect:
        pass


@app.websocket("/ws/host/{device_id}")
async def ws_host(ws: WebSocket, device_id: int, token: str = Query(...)):
    user_id = _ws_auth(token)
    db = SessionLocal()
    try:
        device = _verify_device_ownership(device_id, user_id, "host", db)
        await ws.accept()
        connections[device_id] = ws
        device.is_online = True
        device.last_seen = datetime.now(timezone.utc)
        db.commit()

        await ws.send_json({"type": "connected", "device_id": device_id})
        await _ws_loop(ws, device_id, "host", db)
    finally:
        connections.pop(device_id, None)
        device = db.query(Device).filter(Device.id == device_id).first()
        if device:
            device.is_online = False
            device.last_seen = datetime.now(timezone.utc)
            db.commit()
        db.close()


@app.websocket("/ws/client/{device_id}")
async def ws_client(ws: WebSocket, device_id: int, token: str = Query(...)):
    user_id = _ws_auth(token)
    db = SessionLocal()
    try:
        device = _verify_device_ownership(device_id, user_id, "client", db)
        await ws.accept()
        connections[device_id] = ws
        device.is_online = True
        device.last_seen = datetime.now(timezone.utc)
        db.commit()

        await ws.send_json({"type": "connected", "device_id": device_id})
        await _ws_loop(ws, device_id, "client", db)
    finally:
        connections.pop(device_id, None)
        device = db.query(Device).filter(Device.id == device_id).first()
        if device:
            device.is_online = False
            device.last_seen = datetime.now(timezone.utc)
            db.commit()
        db.close()
