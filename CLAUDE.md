# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SimBridge is a message relay server that bridges communication between Host (SIM-capable) and Client phone applications via WebSocket and REST APIs. Built with Python 3 / FastAPI / SQLAlchemy / SQLite.

## Running the Server

```bash
pip install -r requirements.txt
cp .env.example .env  # set JWT_SECRET to a random string
uvicorn main:app --reload --port 8100
```

API docs available at `http://localhost:8100/docs`.

## Testing

```bash
pytest                          # all tests
pytest -k "test_function_name"  # single test
```

No tests exist yet. The README documents manual curl-based testing.

## Environment Variables

- `JWT_SECRET` — signing key for JWT tokens (required, set in `.env`)
- `DB_PATH` — SQLite database path (default: `simbridge.db`)

## Architecture

Three-file backend:

- **main.py** — FastAPI app with all REST endpoints, WebSocket handlers, and in-memory connection tracking (`connections: dict[int, WebSocket]`). Handles device registration, pairing workflow, SMS/call relay, and message logging.
- **models.py** — SQLAlchemy ORM models: User, Device (host/client types), PairingCode (6-digit, 10-min expiry), Pairing (host↔client link), MessageLog.
- **auth.py** — JWT token creation/verification (24h expiry) and bcrypt password hashing.

## Key Design Patterns

- JWT Bearer auth on REST endpoints; WebSocket auth via `?token=` query parameter.
- Devices are typed as `host` or `client`. Hosts relay SMS/calls; clients send commands.
- Pairing uses expiring 6-digit codes: host generates code via `POST /pair`, client confirms via `POST /pair/confirm`.
- WebSocket connections stored in an in-memory dict keyed by device ID. REST endpoints return 503 if the target host is offline.
- All relayed messages are logged to `message_logs` table for audit.
