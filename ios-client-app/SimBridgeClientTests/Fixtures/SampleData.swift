import Foundation

/// Canned JSON response strings for all SimBridge API endpoints.
enum SampleData {

    // MARK: - Auth

    static let loginResponseJSON = """
    {"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxfQ.test", "user_id": 1}
    """

    static let registerResponseJSON = """
    {"id": 1, "username": "testuser"}
    """

    // MARK: - Devices

    static let deviceResponseJSON = """
    {"id": 10, "name": "iPhone Client", "type": "client", "is_online": false}
    """

    static let devicesListJSON = """
    [
        {"id": 10, "name": "iPhone Client", "type": "client", "is_online": true, "last_seen": "2026-02-19T12:00:00"},
        {"id": 20, "name": "Android Host", "type": "host", "is_online": true, "last_seen": "2026-02-19T12:00:00"}
    ]
    """

    // MARK: - Pairing

    static let pairConfirmResponseJSON = """
    {"status": "paired", "pairing_id": 5, "host_device_id": 20}
    """

    // MARK: - SMS / Call relay

    static let relaySentResponseJSON = """
    {"status": "sent", "req_id": "abc-123"}
    """

    // MARK: - SIMs

    static let simsResponseJSON = """
    {"status": "sent", "req_id": "sim-req-1"}
    """

    // MARK: - History

    static let historyResponseJSON = """
    [
        {
            "id": 1,
            "from_device_id": 10,
            "to_device_id": 20,
            "msg_type": "command",
            "payload": {"type": "command", "cmd": "SEND_SMS", "sim": 1, "to": "+1234567890", "body": "Hello"},
            "created_at": "2026-02-19T10:30:00"
        },
        {
            "id": 2,
            "from_device_id": 20,
            "to_device_id": 10,
            "msg_type": "event",
            "payload": {"type": "INCOMING_SMS", "from": "+0987654321", "body": "Hi back"},
            "created_at": "2026-02-19T10:31:00"
        }
    ]
    """

    static let emptyHistoryJSON = "[]"

    // MARK: - WebSocket messages

    static let wsIncomingSmsJSON = """
    {"type": "INCOMING_SMS", "from": "+1234567890", "body": "Test message", "sim": 1}
    """

    static let wsSmsSentJSON = """
    {"type": "SMS_SENT", "req_id": "abc-123", "status": "delivered"}
    """

    static let wsCallStateJSON = """
    {"type": "CALL_STATE", "state": "active", "to": "+1234567890", "sim": 1}
    """

    static let wsSimInfoJSON = """
    {"type": "SIM_INFO", "sims": [{"slot": 1, "carrier": "T-Mobile", "number": "+1111111111"}, {"slot": 2, "carrier": "AT&T", "number": "+2222222222"}]}
    """

    static let wsErrorJSON = """
    {"type": "ERROR", "message": "Host device is offline"}
    """

    static let wsConnectedJSON = """
    {"type": "connected", "device_id": 10}
    """

    static let wsMalformedJSON = """
    {not valid json at all
    """

    static let wsUnknownEventJSON = """
    {"type": "SOME_FUTURE_EVENT", "data": "whatever"}
    """

    // MARK: - Error responses

    static let error401JSON = """
    {"detail": "Invalid credentials"}
    """

    static let error400JSON = """
    {"detail": "Invalid or expired pairing code"}
    """

    static let error503JSON = """
    {"detail": "Host device is offline"}
    """

    // MARK: - Helpers

    static func data(_ json: String) -> Data {
        return json.data(using: .utf8)!
    }

    static func httpResponse(url: URL, statusCode: Int = 200) -> HTTPURLResponse {
        return HTTPURLResponse(
            url: url,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
    }
}
