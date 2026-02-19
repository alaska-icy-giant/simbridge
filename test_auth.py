import time

import jwt
import pytest
from fastapi import HTTPException

from auth import (
    JWT_ALGORITHM,
    JWT_SECRET,
    create_token,
    decode_token,
    hash_password,
    verify_password,
)


def test_hash_and_verify_password():
    hashed = hash_password("secret123")
    assert hashed != "secret123"
    assert verify_password("secret123", hashed)


def test_verify_wrong_password():
    hashed = hash_password("secret123")
    assert not verify_password("wrong", hashed)


def test_create_and_decode_token():
    token = create_token(42)
    payload = decode_token(token)
    assert payload["user_id"] == 42
    assert "exp" in payload
    assert "iat" in payload


def test_decode_expired_token():
    payload = {
        "user_id": 1,
        "exp": time.time() - 10,
        "iat": time.time() - 100,
    }
    token = jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    with pytest.raises(HTTPException) as exc_info:
        decode_token(token)
    assert exc_info.value.status_code == 401
    assert "expired" in exc_info.value.detail.lower()


def test_decode_invalid_token():
    with pytest.raises(HTTPException) as exc_info:
        decode_token("not.a.valid.token")
    assert exc_info.value.status_code == 401


# ---------------------------------------------------------------------------
# Google token verification
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_verify_google_token_disabled_when_no_client_id(monkeypatch):
    """verify_google_token raises 501 when GOOGLE_CLIENT_ID is empty."""
    import auth
    monkeypatch.setattr(auth, "GOOGLE_CLIENT_ID", "")
    with pytest.raises(HTTPException) as exc_info:
        await auth.verify_google_token("some-token")
    assert exc_info.value.status_code == 501
    assert "not configured" in exc_info.value.detail


@pytest.mark.asyncio
async def test_verify_google_token_invalid(monkeypatch):
    """verify_google_token raises 401 for a garbage token."""
    import auth
    monkeypatch.setattr(auth, "GOOGLE_CLIENT_ID", "test-client-id")

    # Mock google.oauth2.id_token.verify_oauth2_token to raise ValueError
    import unittest.mock as mock
    fake_module = mock.MagicMock()
    fake_module.verify_oauth2_token.side_effect = ValueError("Token is not valid")
    monkeypatch.setattr("google.oauth2.id_token.verify_oauth2_token", fake_module.verify_oauth2_token)

    with pytest.raises(HTTPException) as exc_info:
        await auth.verify_google_token("bad-token")
    assert exc_info.value.status_code == 401
    assert "Invalid Google token" in exc_info.value.detail


@pytest.mark.asyncio
async def test_verify_google_token_valid(monkeypatch):
    """verify_google_token returns payload for a valid token."""
    import auth
    monkeypatch.setattr(auth, "GOOGLE_CLIENT_ID", "test-client-id")

    expected_payload = {"sub": "12345", "email": "user@example.com"}

    import unittest.mock as mock
    fake_module = mock.MagicMock()
    fake_module.verify_oauth2_token.return_value = expected_payload
    monkeypatch.setattr("google.oauth2.id_token.verify_oauth2_token", fake_module.verify_oauth2_token)

    result = await auth.verify_google_token("good-token")
    assert result == expected_payload
