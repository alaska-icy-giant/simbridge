// Models.swift
// Codable structs for SimBridge Client API communication.

import Foundation

// MARK: - Connection Status

enum ConnectionStatus: String, Codable {
    case connected
    case connecting
    case disconnected
}

// MARK: - WebSocket Message

struct WsMessage: Codable {
    let type: String?
    let cmd: String?
    let reqId: String?
    let sim: Int?
    let to: String?
    let body: String?
    let from: String?
    let fromDeviceId: Int?
    let toDeviceId: Int?
    let state: String?
    let error: String?
    let targetDeviceId: Int?
    let deviceId: Int?
    let payload: AnyCodableValue?

    enum CodingKeys: String, CodingKey {
        case type, cmd, sim, to, body, from, state, error, payload
        case reqId = "req_id"
        case fromDeviceId = "from_device_id"
        case toDeviceId = "to_device_id"
        case targetDeviceId = "target_device_id"
        case deviceId = "device_id"
    }
}

// MARK: - SIM Info

struct SimInfo: Codable, Identifiable {
    let slot: Int
    let carrier: String
    let number: String

    var id: Int { slot }
}

// MARK: - Auth

struct LoginRequest: Codable {
    let username: String
    let password: String
}

struct LoginResponse: Codable {
    let token: String
    let userId: Int

    enum CodingKeys: String, CodingKey {
        case token
        case userId = "user_id"
    }
}

struct RegisterResponse: Codable {
    let id: Int
    let username: String
}

// MARK: - Device

struct DeviceCreate: Codable {
    let name: String
    let type: String
}

struct DeviceResponse: Codable, Identifiable {
    let id: Int
    let name: String
    let type: String
    let isOnline: Bool
    let lastSeen: String?

    enum CodingKeys: String, CodingKey {
        case id, name, type
        case isOnline = "is_online"
        case lastSeen = "last_seen"
    }

    init(id: Int, name: String, type: String, isOnline: Bool, lastSeen: String? = nil) {
        self.id = id
        self.name = name
        self.type = type
        self.isOnline = isOnline
        self.lastSeen = lastSeen
    }
}

// MARK: - Pairing

struct PairConfirm: Codable {
    let code: String
    let clientDeviceId: Int

    enum CodingKeys: String, CodingKey {
        case code
        case clientDeviceId = "client_device_id"
    }
}

struct PairResponse: Codable {
    let status: String
    let pairingId: Int
    let hostDeviceId: Int?

    enum CodingKeys: String, CodingKey {
        case status
        case pairingId = "pairing_id"
        case hostDeviceId = "host_device_id"
    }
}

// MARK: - SMS Command

struct SmsCommand: Codable {
    let toDeviceId: Int
    let sim: Int
    let to: String
    let body: String

    enum CodingKeys: String, CodingKey {
        case sim, to, body
        case toDeviceId = "to_device_id"
    }
}

// MARK: - Call Command

struct CallCommand: Codable {
    let toDeviceId: Int
    let sim: Int
    let to: String

    enum CodingKeys: String, CodingKey {
        case sim, to
        case toDeviceId = "to_device_id"
    }
}

// MARK: - Relay Response

struct RelayResponse: Codable {
    let status: String
    let reqId: String?

    enum CodingKeys: String, CodingKey {
        case status
        case reqId = "req_id"
    }
}

// MARK: - History

struct HistoryEntry: Codable, Identifiable {
    let id: Int
    let fromDeviceId: Int
    let toDeviceId: Int
    let msgType: String
    let payload: AnyCodableValue
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case fromDeviceId = "from_device_id"
        case toDeviceId = "to_device_id"
        case msgType = "msg_type"
        case payload
        case createdAt = "created_at"
    }
}

// MARK: - Log Entry (for dashboard event feed)

struct LogEntry: Identifiable {
    let id = UUID()
    let timestamp: Date
    let direction: String  // "IN" or "OUT"
    let summary: String
}

// MARK: - AnyCodableValue

/// A type-erased Codable value for handling arbitrary JSON payloads.
enum AnyCodableValue: Codable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case dictionary([String: AnyCodableValue])
    case array([AnyCodableValue])
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self = .null
            return
        }
        if let boolVal = try? container.decode(Bool.self) {
            self = .bool(boolVal)
            return
        }
        if let intVal = try? container.decode(Int.self) {
            self = .int(intVal)
            return
        }
        if let doubleVal = try? container.decode(Double.self) {
            self = .double(doubleVal)
            return
        }
        if let strVal = try? container.decode(String.self) {
            self = .string(strVal)
            return
        }
        if let dictVal = try? container.decode([String: AnyCodableValue].self) {
            self = .dictionary(dictVal)
            return
        }
        if let arrVal = try? container.decode([AnyCodableValue].self) {
            self = .array(arrVal)
            return
        }
        self = .null
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let val): try container.encode(val)
        case .int(let val): try container.encode(val)
        case .double(let val): try container.encode(val)
        case .bool(let val): try container.encode(val)
        case .dictionary(let val): try container.encode(val)
        case .array(let val): try container.encode(val)
        case .null: try container.encodeNil()
        }
    }

    /// Extract a string value for a given key if this is a dictionary.
    func stringValue(forKey key: String) -> String? {
        if case .dictionary(let dict) = self, case .string(let val) = dict[key] {
            return val
        }
        return nil
    }

    /// Extract an int value for a given key if this is a dictionary.
    func intValue(forKey key: String) -> Int? {
        if case .dictionary(let dict) = self, case .int(let val) = dict[key] {
            return val
        }
        return nil
    }

    /// Pretty description of the payload for display.
    var displaySummary: String {
        switch self {
        case .dictionary(let dict):
            if let cmd = dict["cmd"] {
                if case .string(let cmdStr) = cmd {
                    return cmdStr
                }
            }
            if let type = dict["type"] {
                if case .string(let typeStr) = type {
                    return typeStr
                }
            }
            return "message"
        case .string(let val):
            return val
        default:
            return "event"
        }
    }
}
