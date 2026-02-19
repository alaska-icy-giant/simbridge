import XCTest

// MARK: - ApiClient under test

/// Minimal ApiClient implementation that mirrors the production code.
/// In a real Xcode project you would use `@testable import SimBridgeClient` instead.
final class ApiClient: ApiClientProtocol {

    var token: String?
    var baseURL: String = "http://localhost:8100"

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    // MARK: - Private helpers

    private func makeRequest(
        method: String,
        path: String,
        body: Data? = nil,
        queryItems: [URLQueryItem]? = nil,
        authenticated: Bool = true
    ) -> URLRequest {
        var components = URLComponents(string: baseURL + path)!
        components.queryItems = queryItems
        var request = URLRequest(url: components.url!)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if authenticated, let token = token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = body
        return request
    }

    private func perform<T: Decodable>(_ request: URLRequest) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw ApiError.networkError(underlying: error)
        }
        guard let http = response as? HTTPURLResponse else {
            throw ApiError.networkError(underlying: NSError(domain: "", code: -1))
        }
        guard (200...299).contains(http.statusCode) else {
            let detail: String
            if let body = try? JSONDecoder().decode([String: String].self, from: data),
               let d = body["detail"] {
                detail = d
            } else {
                detail = String(data: data, encoding: .utf8) ?? "Unknown error"
            }
            throw ApiError.httpError(statusCode: http.statusCode, detail: detail)
        }
        do {
            let decoder = JSONDecoder()
            return try decoder.decode(T.self, from: data)
        } catch {
            throw ApiError.decodingError(underlying: error)
        }
    }

    private func encode<T: Encodable>(_ value: T) throws -> Data {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return try encoder.encode(value)
    }

    // MARK: - Auth

    func register(serverUrl: String, username: String, password: String) async throws -> RegisterResponse {
        baseURL = serverUrl
        let body = try JSONSerialization.data(withJSONObject: [
            "username": username, "password": password
        ])
        let request = makeRequest(method: "POST", path: "/auth/register", body: body, authenticated: false)
        return try await perform(request)
    }

    func login(serverUrl: String, username: String, password: String) async throws -> LoginResponse {
        baseURL = serverUrl
        let body = try JSONSerialization.data(withJSONObject: [
            "username": username, "password": password
        ])
        let request = makeRequest(method: "POST", path: "/auth/login", body: body, authenticated: false)
        return try await perform(request)
    }

    // MARK: - Devices

    func registerDevice(name: String) async throws -> DeviceResponse {
        let body = try JSONSerialization.data(withJSONObject: [
            "name": name, "type": "client"
        ])
        let request = makeRequest(method: "POST", path: "/devices", body: body)
        return try await perform(request)
    }

    func listDevices() async throws -> [DeviceResponse] {
        let request = makeRequest(method: "GET", path: "/devices")
        return try await perform(request)
    }

    // MARK: - Pairing

    func confirmPair(code: String, clientDeviceId: Int) async throws -> PairResponse {
        let body = try JSONSerialization.data(withJSONObject: [
            "code": code, "client_device_id": clientDeviceId
        ] as [String : Any])
        let request = makeRequest(method: "POST", path: "/pair/confirm", body: body)
        return try await perform(request)
    }

    // MARK: - Commands

    func sendSms(_ command: SmsCommand) async throws -> RelayResponse {
        let body = try encode(command)
        let request = makeRequest(method: "POST", path: "/sms", body: body)
        return try await perform(request)
    }

    func makeCall(_ command: CallCommand) async throws -> RelayResponse {
        let body = try encode(command)
        let request = makeRequest(method: "POST", path: "/call", body: body)
        return try await perform(request)
    }

    func getSims(hostDeviceId: Int) async throws -> RelayResponse {
        let request = makeRequest(
            method: "GET",
            path: "/sims",
            queryItems: [URLQueryItem(name: "host_device_id", value: String(hostDeviceId))]
        )
        return try await perform(request)
    }

    // MARK: - History

    func getHistory(deviceId: Int? = nil, limit: Int? = nil) async throws -> [HistoryEntry] {
        var items: [URLQueryItem] = []
        if let d = deviceId { items.append(URLQueryItem(name: "device_id", value: String(d))) }
        if let l = limit { items.append(URLQueryItem(name: "limit", value: String(l))) }
        let request = makeRequest(method: "GET", path: "/history", queryItems: items.isEmpty ? nil : items)
        return try await perform(request)
    }
}

// MARK: - Tests

final class ApiClientTests: XCTestCase {

    var sut: ApiClient!

    override func setUp() {
        super.setUp()
        MockURLProtocol.reset()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: config)
        sut = ApiClient(session: session)
        sut.baseURL = "http://localhost:8100"
        sut.token = "test-jwt-token"
    }

    override func tearDown() {
        MockURLProtocol.reset()
        sut = nil
        super.tearDown()
    }

    // MARK: - Login

    func test_login_sends_correct_body() async throws {
        MockURLProtocol.requestHandler = { request in
            let url = request.url!
            XCTAssertEqual(url.path, "/auth/login")
            XCTAssertEqual(request.httpMethod, "POST")

            let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: String]
            XCTAssertEqual(body["username"], "alice")
            XCTAssertEqual(body["password"], "secret123")

            return (
                SampleData.httpResponse(url: url, statusCode: 200),
                SampleData.data(SampleData.loginResponseJSON)
            )
        }

        _ = try await sut.login(serverUrl: "http://localhost:8100", username: "alice", password: "secret123")

        XCTAssertEqual(MockURLProtocol.capturedRequests.count, 1)
    }

    func test_login_parses_token() async throws {
        MockURLProtocol.requestHandler = { request in
            (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.loginResponseJSON)
            )
        }

        let response = try await sut.login(serverUrl: "http://localhost:8100", username: "alice", password: "secret123")

        XCTAssertEqual(response.token, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxfQ.test")
        XCTAssertEqual(response.userId, 1)
    }

    // MARK: - Register

    func test_register_sends_correct_body() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/auth/register")
            XCTAssertEqual(request.httpMethod, "POST")

            let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: String]
            XCTAssertEqual(body["username"], "newuser")
            XCTAssertEqual(body["password"], "pass456")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.registerResponseJSON)
            )
        }

        let response = try await sut.register(serverUrl: "http://localhost:8100", username: "newuser", password: "pass456")

        XCTAssertEqual(response.id, 1)
        XCTAssertEqual(response.username, "testuser")
    }

    // MARK: - Device registration

    func test_register_device_as_client() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/devices")
            XCTAssertEqual(request.httpMethod, "POST")

            let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: String]
            XCTAssertEqual(body["type"], "client")
            XCTAssertEqual(body["name"], "My iPhone")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.deviceResponseJSON)
            )
        }

        let response = try await sut.registerDevice(name: "My iPhone")

        XCTAssertEqual(response.id, 10)
        XCTAssertEqual(response.type, "client")
    }

    // MARK: - Pair confirm

    func test_confirm_pair_sends_code_and_device_id() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/pair/confirm")
            XCTAssertEqual(request.httpMethod, "POST")

            let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
            XCTAssertEqual(body["code"] as? String, "123456")
            XCTAssertEqual(body["client_device_id"] as? Int, 10)

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.pairConfirmResponseJSON)
            )
        }

        let response = try await sut.confirmPair(code: "123456", clientDeviceId: 10)

        XCTAssertEqual(response.status, "paired")
        XCTAssertEqual(response.hostDeviceId, 20)
    }

    // MARK: - SMS

    func test_send_sms_correct_payload() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/sms")
            XCTAssertEqual(request.httpMethod, "POST")

            let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
            XCTAssertEqual(body["to_device_id"] as? Int, 20)
            XCTAssertEqual(body["sim"] as? Int, 1)
            XCTAssertEqual(body["to"] as? String, "+1234567890")
            XCTAssertEqual(body["body"] as? String, "Hello World")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.relaySentResponseJSON)
            )
        }

        let command = SmsCommand(toDeviceId: 20, sim: 1, to: "+1234567890", body: "Hello World")
        let response = try await sut.sendSms(command)

        XCTAssertEqual(response.status, "sent")
        XCTAssertEqual(response.reqId, "abc-123")
    }

    // MARK: - Call

    func test_make_call_correct_payload() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/call")
            XCTAssertEqual(request.httpMethod, "POST")

            let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
            XCTAssertEqual(body["to_device_id"] as? Int, 20)
            XCTAssertEqual(body["sim"] as? Int, 2)
            XCTAssertEqual(body["to"] as? String, "+9876543210")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.relaySentResponseJSON)
            )
        }

        let command = CallCommand(toDeviceId: 20, sim: 2, to: "+9876543210")
        let response = try await sut.makeCall(command)

        XCTAssertEqual(response.status, "sent")
    }

    // MARK: - SIMs

    func test_get_sims_query_param() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/sims")
            XCTAssertEqual(request.httpMethod, "GET")

            let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)!
            let hostDeviceId = components.queryItems?.first(where: { $0.name == "host_device_id" })?.value
            XCTAssertEqual(hostDeviceId, "20")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.simsResponseJSON)
            )
        }

        _ = try await sut.getSims(hostDeviceId: 20)

        XCTAssertEqual(MockURLProtocol.capturedRequests.count, 1)
    }

    // MARK: - History

    func test_get_history_default_limit() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url!.path, "/history")
            XCTAssertEqual(request.httpMethod, "GET")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.historyResponseJSON)
            )
        }

        let entries = try await sut.getHistory()

        XCTAssertEqual(entries.count, 2)
        XCTAssertEqual(entries[0].id, 1)
        XCTAssertEqual(entries[0].fromDeviceId, 10)
        XCTAssertEqual(entries[0].msgType, "command")
    }

    func test_get_history_with_device_filter() async throws {
        MockURLProtocol.requestHandler = { request in
            let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)!
            let deviceId = components.queryItems?.first(where: { $0.name == "device_id" })?.value
            XCTAssertEqual(deviceId, "10")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.historyResponseJSON)
            )
        }

        _ = try await sut.getHistory(deviceId: 10)
    }

    // MARK: - Auth header

    func test_auth_header_set() async throws {
        sut.token = "my-secret-jwt"

        MockURLProtocol.requestHandler = { request in
            let auth = request.value(forHTTPHeaderField: "Authorization")
            XCTAssertEqual(auth, "Bearer my-secret-jwt")

            return (
                SampleData.httpResponse(url: request.url!, statusCode: 200),
                SampleData.data(SampleData.devicesListJSON)
            )
        }

        _ = try await sut.listDevices()
    }

    // MARK: - Error handling

    func test_http_error_throws() async throws {
        MockURLProtocol.requestHandler = { request in
            (
                SampleData.httpResponse(url: request.url!, statusCode: 401),
                SampleData.data(SampleData.error401JSON)
            )
        }

        do {
            _ = try await sut.login(serverUrl: "http://localhost:8100", username: "bad", password: "bad")
            XCTFail("Expected error to be thrown")
        } catch let error as ApiError {
            if case .httpError(let code, let detail) = error {
                XCTAssertEqual(code, 401)
                XCTAssertEqual(detail, "Invalid credentials")
            } else {
                XCTFail("Expected httpError, got \(error)")
            }
        }
    }

    func test_network_error_throws() async throws {
        MockURLProtocol.requestHandler = { _ in
            throw NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet, userInfo: nil)
        }

        do {
            _ = try await sut.login(serverUrl: "http://localhost:8100", username: "a", password: "b")
            XCTFail("Expected network error")
        } catch let error as ApiError {
            if case .networkError = error {
                // Pass
            } else {
                XCTFail("Expected networkError, got \(error)")
            }
        }
    }
}
