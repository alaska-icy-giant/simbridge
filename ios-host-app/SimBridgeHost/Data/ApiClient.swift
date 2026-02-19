// ApiClient.swift
// SimBridgeHost
//
// URLSession async/await REST client matching Android ApiClient.kt.
// All network calls use structured concurrency with a 15-second timeout.

import Foundation

final class ApiClient: Sendable {

    private let prefs: Prefs
    private let session: URLSession

    init(prefs: Prefs) {
        self.prefs = prefs
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 15
        self.session = URLSession(configuration: config)
    }

    // MARK: - Public API

    /// POST /auth/login -- returns JWT token.
    func login(serverUrl: String, username: String, password: String) async throws -> LoginResponse {
        let url = URL(string: "\(serverUrl.trimmingSuffix("/"))/auth/login")!
        let body = LoginRequest(username: username, password: password)
        return try await post(url: url, body: body, token: nil)
    }

    /// POST /devices -- register this device as a host.
    func registerDevice(name: String) async throws -> DeviceResponse {
        let baseUrl = prefs.serverUrl
        let url = URL(string: "\(baseUrl)/devices")!
        let body = DeviceRegisterRequest(name: name, type: "host")
        return try await post(url: url, body: body, token: prefs.token)
    }

    /// POST /pair -- pair with a client device using a pairing code.
    func pair(code: String) async throws -> PairResponse {
        let baseUrl = prefs.serverUrl
        let url = URL(string: "\(baseUrl)/pair")!
        let body = PairRequest(code: code)
        return try await post(url: url, body: body, token: prefs.token)
    }

    // MARK: - Private

    private func post<RequestBody: Encodable, ResponseBody: Decodable>(
        url: URL,
        body: RequestBody,
        token: String?
    ) async throws -> ResponseBody {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")

        if let token = token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(body)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let responseBody = String(data: data, encoding: .utf8) ?? "no body"
            throw ApiError.httpError(code: httpResponse.statusCode, message: responseBody)
        }

        let decoder = JSONDecoder()
        return try decoder.decode(ResponseBody.self, from: data)
    }
}

// MARK: - Error Types

enum ApiError: LocalizedError {
    case invalidResponse
    case httpError(code: Int, message: String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid server response"
        case .httpError(let code, let message):
            return "HTTP \(code): \(message)"
        }
    }
}

// MARK: - String Extension

private extension String {
    func trimmingSuffix(_ suffix: String) -> String {
        if hasSuffix(suffix) {
            return String(dropLast(suffix.count))
        }
        return self
    }
}
