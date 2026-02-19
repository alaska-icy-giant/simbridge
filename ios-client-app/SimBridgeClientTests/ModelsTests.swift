import XCTest

final class ModelsTests: XCTestCase {

    // MARK: - SmsCommand encodes with snake_case

    func test_sms_command_encodes() throws {
        let command = SmsCommand(toDeviceId: 20, sim: 1, to: "+1234567890", body: "Hello")

        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .useDefaultKeys // Our CodingKeys handle snake_case
        let data = try encoder.encode(command)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["to_device_id"] as? Int, 20)
        XCTAssertEqual(dict["sim"] as? Int, 1)
        XCTAssertEqual(dict["to"] as? String, "+1234567890")
        XCTAssertEqual(dict["body"] as? String, "Hello")

        // Verify it uses snake_case key, not camelCase
        XCTAssertNil(dict["toDeviceId"], "Should use snake_case key 'to_device_id', not camelCase")
    }

    // MARK: - CallCommand encodes with snake_case

    func test_call_command_encodes() throws {
        let command = CallCommand(toDeviceId: 20, sim: 2, to: "+9876543210")

        let encoder = JSONEncoder()
        let data = try encoder.encode(command)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["to_device_id"] as? Int, 20)
        XCTAssertEqual(dict["sim"] as? Int, 2)
        XCTAssertEqual(dict["to"] as? String, "+9876543210")
        XCTAssertNil(dict["toDeviceId"], "Should use snake_case key")
    }

    // MARK: - PairConfirm encodes

    func test_pair_confirm_encodes() throws {
        let confirm = PairConfirm(code: "654321", clientDeviceId: 10)

        let encoder = JSONEncoder()
        let data = try encoder.encode(confirm)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["code"] as? String, "654321")
        XCTAssertEqual(dict["client_device_id"] as? Int, 10)
        XCTAssertNil(dict["clientDeviceId"], "Should use snake_case key")
    }

    // MARK: - HistoryEntry decodes all fields

    func test_history_entry_decodes() throws {
        let json = """
        {
            "id": 42,
            "from_device_id": 10,
            "to_device_id": 20,
            "msg_type": "command",
            "payload": {"cmd": "SEND_SMS", "sim": 1, "to": "+1234567890", "body": "Hi"},
            "created_at": "2026-02-19T10:30:00"
        }
        """
        let data = json.data(using: .utf8)!
        let entry = try JSONDecoder().decode(HistoryEntry.self, from: data)

        XCTAssertEqual(entry.id, 42)
        XCTAssertEqual(entry.fromDeviceId, 10)
        XCTAssertEqual(entry.toDeviceId, 20)
        XCTAssertEqual(entry.msgType, "command")
        XCTAssertEqual(entry.createdAt, "2026-02-19T10:30:00")
        XCTAssertEqual(entry.payload["cmd"], .string("SEND_SMS"))
        XCTAssertEqual(entry.payload["sim"], .int(1))
        XCTAssertEqual(entry.payload["to"], .string("+1234567890"))
        XCTAssertEqual(entry.payload["body"], .string("Hi"))
    }

    func test_history_entry_decodes_null_created_at() throws {
        let json = """
        {
            "id": 1,
            "from_device_id": 10,
            "to_device_id": 20,
            "msg_type": "event",
            "payload": {},
            "created_at": null
        }
        """
        let data = json.data(using: .utf8)!
        let entry = try JSONDecoder().decode(HistoryEntry.self, from: data)

        XCTAssertNil(entry.createdAt)
    }

    // MARK: - WsMessage decodes with optional fields

    func test_ws_message_decodes() throws {
        let json = SampleData.wsIncomingSmsJSON
        let data = json.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        XCTAssertEqual(message.type, "INCOMING_SMS")
        XCTAssertEqual(message.from, "+1234567890")
        XCTAssertEqual(message.body, "Test message")
        XCTAssertEqual(message.sim, 1)

        // Optional fields should be nil
        XCTAssertNil(message.status)
        XCTAssertNil(message.state)
        XCTAssertNil(message.reqId)
        XCTAssertNil(message.deviceId)
    }

    func test_ws_message_decodes_minimal() throws {
        let json = """
        {"type": "connected", "device_id": 10}
        """
        let data = json.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        XCTAssertEqual(message.type, "connected")
        XCTAssertEqual(message.deviceId, 10)
        XCTAssertNil(message.from)
        XCTAssertNil(message.body)
        XCTAssertNil(message.sim)
    }

    func test_ws_message_decodes_sim_info() throws {
        let data = SampleData.wsSimInfoJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        XCTAssertEqual(message.type, "SIM_INFO")
        XCTAssertNotNil(message.sims)
        XCTAssertEqual(message.sims?.count, 2)
        XCTAssertEqual(message.sims?[0].slot, 1)
        XCTAssertEqual(message.sims?[0].carrier, "T-Mobile")
    }

    // MARK: - ConnectionStatus enum

    func test_connection_status_enum() {
        // Verify all 3 cases exist and are distinct
        let connected = ConnectionStatus.connected
        let connecting = ConnectionStatus.connecting
        let disconnected = ConnectionStatus.disconnected

        XCTAssertNotEqual(connected, connecting)
        XCTAssertNotEqual(connected, disconnected)
        XCTAssertNotEqual(connecting, disconnected)

        // Verify raw values
        XCTAssertEqual(connected.rawValue, "connected")
        XCTAssertEqual(connecting.rawValue, "connecting")
        XCTAssertEqual(disconnected.rawValue, "disconnected")
    }

    // MARK: - GoogleAuthRequest encodes

    func test_google_auth_request_encodes() throws {
        let request = GoogleAuthRequest(idToken: "my-google-id-token")

        let encoder = JSONEncoder()
        let data = try encoder.encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["id_token"] as? String, "my-google-id-token")
        XCTAssertNil(dict["idToken"], "Should use snake_case key 'id_token', not camelCase")
    }

    // MARK: - RelayResponse decodes

    func test_relay_response_decodes() throws {
        let data = SampleData.relaySentResponseJSON.data(using: .utf8)!
        let response = try JSONDecoder().decode(RelayResponse.self, from: data)

        XCTAssertEqual(response.status, "sent")
        XCTAssertEqual(response.reqId, "abc-123")
    }

    // MARK: - LoginResponse decodes

    func test_login_response_decodes() throws {
        let data = SampleData.loginResponseJSON.data(using: .utf8)!
        let response = try JSONDecoder().decode(LoginResponse.self, from: data)

        XCTAssertEqual(response.userId, 1)
        XCTAssertFalse(response.token.isEmpty)
    }

    // MARK: - AnyCodableValue

    func test_any_codable_value_types() throws {
        let json = """
        {"str": "hello", "num": 42, "flag": true, "nothing": null, "decimal": 3.14}
        """
        let data = json.data(using: .utf8)!
        let dict = try JSONDecoder().decode([String: AnyCodableValue].self, from: data)

        XCTAssertEqual(dict["str"], .string("hello"))
        XCTAssertEqual(dict["num"], .int(42))
        XCTAssertEqual(dict["flag"], .bool(true))
        XCTAssertEqual(dict["nothing"], .null)
        XCTAssertEqual(dict["decimal"], .double(3.14))
    }
}
