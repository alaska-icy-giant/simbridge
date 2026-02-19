from datetime import datetime, timezone

from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    create_engine,
)
from sqlalchemy.orm import DeclarativeBase, Session, relationship, sessionmaker


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True)
    username = Column(String(50), unique=True, nullable=False, index=True)
    password_hash = Column(String(128), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    devices = relationship("Device", back_populates="user")


class Device(Base):
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    name = Column(String(100), nullable=False)
    device_type = Column(String(10), nullable=False)  # "host" or "client"
    device_token = Column(String(255), nullable=True)  # future FCM token
    is_online = Column(Boolean, default=False)
    last_seen = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    user = relationship("User", back_populates="devices")
    pairings_as_host = relationship(
        "Pairing",
        back_populates="host_device",
        foreign_keys="Pairing.host_device_id",
    )
    pairings_as_client = relationship(
        "Pairing",
        back_populates="client_device",
        foreign_keys="Pairing.client_device_id",
    )


class PairingCode(Base):
    __tablename__ = "pairing_codes"

    id = Column(Integer, primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    host_device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    code = Column(String(6), nullable=False, index=True)
    expires_at = Column(DateTime, nullable=False)
    used = Column(Boolean, default=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class Pairing(Base):
    __tablename__ = "pairings"

    id = Column(Integer, primary_key=True)
    host_device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    client_device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    host_device = relationship(
        "Device", back_populates="pairings_as_host", foreign_keys=[host_device_id]
    )
    client_device = relationship(
        "Device", back_populates="pairings_as_client", foreign_keys=[client_device_id]
    )


class MessageLog(Base):
    __tablename__ = "message_logs"

    id = Column(Integer, primary_key=True)
    from_device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    to_device_id = Column(Integer, ForeignKey("devices.id"), nullable=False)
    msg_type = Column(String(20), nullable=False)  # command, event
    payload = Column(Text, nullable=False)  # JSON string
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


def init_db(db_path: str = "simbridge.db") -> sessionmaker:
    engine = create_engine(f"sqlite:///{db_path}", echo=False)
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine)
