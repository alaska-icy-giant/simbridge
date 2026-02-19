# SimBridge Relay Server

Message relay between Host (Phone A with SIM cards) and Client (Phone B) apps. The relay server is a WebSocket-based message broker that routes commands from Client to Host and events from Host to Client.

## System Architecture

```
┌──────────────┐        ┌──────────────────┐        ┌──────────────┐
│  Host App    │  WSS   │  Relay Server    │  WSS   │  Client App  │
│  (Phone A)   │◄──────►│  (this project)  │◄──────►│  (Phone B)   │
│              │        │                  │        │              │
│  SIM 1, SIM 2│        │  FastAPI + SQLite │        │  Dialer UI   │
│  Android     │        │  JWT auth        │        │  SMS compose  │
│  Telecom API │        │  WebSocket hub   │        │  SIM selector │
└──────────────┘        └──────────────────┘        └──────────────┘
```

**Design philosophy**: Simple and stupid. SQLite database, in-memory WebSocket tracking, no Redis, no background workers, no message queuing. If the Host is offline, commands fail immediately.

## System Requirements

### Runtime
- Python 3.11+
- ~50MB RAM (idle), scales with concurrent WebSocket connections
- Minimal CPU — primarily I/O bound (WebSocket relay)
- SQLite (included with Python, no external DB needed)

### Dependencies
- **FastAPI** — async web framework with native WebSocket support
- **uvicorn** — ASGI server
- **SQLAlchemy** — ORM for SQLite
- **PyJWT** — JWT token creation and verification (HS256)
- **bcrypt** — password hashing
- **python-dotenv** — environment variable loading

### Network
- One TCP port (default 8100) for both REST API and WebSocket connections
- TLS termination recommended via reverse proxy (nginx, caddy) in production

## Project Structure

```
/opt/ws/simbridge/
├── main.py              # FastAPI app — all REST + WebSocket endpoints
├── models.py            # SQLAlchemy models (User, Device, PairingCode, Pairing, MessageLog)
├── auth.py              # JWT create/verify, password hashing, FastAPI auth dependency
├── requirements.txt     # Python dependencies
├── .env.example         # Example environment variables
└── README.md            # This file
```

## Development Guide

### Initial Setup

```bash
cd /opt/ws/simbridge
pip install -r requirements.txt
cp .env.example .env
# Edit .env — set JWT_SECRET to a random string (32+ chars recommended)
```

### Run Development Server

```bash
cd /opt/ws/simbridge
uvicorn main:app --reload --port 8100
```

- API docs (Swagger UI): http://localhost:8100/docs
- ReDoc: http://localhost:8100/redoc

The `--reload` flag enables hot reload on file changes.

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `change-me` | HMAC key for signing JWT tokens. Use 32+ random characters in production. |
| `DB_PATH` | `simbridge.db` | Path to SQLite database file. Created automatically on first run. |

### Quick Smoke Test

```bash
# 1. Register a user
curl -s -X POST http://localhost:8100/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'
# -> {"id":1,"username":"test"}

# 2. Login to get JWT token
curl -s -X POST http://localhost:8100/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'
# -> {"token":"eyJ...","user_id":1}

export TOKEN=eyJ...   # paste token from login response

# 3. Register a host device (Phone A with SIMs)
curl -s -X POST http://localhost:8100/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Phone A","type":"host"}'
# -> {"id":1,"name":"My Phone A","type":"host","is_online":false}

# 4. Register a client device (Phone B)
curl -s -X POST http://localhost:8100/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Phone B","type":"client"}'
# -> {"id":2,"name":"My Phone B","type":"client","is_online":false}

# 5. Generate pairing code on host
curl -s -X POST "http://localhost:8100/pair?host_device_id=1" \
  -H "Authorization: Bearer $TOKEN"
# -> {"code":"448826","expires_in_seconds":600}

# 6. Confirm pairing from client
curl -s -X POST http://localhost:8100/pair/confirm \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"448826","client_device_id":2}'
# -> {"status":"paired","pairing_id":1,"host_device_id":1}

# 7. Try sending SMS (will fail — host not connected via WebSocket)
curl -s -X POST http://localhost:8100/sms \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"to_device_id":1,"sim":1,"to":"+886912345678","body":"Hello"}'
# -> {"detail":"Host device is offline"}
```

### Testing WebSocket Connections

Use `websocat` or browser console to test WebSocket endpoints:

```bash
# Install websocat
# brew install websocat  (macOS)
# cargo install websocat (from source)

# Connect as host device
websocat "ws://localhost:8100/ws/host/1?token=$TOKEN"

# In another terminal, connect as client device
websocat "ws://localhost:8100/ws/client/2?token=$TOKEN"

# From client, send a command (auto-routed to paired host):
{"type":"command","cmd":"GET_SIMS","req_id":"test-1"}

# From host, send an event (auto-routed to paired client):
{"type":"event","event":"SIM_INFO","sims":[{"slot":1,"carrier":"CHT"}],"req_id":"test-1"}
```

## API Reference

### Authentication

| Endpoint | Method | Auth | Body | Response |
|---|---|---|---|---|
| `/auth/register` | POST | No | `{"username","password"}` | `{"id","username"}` |
| `/auth/login` | POST | No | `{"username","password"}` | `{"token","user_id"}` |

### Devices

| Endpoint | Method | Auth | Body/Params | Response |
|---|---|---|---|---|
| `POST /devices` | POST | JWT | `{"name","type":"host\|client"}` | `{"id","name","type","is_online"}` |
| `GET /devices` | GET | JWT | — | `[{"id","name","type","is_online","last_seen"}]` |

### Pairing

| Endpoint | Method | Auth | Body/Params | Response |
|---|---|---|---|---|
| `POST /pair` | POST | JWT | `?host_device_id=N` | `{"code","expires_in_seconds"}` |
| `POST /pair/confirm` | POST | JWT | `{"code","client_device_id"}` | `{"status","pairing_id","host_device_id"}` |

Pairing codes are 6-digit numbers, valid for 10 minutes.

### Commands (REST fallback)

These relay commands to the Host device via its WebSocket connection. Returns `503` if the Host is offline.

| Endpoint | Method | Auth | Body | Response |
|---|---|---|---|---|
| `POST /sms` | POST | JWT | `{"to_device_id","sim","to","body"}` | `{"status":"sent","req_id"}` |
| `POST /call` | POST | JWT | `{"to_device_id","sim","to"}` | `{"status":"sent","req_id"}` |
| `GET /sims` | GET | JWT | `?host_device_id=N` | `{"status":"sent","req_id"}` |

### History

| Endpoint | Method | Auth | Params | Response |
|---|---|---|---|---|
| `GET /history` | GET | JWT | `?device_id=N&limit=50` | `[{"id","from_device_id","to_device_id","msg_type","payload","created_at"}]` |

### WebSocket

| Endpoint | Auth | Purpose |
|---|---|---|
| `WS /ws/host/{device_id}?token=JWT` | JWT in query | Host persistent connection |
| `WS /ws/client/{device_id}?token=JWT` | JWT in query | Client persistent connection |

On connect, server sends: `{"type":"connected","device_id":N}`

**Ping/pong**: Send `{"type":"ping"}`, receive `{"type":"pong"}`.

**Message routing**: Messages are automatically forwarded to the paired device. If no `to_device_id` is specified in the message, the server looks up the first paired device.

### WebSocket Message Format

**Commands** (Client → Host):
```json
{"type":"command","cmd":"SEND_SMS","sim":1,"to":"+886...","body":"Hi","req_id":"uuid"}
{"type":"command","cmd":"MAKE_CALL","sim":1,"to":"+886...","req_id":"uuid"}
{"type":"command","cmd":"GET_SIMS","req_id":"uuid"}
```

**Events** (Host → Client):
```json
{"type":"event","event":"SMS_SENT","status":"ok","req_id":"uuid"}
{"type":"event","event":"INCOMING_SMS","sim":1,"from":"+1...","body":"Hello"}
{"type":"event","event":"INCOMING_CALL","sim":2,"from":"+1..."}
{"type":"event","event":"SIM_INFO","sims":[{"slot":1,"carrier":"CHT","number":"+886..."}]}
{"type":"event","event":"CALL_STATE","state":"dialing|ringing|active|ended","req_id":"uuid"}
```

**Error** (Server → Device):
```json
{"error":"target_offline","target_device_id":1,"req_id":"uuid"}
{"error":"no paired host"}
{"error":"invalid JSON"}
```

## Deployment Guide

### Option 1: Direct (systemd)

```bash
# On your VPS
cd /opt/ws/simbridge
pip install -r requirements.txt

# Create .env
cat > .env << 'EOF'
JWT_SECRET=your-very-long-random-secret-string-here
DB_PATH=/opt/ws/simbridge/simbridge.db
EOF

# Test run
uvicorn main:app --host 0.0.0.0 --port 8100

# Create systemd service
sudo tee /etc/systemd/system/simbridge.service << 'EOF'
[Unit]
Description=SimBridge Relay Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/ws/simbridge
EnvironmentFile=/opt/ws/simbridge/.env
ExecStart=/usr/local/bin/uvicorn main:app --host 0.0.0.0 --port 8100
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now simbridge
sudo systemctl status simbridge
```

### Option 2: Docker

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8100
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]
```

```bash
docker build -t simbridge .
docker run -d --name simbridge \
  -p 8100:8100 \
  -e JWT_SECRET=your-secret-here \
  -v simbridge_data:/app/data \
  -e DB_PATH=/app/data/simbridge.db \
  simbridge
```

### Reverse Proxy (nginx)

For TLS termination and production use:

```nginx
server {
    listen 443 ssl;
    server_name simbridge.example.com;

    ssl_certificate /etc/letsencrypt/live/simbridge.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/simbridge.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8100;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400;  # keep WebSocket alive for 24h
    }
}
```

### Production Checklist

- [ ] Set `JWT_SECRET` to a strong random string (32+ characters)
- [ ] Use TLS (HTTPS/WSS) — either via reverse proxy or cloud load balancer
- [ ] Set `DB_PATH` to a persistent volume location
- [ ] Back up the SQLite database periodically
- [ ] Monitor disk space (SQLite + message logs grow over time)
- [ ] Consider adding `--workers 1` to uvicorn (SQLite doesn't support concurrent writes well)

## Database Schema

```
users           devices              pairings              message_logs
─────           ───────              ────────              ────────────
id              id                   id                    id
username        user_id → users      host_device_id →      from_device_id →
password_hash   name                 client_device_id →    to_device_id →
created_at      device_type          created_at            msg_type
                device_token                               payload (JSON)
                is_online                                  created_at
                last_seen
                created_at

pairing_codes
─────────────
id
user_id → users
host_device_id → devices
code (6-digit)
expires_at
used
created_at
```

## What's NOT Included (by design)

| Feature | Status | Notes |
|---|---|---|
| Redis / message queue | Skipped | In-memory dict; messages dropped if target offline |
| FCM push notifications | Skipped | Add later to wake Host when offline |
| WebRTC signaling | Skipped | Add later for voice call audio bridging |
| TURN server | Skipped | Add later with coturn for NAT traversal |
| E2E encryption | Skipped | TLS only for now |
| Rate limiting | Skipped | Add when abuse becomes a concern |
| Tests | Skipped | Get it working first, add tests later |
| Message buffering | Skipped | Commands fail immediately if Host offline |
