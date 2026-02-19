import XCTest
@testable import SimBridgeHost

/// Tests for Codable model encoding/decoding round-trips.
final class ModelsTests: XCTestCase {

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    // MARK: - WsMessage Serialization Round-Trip

    func testWsMessageSerializationRoundTripWithAllFields() throws {
        let original = WsMessage(
            type: "command",
            cmd: "SEND_SMS",
            to: "+15551234567",
            body: "Hello World",
            sim: 1,
            reqId: "req-123",
            fromDeviceId: 42
        )

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(WsMessage.self, from: data)

        XCTAssertEqual(decoded.type, original.type)
        XCTAssertEqual(decoded.cmd, original.cmd)
        XCTAssertEqual(decoded.to, original.to)
        XCTAssertEqual(decoded.body, original.body)
        XCTAssertEqual(decoded.sim, original.sim)
        XCTAssertEqual(decoded.reqId, original.reqId)
        XCTAssertEqual(decoded.fromDeviceId, original.fromDeviceId)
    }

    func testWsMessageUsesSnakeCaseKeys() throws {
        let msg = WsMessage(type: "event", reqId: "abc", fromDeviceId: 7)

        let data = try encoder.encode(msg)
        let json = String(data: data, encoding: .utf8)!

        XCTAssertTrue(json.contains("\"req_id\""),
                       "Expected snake_case key 'req_id' in JSON: \(json)")
        XCTAssertTrue(json.contains("\"from_device_id\""),
                       "Expected snake_case key 'from_device_id' in JSON: \(json)")
    }

    func testWsMessageEventRoundTrip() throws {
        let original = WsMessage(type: "event", event: "SMS_SENT",
                                  status: "ok", reqId: "req-456")

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(WsMessage.self, from: data)

        XCTAssertEqual(decoded.type, "event")
        XCTAssertEqual(decoded.event, "SMS_SENT")
        XCTAssertEqual(decoded.status, "ok")
        XCTAssertEqual(decoded.reqId, "req-456")
    }

    func testWsMessageWebrtcRoundTrip() throws {
        let original = WsMessage(
            type: "webrtc",
            action: "offer",
            sdp: "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n",
            sdpMid: "audio",
            sdpMLineIndex: 0
        )

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(WsMessage.self, from: data)

        XCTAssertEqual(decoded.type, "webrtc")
        XCTAssertEqual(decoded.action, "offer")
        XCTAssertEqual(decoded.sdp, original.sdp)
        XCTAssertEqual(decoded.sdpMid, "audio")
        XCTAssertEqual(decoded.sdpMLineIndex, 0)
    }

    // MARK: - Nullable Fields

    func testWsMessageNullableFieldsDecodeAsNilWhenMissing() throws {
        let json = """
        {"type": "command"}
        """.data(using: .utf8)!

        let msg = try decoder.decode(WsMessage.self, from: json)

        XCTAssertEqual(msg.type, "command")
        XCTAssertNil(msg.cmd)
        XCTAssertNil(msg.event)
        XCTAssertNil(msg.action)
        XCTAssertNil(msg.sim)
        XCTAssertNil(msg.to)
        XCTAssertNil(msg.from)
        XCTAssertNil(msg.body)
        XCTAssertNil(msg.status)
        XCTAssertNil(msg.state)
        XCTAssertNil(msg.sims)
        XCTAssertNil(msg.sdp)
        XCTAssertNil(msg.candidate)
        XCTAssertNil(msg.sdpMid)
        XCTAssertNil(msg.sdpMLineIndex)
        XCTAssertNil(msg.reqId)
        XCTAssertNil(msg.fromDeviceId)
    }

    func testWsMessageWithOnlyTypeSerializesMinimalJSON() throws {
        let msg = WsMessage(type: "event")

        let data = try encoder.encode(msg)
        let decoded = try decoder.decode(WsMessage.self, from: data)

        XCTAssertEqual(decoded.type, "event")
        XCTAssertNil(decoded.cmd)
        XCTAssertNil(decoded.reqId)
    }

    // MARK: - SimInfo Serialization

    func testSimInfoSerializationRoundTrip() throws {
        let original = SimInfo(slot: 1, carrier: "T-Mobile", number: "+15551111111")

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(SimInfo.self, from: data)

        XCTAssertEqual(decoded.slot, 1)
        XCTAssertEqual(decoded.carrier, "T-Mobile")
        XCTAssertEqual(decoded.number, "+15551111111")
    }

    func testSimInfoWithNullNumber() throws {
        let original = SimInfo(slot: 2, carrier: "AT&T", number: nil)

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(SimInfo.self, from: data)

        XCTAssertEqual(decoded.slot, 2)
        XCTAssertEqual(decoded.carrier, "AT&T")
        XCTAssertNil(decoded.number)
    }

    // MARK: - WsMessage with SimInfo List

    func testWsMessageWithSimsListRoundTrip() throws {
        let sims = [
            SimInfo(slot: 1, carrier: "T-Mobile", number: "+15551111111"),
            SimInfo(slot: 2, carrier: "AT&T", number: nil),
        ]
        let original = WsMessage(type: "event", event: "SIM_INFO", sims: sims)

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(WsMessage.self, from: data)

        XCTAssertNotNil(decoded.sims)
        XCTAssertEqual(decoded.sims?.count, 2)
        XCTAssertEqual(decoded.sims?[0].carrier, "T-Mobile")
        XCTAssertNil(decoded.sims?[1].number)
    }

    // MARK: - ConnectionStatus Enum

    func testConnectionStatusEnumValues() {
        let values: [ConnectionStatus] = [.disconnected, .connecting, .connected]
        XCTAssertEqual(values.count, 3)
        XCTAssertTrue(values.contains(.disconnected))
        XCTAssertTrue(values.contains(.connecting))
        XCTAssertTrue(values.contains(.connected))
    }

    // MARK: - LogEntry

    func testLogEntryProperties() {
        let entry = LogEntry(direction: "IN", summary: "test message")

        XCTAssertEqual(entry.direction, "IN")
        XCTAssertEqual(entry.summary, "test message")
    }

    // MARK: - REST API Models

    func testLoginRequestSerialization() throws {
        let req = LoginRequest(username: "admin", password: "secret")

        let data = try encoder.encode(req)
        let decoded = try decoder.decode(LoginRequest.self, from: data)

        XCTAssertEqual(decoded.username, "admin")
        XCTAssertEqual(decoded.password, "secret")
    }

    func testDeviceResponseWithPairedWith() throws {
        let json = """
        {"id": 1, "name": "Host Phone", "type": "host", "paired_with": 5}
        """.data(using: .utf8)!

        let resp = try decoder.decode(DeviceResponse.self, from: json)

        XCTAssertEqual(resp.id, 1)
        XCTAssertEqual(resp.name, "Host Phone")
        XCTAssertEqual(resp.type, "host")
        XCTAssertEqual(resp.pairedWith, 5)
    }

    func testDeviceResponseWithNullPairedWith() throws {
        let json = """
        {"id": 2, "name": "New Device", "type": "host"}
        """.data(using: .utf8)!

        let resp = try decoder.decode(DeviceResponse.self, from: json)

        XCTAssertNil(resp.pairedWith)
    }

    func testPairResponseSerialization() throws {
        let json = """
        {"status": "ok", "paired_device_id": 10}
        """.data(using: .utf8)!

        let resp = try decoder.decode(PairResponse.self, from: json)

        XCTAssertEqual(resp.status, "ok")
        XCTAssertEqual(resp.pairedDeviceId, 10)
    }

    // MARK: - GoogleAuthRequest Serialization

    func testGoogleAuthRequestSerialization() throws {
        let req = GoogleAuthRequest(idToken: "my-google-token")

        let data = try encoder.encode(req)
        let json = String(data: data, encoding: .utf8)!

        XCTAssertTrue(json.contains("\"id_token\""),
                       "Expected snake_case key 'id_token' in JSON: \(json)")
        XCTAssertTrue(json.contains("my-google-token"))
    }

    func testGoogleAuthRequestRoundTrip() throws {
        let original = GoogleAuthRequest(idToken: "token-abc-123")

        let data = try encoder.encode(original)
        let decoded = try decoder.decode(GoogleAuthRequest.self, from: data)

        XCTAssertEqual(decoded.idToken, "token-abc-123")
    }

    // MARK: - JSON from Server (External Format)

    func testDecodingServerFormatWsMessage() throws {
        let json = """
        {
            "type": "command",
            "cmd": "SEND_SMS",
            "to": "+15551234567",
            "body": "Hello",
            "sim": 1,
            "req_id": "abc-123",
            "from_device_id": 99
        }
        """.data(using: .utf8)!

        let msg = try decoder.decode(WsMessage.self, from: json)

        XCTAssertEqual(msg.type, "command")
        XCTAssertEqual(msg.cmd, "SEND_SMS")
        XCTAssertEqual(msg.to, "+15551234567")
        XCTAssertEqual(msg.body, "Hello")
        XCTAssertEqual(msg.sim, 1)
        XCTAssertEqual(msg.reqId, "abc-123")
        XCTAssertEqual(msg.fromDeviceId, 99)
    }
}
