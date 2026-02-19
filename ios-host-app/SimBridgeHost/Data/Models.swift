// Models.swift
// SimBridgeHost
//
// Codable structs matching Android Models.kt.
// Uses CodingKeys where JSON key differs from Swift property name.

import Foundation

// MARK: - WebSocket Messages

struct WsMessage: Codable {
    let type: String                    // "command", "event", "webrtc"
    var cmd: String?                    // for type="command"
    var event: String?                  // for type="event"
    var action: String?                 // for type="webrtc"
    var sim: Int?
    var to: String?
    var from: String?
    var body: String?
    var status: String?
    var state: String?
    var sims: [SimInfo]?
    var sdp: String?
    var candidate: String?
    var sdpMid: String?
    var sdpMLineIndex: Int?
    var reqId: String?
    var fromDeviceId: Int?

    enum CodingKeys: String, CodingKey {
        case type, cmd, event, action, sim, to, from, body, status, state
        case sims, sdp, candidate
        case sdpMid
        case sdpMLineIndex
        case reqId = "req_id"
        case fromDeviceId = "from_device_id"
    }
}

struct SimInfo: Codable, Identifiable {
    let slot: Int
    let carrier: String
    let number: String?

    var id: Int { slot }
}

// MARK: - REST API

struct LoginRequest: Codable {
    let username: String
    let password: String
}

struct LoginResponse: Codable {
    let token: String
}

struct DeviceRegisterRequest: Codable {
    let name: String
    let type: String

    init(name: String, type: String = "host") {
        self.name = name
        self.type = type
    }
}

struct DeviceResponse: Codable {
    let id: Int
    let name: String
    let type: String
    let pairedWith: Int?

    enum CodingKeys: String, CodingKey {
        case id, name, type
        case pairedWith = "paired_with"
    }
}

struct PairRequest: Codable {
    let code: String
}

struct PairResponse: Codable {
    let status: String
    let pairedDeviceId: Int?

    enum CodingKeys: String, CodingKey {
        case status
        case pairedDeviceId = "paired_device_id"
    }
}

// MARK: - UI State

enum ConnectionStatus: String {
    case disconnected
    case connecting
    case connected
}

struct LogEntry: Identifiable {
    let id = UUID()
    let timestamp: Date
    let direction: String   // "IN" or "OUT"
    let summary: String

    init(direction: String, summary: String, timestamp: Date = Date()) {
        self.timestamp = timestamp
        self.direction = direction
        self.summary = summary
    }
}
