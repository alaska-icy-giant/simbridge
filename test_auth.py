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
