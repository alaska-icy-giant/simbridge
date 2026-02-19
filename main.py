import asyncio
import json
import logging
import os
import secrets
import string
import uuid
from collections import defaultdict
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, HTTPException, Query, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from auth import (
    create_token,
    decode_token,
    get_current_user_id,
    hash_password,
    verify_google_token,
    verify_password,
)
from models import Device, MessageLog, Pairing, PairingCode, PendingCommand, User, init_db

load_dotenv()

logger = logging.getLogger("simbridge")

DB_PATH = os.getenv("DB_PATH", "simbridge.db")

SessionLocal = None

# In-memory WebSocket connections: device_id -> WebSocket
# Protected by _conn_lock for all reads and writes.
connections: dict[int, WebSocket] = {}
_conn_lock = asyncio.Lock()

# Rate limiting: track recent auth attempts per IP-like key (username).
# Maps username -> list of timestamps. Cleaned lazily.
_auth_attempts: dict[str, list[float]] = defaultdict(list)
AUTH_RATE_LIMIT = 5  # max attempts
AUTH_RATE_WINDOW = 60  # per N seconds


MESSAGE_LOG_RETENTION_DAYS = int(os.getenv("LOG_RETENTION_DAYS", "90"))


@asynccontextmanager
async def lifespan(app: FastAPI):
    global SessionLocal
    SessionLocal = init_db(DB_PATH)

    # Clean up old message logs on startup
    db = SessionLocal()
    try:
        cutoff = datetime.now(timezone.utc) - timedelta(days=MESSAGE_LOG_RETENTION_DAYS)
        deleted = db.query(MessageLog).filter(MessageLog.created_at < cutoff).delete()
        db.commit()
        if deleted:
            logger.info("Cleaned up %d old message log entries", deleted)
    except Exception as e:
        logger.error("Log cleanup failed: %s", e)
        db.rollback()
    finally:
        db.close()

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


class GoogleAuthRequest(BaseModel):
    id_token: str


class DeviceCreate(BaseModel):
    name: str
    type: str  # "host" or "client"


class PairConfirm(BaseModel):
    code: str
    client_device_id: int


class SmsCommand(BaseModel):
    to_device_id: int = Field(..., gt=0)
    sim: int = Field(..., ge=1, le=2)
    to: str = Field(..., min_length=1, max_length=30)
    body: str = Field(..., min_length=1, max_length=1600)


class CallCommand(BaseModel):
    to_device_id: int = Field(..., gt=0)
    sim: int = Field(..., ge=1, le=2)
    to: str = Field(..., min_length=1, max_length=30)


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


def _check_rate_limit(key: str):
    """Enforce per-key rate limiting. Raises 429 if exceeded."""
    import time
    now = time.time()
    attempts = _auth_attempts[key]
    # Prune old entries
    _auth_attempts[key] = [t for t in attempts if now - t < AUTH_RATE_WINDOW]
    if len(_auth_attempts[key]) >= AUTH_RATE_LIMIT:
        raise HTTPException(429, "Too many attempts. Try again later.")
    _auth_attempts[key].append(now)


@app.post("/auth/login")
def login(req: AuthRequest, db: Session = Depends(get_db)):
    _check_rate_limit(req.username)
    user = db.query(User).filter(User.username == req.username).first()
    if not user or not user.password_hash or not verify_password(req.password, user.password_hash):
        raise HTTPException(401, "Invalid credentials")
    token = create_token(user.id)
    return {"token": token, "user_id": user.id}


@app.post("/auth/google")
async def google_login(req: GoogleAuthRequest, db: Session = Depends(get_db)):
    payload = await verify_google_token(req.id_token)

    google_id = payload["sub"]
    email = payload.get("email")

    # 1. Find by google_id
    user = db.query(User).filter(User.google_id == google_id).first()

    if not user and email:
        # 2. Link by email
        user = db.query(User).filter(User.email == email).first()
        if user:
            user.google_id = google_id
            db.commit()

    if not user:
        # 3. Auto-create
        # Generate a unique username from email or google_id
        base_username = email.split("@")[0] if email else f"google_{google_id[:8]}"
        username = base_username
        counter = 1
        while db.query(User).filter(User.username == username).first():
            username = f"{base_username}{counter}"
            counter += 1

        user = User(username=username, email=email, google_id=google_id)
        db.add(user)
        db.commit()
        db.refresh(user)

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
    """Generate a cryptographically secure 6-digit pairing code."""
    return "".join(secrets.choice(string.digits) for _ in range(6))


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

    # Expire any previous unused codes for this host
    db.query(PairingCode).filter(
        PairingCode.host_device_id == host_device_id,
        PairingCode.used == False,
    ).update({"used": True})

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

    # Rate limit pairing attempts to prevent brute-force
    _check_rate_limit(f"pair:{req.client_device_id}")

    # Find valid pairing code
    pc = db.query(PairingCode).filter(
        PairingCode.code == req.code,
        PairingCode.used == False,
        PairingCode.expires_at > datetime.now(timezone.utc),
    ).first()
    if not pc:
        raise HTTPException(400, "Invalid or expired pairing code")

    # Verify the pairing code belongs to the same user (R-03: prevent cross-user pairing)
    if pc.user_id != user_id:
        raise HTTPException(403, "Pairing code does not belong to your account")

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
    host = db.query(Device).filter(
        Device.id == host_device_id,
        Device.user_id == user_id,
    ).first()
    if not host:
        raise HTTPException(403, "Host device not found or not yours")
    return host


async def _relay_command(
    host_device_id: int, msg: dict, from_device_id: int, db: Session
) -> dict:
    """Send a command to the host via WebSocket. Queues if host is offline (R-15)."""
    req_id = msg.get("req_id") or str(uuid.uuid4())
    msg["req_id"] = req_id

    async with _conn_lock:
        ws = connections.get(host_device_id)

    # Log message
    try:
        log = MessageLog(
            from_device_id=from_device_id,
            to_device_id=host_device_id,
            msg_type="command",
            payload=json.dumps(msg),
        )
        db.add(log)
        db.commit()
    except Exception as e:
        logger.error("Failed to log command: %s", e)
        db.rollback()

    if not ws:
        # R-15: Queue for later delivery instead of failing with 503
        try:
            pending = PendingCommand(
                host_device_id=host_device_id,
                from_device_id=from_device_id,
                payload=json.dumps(msg),
            )
            db.add(pending)
            db.commit()
        except Exception as e:
            logger.error("Failed to queue command: %s", e)
            db.rollback()
        return {"status": "queued", "req_id": req_id}

    try:
        await ws.send_json(msg)
    except Exception as e:
        logger.error("Failed to send command to host %d: %s", host_device_id, e)
        raise HTTPException(502, "Failed to deliver command to host")

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
    offset: int = Query(0, ge=0),
    user_id: int = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    # Get all device IDs for this user
    user_device_ids = [
        d.id for d in db.query(Device).filter(Device.user_id == user_id).all()
    ]
    if not user_device_ids:
        return {"items": [], "total": 0, "offset": offset, "limit": limit}

    q = db.query(MessageLog).filter(
        (MessageLog.from_device_id.in_(user_device_ids))
        | (MessageLog.to_device_id.in_(user_device_ids))
    )
    if device_id and device_id in user_device_ids:
        q = q.filter(
            (MessageLog.from_device_id == device_id)
            | (MessageLog.to_device_id == device_id)
        )

    total = q.count()
    logs = q.order_by(MessageLog.created_at.desc()).offset(offset).limit(limit).all()
    items = [
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
    return {"items": items, "total": total, "offset": offset, "limit": limit}


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


ALLOWED_WS_TYPES = {"ping", "command", "event", "webrtc"}


async def _ws_loop(ws: WebSocket, device_id: int, device_type: str):
    """Shared WebSocket read loop for host and client.

    Uses a fresh DB session per message to avoid stale-session issues (R-16).
    """
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

            # R-13: Validate message type
            if msg_type not in ALLOWED_WS_TYPES:
                await ws.send_json({"error": f"invalid message type: {msg_type}"})
                continue

            # Use a fresh session per message (R-16)
            db = SessionLocal()
            try:
                # Determine target device(s) based on pairings
                if device_type == "client":
                    target_id = msg.get("to_device_id")
                    if not target_id:
                        pairing = db.query(Pairing).filter(
                            Pairing.client_device_id == device_id
                        ).first()
                        if not pairing:
                            await ws.send_json({"error": "no paired host"})
                            continue
                        target_id = pairing.host_device_id

                elif device_type == "host":
                    target_id = msg.get("to_device_id")
                    if not target_id:
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
                try:
                    log = MessageLog(
                        from_device_id=device_id,
                        to_device_id=target_id,
                        msg_type=msg_type or "unknown",
                        payload=raw,
                    )
                    db.add(log)
                    db.commit()
                except Exception as e:
                    logger.error("Failed to log message: %s", e)
                    db.rollback()
            finally:
                db.close()

            # Forward to target (use lock to safely read connections)
            async with _conn_lock:
                target_ws = connections.get(target_id)
            if target_ws:
                msg["from_device_id"] = device_id
                try:
                    await target_ws.send_json(msg)
                except Exception as e:
                    logger.error("Failed to forward message to %d: %s", target_id, e)
            else:
                await ws.send_json({
                    "error": "target_offline",
                    "target_device_id": target_id,
                    "req_id": msg.get("req_id"),
                })

    except WebSocketDisconnect:
        pass


HEARTBEAT_INTERVAL = 30  # seconds
HEARTBEAT_TIMEOUT = 60  # seconds — close connection if no pong received


async def _server_heartbeat(ws: WebSocket, device_id: int):
    """R-19: Server-initiated pings to detect dead connections."""
    try:
        while True:
            await asyncio.sleep(HEARTBEAT_INTERVAL)
            try:
                await ws.send_json({"type": "ping"})
            except Exception:
                break
    except asyncio.CancelledError:
        pass


async def _notify_paired_offline(device_id: int, device_type: str):
    """R-18: Notify paired device(s) when a device goes offline."""
    db = SessionLocal()
    try:
        if device_type == "host":
            pairings = db.query(Pairing).filter(Pairing.host_device_id == device_id).all()
            target_ids = [p.client_device_id for p in pairings]
        else:
            pairings = db.query(Pairing).filter(Pairing.client_device_id == device_id).all()
            target_ids = [p.host_device_id for p in pairings]
    finally:
        db.close()

    for tid in target_ids:
        async with _conn_lock:
            target_ws = connections.get(tid)
        if target_ws:
            try:
                await target_ws.send_json({
                    "type": "event",
                    "event": "DEVICE_OFFLINE",
                    "device_id": device_id,
                })
            except Exception:
                pass


async def _ws_connect_and_loop(ws: WebSocket, device_id: int, user_id: int, device_type: str):
    """Shared connect/loop/cleanup logic for both host and client WebSocket endpoints."""
    db = SessionLocal()
    try:
        _verify_device_ownership(device_id, user_id, device_type, db)
    finally:
        db.close()

    await ws.accept()

    # Register connection (R-02: with lock, close old duplicate)
    async with _conn_lock:
        old_ws = connections.get(device_id)
        if old_ws:
            try:
                await old_ws.close(1008, "Replaced by new connection")
            except Exception:
                pass
        connections[device_id] = ws

    # R-10: Update last_seen but do NOT persist is_online (computed from connections dict)
    db = SessionLocal()
    try:
        device = db.query(Device).filter(Device.id == device_id).first()
        if device:
            device.last_seen = datetime.now(timezone.utc)
            db.commit()
    except Exception:
        pass
    finally:
        db.close()

    # R-15: Deliver queued commands when host reconnects
    if device_type == "host":
        db = SessionLocal()
        try:
            pending = db.query(PendingCommand).filter(
                PendingCommand.host_device_id == device_id,
                PendingCommand.delivered == False,
            ).order_by(PendingCommand.created_at).all()
            for cmd in pending:
                try:
                    await ws.send_json(json.loads(cmd.payload))
                    cmd.delivered = True
                except Exception:
                    break
            if pending:
                db.commit()
                logger.info("Delivered %d queued commands to host %d", len(pending), device_id)
        except Exception as e:
            logger.error("Failed to deliver queued commands: %s", e)
        finally:
            db.close()

    # Start server-side heartbeat (R-19)
    heartbeat_task = asyncio.create_task(_server_heartbeat(ws, device_id))

    try:
        await ws.send_json({"type": "connected", "device_id": device_id})
        await _ws_loop(ws, device_id, device_type)
    finally:
        heartbeat_task.cancel()
        # Remove from connections (only if we're still the registered one)
        async with _conn_lock:
            if connections.get(device_id) is ws:
                connections.pop(device_id, None)
        # Update last_seen on disconnect
        db = SessionLocal()
        try:
            device = db.query(Device).filter(Device.id == device_id).first()
            if device:
                device.last_seen = datetime.now(timezone.utc)
                db.commit()
        except Exception:
            pass
        finally:
            db.close()
        # R-18: Notify paired devices
        await _notify_paired_offline(device_id, device_type)


@app.websocket("/ws/host/{device_id}")
async def ws_host(ws: WebSocket, device_id: int, token: str = Query(...)):
    user_id = _ws_auth(token)
    await _ws_connect_and_loop(ws, device_id, user_id, "host")


@app.websocket("/ws/client/{device_id}")
async def ws_client(ws: WebSocket, device_id: int, token: str = Query(...)):
    user_id = _ws_auth(token)
    await _ws_connect_and_loop(ws, device_id, user_id, "client")
