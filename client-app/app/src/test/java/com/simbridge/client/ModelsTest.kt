package com.simbridge.client

import com.google.gson.Gson
import com.simbridge.client.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelsTest {

    private lateinit var gson: Gson

    @BeforeEach
    fun setUp() {
        gson = Gson()
    }

    // ── WsMessage Round-Trip: Command ──

    @Test
    fun `test WsMessage command round-trip with all fields`() {
        val original = WsMessage(
            type = "command",
            cmd = "SEND_SMS",
            to = "+15551234567",
            body = "Hello World",
            sim = 1,
            reqId = "req-123",
            fromDeviceId = 42,
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, WsMessage::class.java)

        assertEquals(original.type, deserialized.type)
        assertEquals(original.cmd, deserialized.cmd)
        assertEquals(original.to, deserialized.to)
        assertEquals(original.body, deserialized.body)
        assertEquals(original.sim, deserialized.sim)
        assertEquals(original.reqId, deserialized.reqId)
        assertEquals(original.fromDeviceId, deserialized.fromDeviceId)
    }

    // ── WsMessage Round-Trip: Event ──

    @Test
    fun `test WsMessage event round-trip`() {
        val original = WsMessage(
            type = "event",
            event = "SMS_SENT",
            status = "ok",
            reqId = "req-456",
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, WsMessage::class.java)

        assertEquals("event", deserialized.type)
        assertEquals("SMS_SENT", deserialized.event)
        assertEquals("ok", deserialized.status)
        assertEquals("req-456", deserialized.reqId)
    }

    // ── WsMessage Round-Trip: WebRTC ──

    @Test
    fun `test WsMessage webrtc round-trip`() {
        val original = WsMessage(
            type = "webrtc",
            action = "offer",
            sdp = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n",
            sdpMid = "audio",
            sdpMLineIndex = 0,
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, WsMessage::class.java)

        assertEquals("webrtc", deserialized.type)
        assertEquals("offer", deserialized.action)
        assertEquals(original.sdp, deserialized.sdp)
        assertEquals("audio", deserialized.sdpMid)
        assertEquals(0, deserialized.sdpMLineIndex)
    }

    // ── Snake_case JSON Keys ──

    @Test
    fun `test WsMessage serialization uses snake_case for req_id`() {
        val msg = WsMessage(type = "event", reqId = "abc")
        val json = gson.toJson(msg)
        assertTrue(json.contains("\"req_id\""), "Expected snake_case key 'req_id' in JSON: $json")
    }

    @Test
    fun `test WsMessage serialization uses snake_case for from_device_id`() {
        val msg = WsMessage(type = "event", fromDeviceId = 7)
        val json = gson.toJson(msg)
        assertTrue(json.contains("\"from_device_id\""), "Expected snake_case key 'from_device_id' in JSON: $json")
    }

    @Test
    fun `test WsMessage serialization uses snake_case for to_device_id`() {
        val msg = WsMessage(type = "command", toDeviceId = 3)
        val json = gson.toJson(msg)
        assertTrue(json.contains("\"to_device_id\""), "Expected snake_case key 'to_device_id' in JSON: $json")
    }

    @Test
    fun `test WsMessage serialization uses snake_case for target_device_id`() {
        val msg = WsMessage(type = "command", targetDeviceId = 10)
        val json = gson.toJson(msg)
        assertTrue(json.contains("\"target_device_id\""), "Expected snake_case key 'target_device_id' in JSON: $json")
    }

    // ── Nullable Fields ──

    @Test
    fun `test WsMessage nullable fields decode as null when missing`() {
        val json = """{"type":"command"}"""
        val msg = gson.fromJson(json, WsMessage::class.java)

        assertEquals("command", msg.type)
        assertNull(msg.cmd)
        assertNull(msg.event)
        assertNull(msg.action)
        assertNull(msg.sim)
        assertNull(msg.to)
        assertNull(msg.from)
        assertNull(msg.body)
        assertNull(msg.status)
        assertNull(msg.state)
        assertNull(msg.sims)
        assertNull(msg.sdp)
        assertNull(msg.candidate)
        assertNull(msg.sdpMid)
        assertNull(msg.sdpMLineIndex)
        assertNull(msg.reqId)
        assertNull(msg.fromDeviceId)
        assertNull(msg.toDeviceId)
        assertNull(msg.error)
        assertNull(msg.targetDeviceId)
    }

    @Test
    fun `test WsMessage with only type serializes minimal JSON`() {
        val msg = WsMessage(type = "ping")
        val json = gson.toJson(msg)
        val deserialized = gson.fromJson(json, WsMessage::class.java)

        assertEquals("ping", deserialized.type)
        assertNull(deserialized.cmd)
        assertNull(deserialized.reqId)
    }

    // ── SmsEntry Round-Trip ──

    @Test
    fun `test SmsEntry round-trip`() {
        val original = SmsEntry(
            timestamp = 1700000000000L,
            direction = "received",
            sim = 1,
            address = "+15551234567",
            body = "Hello",
            status = "delivered",
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, SmsEntry::class.java)

        assertEquals(1700000000000L, deserialized.timestamp)
        assertEquals("received", deserialized.direction)
        assertEquals(1, deserialized.sim)
        assertEquals("+15551234567", deserialized.address)
        assertEquals("Hello", deserialized.body)
        assertEquals("delivered", deserialized.status)
    }

    @Test
    fun `test SmsEntry with null optional fields`() {
        val original = SmsEntry(
            direction = "sent",
            sim = null,
            address = "+15559999999",
            body = "Test",
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, SmsEntry::class.java)

        assertNull(deserialized.sim)
        assertNull(deserialized.status)
    }

    @Test
    fun `test SmsEntry has default timestamp`() {
        val before = System.currentTimeMillis()
        val entry = SmsEntry(direction = "sent", sim = 1, address = "123", body = "test")
        val after = System.currentTimeMillis()

        assertTrue(entry.timestamp in before..after)
    }

    // ── LogEntry Round-Trip ──

    @Test
    fun `test LogEntry round-trip`() {
        val original = LogEntry(
            timestamp = 1700000000000L,
            direction = "OUT",
            summary = "SEND_SMS -> +15551234567",
        )

        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, LogEntry::class.java)

        assertEquals(1700000000000L, deserialized.timestamp)
        assertEquals("OUT", deserialized.direction)
        assertEquals("SEND_SMS -> +15551234567", deserialized.summary)
    }

    @Test
    fun `test LogEntry has default timestamp`() {
        val before = System.currentTimeMillis()
        val entry = LogEntry(direction = "IN", summary = "test")
        val after = System.currentTimeMillis()

        assertTrue(entry.timestamp in before..after)
    }

    // ── SimInfo Round-Trip ──

    @Test
    fun `test SimInfo serialization round-trip`() {
        val original = SimInfo(slot = 1, carrier = "T-Mobile", number = "+15551111111")
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, SimInfo::class.java)

        assertEquals(1, deserialized.slot)
        assertEquals("T-Mobile", deserialized.carrier)
        assertEquals("+15551111111", deserialized.number)
    }

    @Test
    fun `test SimInfo with null number`() {
        val original = SimInfo(slot = 2, carrier = "AT&T", number = null)
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, SimInfo::class.java)

        assertEquals(2, deserialized.slot)
        assertEquals("AT&T", deserialized.carrier)
        assertNull(deserialized.number)
    }

    // ── WsMessage with SimInfo list ──

    @Test
    fun `test WsMessage with sims list round-trip`() {
        val sims = listOf(
            SimInfo(slot = 1, carrier = "T-Mobile", number = "+15551111111"),
            SimInfo(slot = 2, carrier = "AT&T", number = null),
        )
        val original = WsMessage(type = "event", event = "SIM_INFO", sims = sims)
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, WsMessage::class.java)

        assertNotNull(deserialized.sims)
        assertEquals(2, deserialized.sims!!.size)
        assertEquals("T-Mobile", deserialized.sims!![0].carrier)
        assertNull(deserialized.sims!![1].number)
    }

    // ── ConnectionStatus Enum ──

    @Test
    fun `test ConnectionStatus enum values`() {
        val values = ConnectionStatus.values()
        assertEquals(3, values.size)
        assertTrue(values.contains(ConnectionStatus.DISCONNECTED))
        assertTrue(values.contains(ConnectionStatus.CONNECTING))
        assertTrue(values.contains(ConnectionStatus.CONNECTED))
    }

    // ── CallState Enum ──

    @Test
    fun `test CallState enum values`() {
        val values = CallState.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(CallState.IDLE))
        assertTrue(values.contains(CallState.DIALING))
        assertTrue(values.contains(CallState.RINGING))
        assertTrue(values.contains(CallState.ACTIVE))
    }

    // ── REST Models: LoginRequest ──

    @Test
    fun `test LoginRequest serialization`() {
        val req = LoginRequest(username = "admin", password = "secret")
        val json = gson.toJson(req)
        val deserialized = gson.fromJson(json, LoginRequest::class.java)

        assertEquals("admin", deserialized.username)
        assertEquals("secret", deserialized.password)
    }

    // ── REST Models: RegisterRequest ──

    @Test
    fun `test RegisterRequest serialization`() {
        val req = RegisterRequest(username = "user1", password = "pass123")
        val json = gson.toJson(req)
        val deserialized = gson.fromJson(json, RegisterRequest::class.java)

        assertEquals("user1", deserialized.username)
        assertEquals("pass123", deserialized.password)
    }

    // ── REST Models: AuthResponse ──

    @Test
    fun `test AuthResponse round-trip with user_id`() {
        val json = """{"token":"jwt-token-abc","user_id":42}"""
        val resp = gson.fromJson(json, AuthResponse::class.java)

        assertEquals("jwt-token-abc", resp.token)
        assertEquals(42, resp.userId)
    }

    @Test
    fun `test AuthResponse with null user_id`() {
        val json = """{"token":"jwt-token-abc"}"""
        val resp = gson.fromJson(json, AuthResponse::class.java)

        assertEquals("jwt-token-abc", resp.token)
        assertNull(resp.userId)
    }

    // ── REST Models: DeviceInfo ──

    @Test
    fun `test DeviceInfo with is_online and last_seen`() {
        val json = """{"id":1,"name":"Host Phone","type":"host","is_online":true,"last_seen":"2024-01-01T00:00:00"}"""
        val resp = gson.fromJson(json, DeviceInfo::class.java)

        assertEquals(1, resp.id)
        assertEquals("Host Phone", resp.name)
        assertEquals("host", resp.type)
        assertTrue(resp.isOnline)
        assertEquals("2024-01-01T00:00:00", resp.lastSeen)
    }

    @Test
    fun `test DeviceInfo with defaults for optional fields`() {
        val json = """{"id":2,"name":"Client","type":"client"}"""
        val resp = gson.fromJson(json, DeviceInfo::class.java)

        assertFalse(resp.isOnline)
        assertNull(resp.lastSeen)
    }

    // ── REST Models: SmsRequest ──

    @Test
    fun `test SmsRequest uses snake_case to_device_id`() {
        val req = SmsRequest(toDeviceId = 5, sim = 1, to = "+15551234567", body = "Hello")
        val json = gson.toJson(req)

        assertTrue(json.contains("\"to_device_id\""), "Expected snake_case in JSON: $json")

        val deserialized = gson.fromJson(json, SmsRequest::class.java)
        assertEquals(5, deserialized.toDeviceId)
        assertEquals(1, deserialized.sim)
        assertEquals("+15551234567", deserialized.to)
        assertEquals("Hello", deserialized.body)
    }

    // ── REST Models: CallRequest ──

    @Test
    fun `test CallRequest uses snake_case to_device_id`() {
        val req = CallRequest(toDeviceId = 5, sim = 2, to = "+15559876543")
        val json = gson.toJson(req)

        assertTrue(json.contains("\"to_device_id\""), "Expected snake_case in JSON: $json")

        val deserialized = gson.fromJson(json, CallRequest::class.java)
        assertEquals(5, deserialized.toDeviceId)
        assertEquals(2, deserialized.sim)
        assertEquals("+15559876543", deserialized.to)
    }

    // ── REST Models: PairConfirmRequest ──

    @Test
    fun `test PairConfirmRequest uses snake_case client_device_id`() {
        val req = PairConfirmRequest(code = "123456", clientDeviceId = 7)
        val json = gson.toJson(req)

        assertTrue(json.contains("\"client_device_id\""), "Expected snake_case in JSON: $json")

        val deserialized = gson.fromJson(json, PairConfirmRequest::class.java)
        assertEquals("123456", deserialized.code)
        assertEquals(7, deserialized.clientDeviceId)
    }

    // ── REST Models: PairConfirmResponse ──

    @Test
    fun `test PairConfirmResponse round-trip with host_device_id`() {
        val json = """{"status":"ok","pairing_id":99,"host_device_id":5}"""
        val resp = gson.fromJson(json, PairConfirmResponse::class.java)

        assertEquals("ok", resp.status)
        assertEquals(99, resp.pairingId)
        assertEquals(5, resp.hostDeviceId)
    }

    @Test
    fun `test PairConfirmResponse with null optional fields`() {
        val json = """{"status":"error"}"""
        val resp = gson.fromJson(json, PairConfirmResponse::class.java)

        assertEquals("error", resp.status)
        assertNull(resp.pairingId)
        assertNull(resp.hostDeviceId)
    }

    // ── REST Models: CommandResponse ──

    @Test
    fun `test CommandResponse round-trip`() {
        val json = """{"status":"ok","req_id":"abc-123"}"""
        val resp = gson.fromJson(json, CommandResponse::class.java)

        assertEquals("ok", resp.status)
        assertEquals("abc-123", resp.reqId)
    }

    // ── REST Models: HistoryEntry ──

    @Test
    fun `test HistoryEntry round-trip with snake_case fields`() {
        val json = """{
            "id": 1,
            "from_device_id": 2,
            "to_device_id": 3,
            "msg_type": "sms",
            "payload": "{\"to\":\"+15551234567\",\"body\":\"Hi\"}",
            "created_at": "2024-01-15T10:30:00"
        }"""

        val entry = gson.fromJson(json, HistoryEntry::class.java)

        assertEquals(1, entry.id)
        assertEquals(2, entry.fromDeviceId)
        assertEquals(3, entry.toDeviceId)
        assertEquals("sms", entry.msgType)
        assertTrue(entry.payload.contains("+15551234567"))
        assertEquals("2024-01-15T10:30:00", entry.createdAt)
    }

    // ── SmsConversation ──

    @Test
    fun `test SmsConversation holds messages grouped by address`() {
        val messages = listOf(
            SmsEntry(direction = "sent", sim = 1, address = "+15551234567", body = "Hello"),
            SmsEntry(direction = "received", sim = 1, address = "+15551234567", body = "Hi back"),
        )
        val convo = SmsConversation(address = "+15551234567", messages = messages)

        assertEquals("+15551234567", convo.address)
        assertEquals(2, convo.messages.size)
    }

    // ── DeviceRegisterRequest ──

    @Test
    fun `test DeviceRegisterRequest defaults to client type`() {
        val req = DeviceRegisterRequest(name = "My Phone")
        val json = gson.toJson(req)
        val deserialized = gson.fromJson(json, DeviceRegisterRequest::class.java)

        assertEquals("My Phone", deserialized.name)
        assertEquals("client", deserialized.type)
    }
}
