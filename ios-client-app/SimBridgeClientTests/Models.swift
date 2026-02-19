// Models.swift
// Shared Codable models used by the app and referenced by tests.
// In a real Xcode project this file lives in SimBridgeClient/Data/Models.swift
// and is imported by the test target via @testable import SimBridgeClient.
// We duplicate the definitions here so the test files compile stand-alone.

import Foundation

// MARK: - Connection Status

enum ConnectionStatus: String, Codable {
    case connected
    case connecting
    case disconnected
}

// MARK: - Auth responses

struct LoginResponse: Codable, Equatable {
    let token: String
    let userId: Int

    enum CodingKeys: String, CodingKey {
        case token
        case userId = "user_id"
    }
}

struct RegisterResponse: Codable, Equatable {
    let id: Int
    let username: String
}

// MARK: - Device

struct DeviceResponse: Codable, Equatable {
    let id: Int
    let name: String
    let type: String
    let isOnline: Bool

    enum CodingKeys: String, CodingKey {
        case id, name, type
        case isOnline = "is_online"
    }
}

// MARK: - Pairing

struct PairConfirm: Codable, Equatable {
    let code: String
    let clientDeviceId: Int

    enum CodingKeys: String, CodingKey {
        case code
        case clientDeviceId = "client_device_id"
    }
}

struct PairResponse: Codable, Equatable {
    let status: String
    let pairingId: Int
    let hostDeviceId: Int?

    enum CodingKeys: String, CodingKey {
        case status
        case pairingId = "pairing_id"
        case hostDeviceId = "host_device_id"
    }
}

// MARK: - Commands

struct SmsCommand: Codable, Equatable {
    let toDeviceId: Int
    let sim: Int
    let to: String
    let body: String

    enum CodingKeys: String, CodingKey {
        case toDeviceId = "to_device_id"
        case sim, to, body
    }
}

struct CallCommand: Codable, Equatable {
    let toDeviceId: Int
    let sim: Int
    let to: String

    enum CodingKeys: String, CodingKey {
        case toDeviceId = "to_device_id"
        case sim, to
    }
}

// MARK: - Relay response

struct RelayResponse: Codable, Equatable {
    let status: String
    let reqId: String?

    enum CodingKeys: String, CodingKey {
        case status
        case reqId = "req_id"
    }
}

// MARK: - History

struct HistoryEntry: Codable, Equatable {
    let id: Int
    let fromDeviceId: Int
    let toDeviceId: Int
    let msgType: String
    let payload: [String: AnyCodableValue]
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

// MARK: - WebSocket message

struct WsMessage: Codable {
    let type: String?
    let from: String?
    let body: String?
    let sim: Int?
    let status: String?
    let state: String?
    let to: String?
    let message: String?
    let reqId: String?
    let deviceId: Int?
    let sims: [SimInfo]?
    let fromDeviceId: Int?
    let data: String?

    enum CodingKeys: String, CodingKey {
        case type, from, body, sim, status, state, to, message, sims, data
        case reqId = "req_id"
        case deviceId = "device_id"
        case fromDeviceId = "from_device_id"
    }
}

struct SimInfo: Codable, Equatable {
    let slot: Int
    let carrier: String
    let number: String
}

// MARK: - AnyCodableValue

/// A type-erased Codable wrapper to handle arbitrary JSON values in payloads.
enum AnyCodableValue: Codable, Equatable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let v = try? container.decode(String.self) { self = .string(v); return }
        if let v = try? container.decode(Int.self) { self = .int(v); return }
        if let v = try? container.decode(Double.self) { self = .double(v); return }
        if let v = try? container.decode(Bool.self) { self = .bool(v); return }
        if container.decodeNil() { self = .null; return }
        throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported value")
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let v): try container.encode(v)
        case .int(let v): try container.encode(v)
        case .double(let v): try container.encode(v)
        case .bool(let v): try container.encode(v)
        case .null: try container.encodeNil()
        }
    }
}

// MARK: - API Errors

enum ApiError: Error, LocalizedError {
    case httpError(statusCode: Int, detail: String)
    case networkError(underlying: Error)
    case decodingError(underlying: Error)

    var errorDescription: String? {
        switch self {
        case .httpError(let code, let detail):
            return "HTTP \(code): \(detail)"
        case .networkError(let err):
            return "Network error: \(err.localizedDescription)"
        case .decodingError(let err):
            return "Decoding error: \(err.localizedDescription)"
        }
    }
}
