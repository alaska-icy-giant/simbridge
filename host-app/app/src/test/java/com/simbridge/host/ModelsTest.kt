package com.simbridge.host

import com.google.gson.Gson
import com.simbridge.host.data.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelsTest {

    private lateinit var gson: Gson

    @BeforeEach
    fun setUp() {
        gson = Gson()
    }

    // ── WsMessage Serialization Round-Trip ──

    @Test
    fun `test WsMessage serialization round-trip with all fields`() {
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

    @Test
    fun `test WsMessage serialization uses snake_case for annotated fields`() {
        val msg = WsMessage(
            type = "event",
            reqId = "abc",
            fromDeviceId = 7,
        )

        val json = gson.toJson(msg)

        assertTrue(json.contains("\"req_id\""), "Expected snake_case key 'req_id' in JSON: $json")
        assertTrue(json.contains("\"from_device_id\""), "Expected snake_case key 'from_device_id' in JSON: $json")
    }

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
    }

    @Test
    fun `test WsMessage with only type field serializes minimal JSON`() {
        val msg = WsMessage(type = "event")
        val json = gson.toJson(msg)
        val deserialized = gson.fromJson(json, WsMessage::class.java)

        assertEquals("event", deserialized.type)
        assertNull(deserialized.cmd)
        assertNull(deserialized.reqId)
    }

    // ── SimInfo Serialization ──

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

    // ── LogEntry ──

    @Test
    fun `test LogEntry has default timestamp`() {
        val before = System.currentTimeMillis()
        val entry = LogEntry(direction = "IN", summary = "test")
        val after = System.currentTimeMillis()

        assertTrue(entry.timestamp in before..after)
    }

    // ── REST API Models ──

    @Test
    fun `test LoginRequest serialization`() {
        val req = LoginRequest(username = "admin", password = "secret")
        val json = gson.toJson(req)
        val deserialized = gson.fromJson(json, LoginRequest::class.java)

        assertEquals("admin", deserialized.username)
        assertEquals("secret", deserialized.password)
    }

    @Test
    fun `test DeviceResponse with paired_with`() {
        val json = """{"id":1,"name":"Host Phone","type":"host","paired_with":5}"""
        val resp = gson.fromJson(json, DeviceResponse::class.java)

        assertEquals(1, resp.id)
        assertEquals("Host Phone", resp.name)
        assertEquals("host", resp.type)
        assertEquals(5, resp.pairedWith)
    }

    @Test
    fun `test DeviceResponse with null paired_with`() {
        val json = """{"id":2,"name":"New Device","type":"host"}"""
        val resp = gson.fromJson(json, DeviceResponse::class.java)

        assertNull(resp.pairedWith)
    }

    @Test
    fun `test PairResponse serialization`() {
        val json = """{"status":"ok","paired_device_id":10}"""
        val resp = gson.fromJson(json, PairResponse::class.java)

        assertEquals("ok", resp.status)
        assertEquals(10, resp.pairedDeviceId)
    }
}
