// ApiClient.swift
// Full async/await REST client for SimBridge relay server.

import Foundation

/// Errors from the API client.
enum ApiError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(Int, String)
    case decodingError(String)
    case noToken
    case noServerUrl

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid server URL"
        case .invalidResponse:
            return "Invalid response from server"
        case .httpError(let code, let message):
            return "Server error (\(code)): \(message)"
        case .decodingError(let detail):
            return "Failed to parse response: \(detail)"
        case .noToken:
            return "Not authenticated"
        case .noServerUrl:
            return "Server URL not configured"
        }
    }
}

/// HTTP error detail returned by FastAPI.
private struct ErrorDetail: Codable {
    let detail: String?
}

final class ApiClient {

    static let shared = ApiClient()

    private let session: URLSession
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)

        self.encoder = JSONEncoder()
        self.decoder = JSONDecoder()
    }

    // MARK: - Helpers

    private func baseURL() throws -> String {
        let url = Prefs.serverUrl
        guard !url.isEmpty else { throw ApiError.noServerUrl }
        // Strip trailing slash
        return url.hasSuffix("/") ? String(url.dropLast()) : url
    }

    private func authToken() throws -> String {
        let token = Prefs.token
        guard !token.isEmpty else { throw ApiError.noToken }
        return token
    }

    private func buildRequest(
        path: String,
        method: String,
        body: (any Encodable)? = nil,
        serverUrl: String? = nil,
        token: String? = nil
    ) throws -> URLRequest {
        let base = try serverUrl ?? baseURL()
        guard let url = URL(string: "\(base)\(path)") else {
            throw ApiError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        if let token = token ?? (try? authToken()) {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = body {
            request.httpBody = try encoder.encode(body)
        }

        return request
    }

    private func perform<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let message: String
            if let errorDetail = try? decoder.decode(ErrorDetail.self, from: data) {
                message = errorDetail.detail ?? "Unknown error"
            } else {
                message = String(data: data, encoding: .utf8) ?? "Unknown error"
            }
            throw ApiError.httpError(httpResponse.statusCode, message)
        }

        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw ApiError.decodingError(error.localizedDescription)
        }
    }

    // MARK: - Auth

    func register(serverUrl: String, username: String, password: String) async throws -> RegisterResponse {
        let body = LoginRequest(username: username, password: password)
        let request = try buildRequest(
            path: "/auth/register",
            method: "POST",
            body: body,
            serverUrl: serverUrl
        )
        return try await perform(request)
    }

    func login(serverUrl: String, username: String, password: String) async throws -> LoginResponse {
        let body = LoginRequest(username: username, password: password)
        let request = try buildRequest(
            path: "/auth/login",
            method: "POST",
            body: body,
            serverUrl: serverUrl
        )
        return try await perform(request)
    }

    // MARK: - Devices

    func registerDevice(name: String) async throws -> DeviceResponse {
        let body = DeviceCreate(name: name, type: "client")
        let request = try buildRequest(
            path: "/devices",
            method: "POST",
            body: body
        )
        return try await perform(request)
    }

    func listDevices() async throws -> [DeviceResponse] {
        let request = try buildRequest(
            path: "/devices",
            method: "GET"
        )
        return try await perform(request)
    }

    // MARK: - Pairing

    func confirmPair(code: String, clientDeviceId: Int) async throws -> PairResponse {
        let body = PairConfirm(code: code, clientDeviceId: clientDeviceId)
        let request = try buildRequest(
            path: "/pair/confirm",
            method: "POST",
            body: body
        )
        return try await perform(request)
    }

    // MARK: - SMS

    func sendSms(_ command: SmsCommand) async throws -> RelayResponse {
        let request = try buildRequest(
            path: "/sms",
            method: "POST",
            body: command
        )
        return try await perform(request)
    }

    // MARK: - Call

    func makeCall(_ command: CallCommand) async throws -> RelayResponse {
        let request = try buildRequest(
            path: "/call",
            method: "POST",
            body: command
        )
        return try await perform(request)
    }

    // MARK: - SIMs

    func getSims(hostDeviceId: Int) async throws -> RelayResponse {
        let request = try buildRequest(
            path: "/sims?host_device_id=\(hostDeviceId)",
            method: "GET"
        )
        return try await perform(request)
    }

    // MARK: - History

    func getHistory(deviceId: Int? = nil, limit: Int = 50) async throws -> [HistoryEntry] {
        var path = "/history?limit=\(limit)"
        if let deviceId = deviceId {
            path += "&device_id=\(deviceId)"
        }
        let request = try buildRequest(
            path: path,
            method: "GET"
        )
        return try await perform(request)
    }
}
