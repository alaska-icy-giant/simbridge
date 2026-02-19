import Foundation

// MARK: - ApiClientProtocol

/// Protocol describing the REST API surface so we can substitute a mock in tests.
protocol ApiClientProtocol {
    var token: String? { get set }
    var baseURL: String { get set }

    func register(serverUrl: String, username: String, password: String) async throws -> RegisterResponse
    func login(serverUrl: String, username: String, password: String) async throws -> LoginResponse
    func googleLogin(serverUrl: String, idToken: String) async throws -> LoginResponse
    func registerDevice(name: String) async throws -> DeviceResponse
    func listDevices() async throws -> [DeviceResponse]
    func confirmPair(code: String, clientDeviceId: Int) async throws -> PairResponse
    func sendSms(_ command: SmsCommand) async throws -> RelayResponse
    func makeCall(_ command: CallCommand) async throws -> RelayResponse
    func getSims(hostDeviceId: Int) async throws -> RelayResponse
    func getHistory(deviceId: Int?, limit: Int?) async throws -> [HistoryEntry]
}

// MARK: - MockApiClient

/// Protocol-conforming stub that records all method calls and returns canned responses.
final class MockApiClient: ApiClientProtocol {
    var token: String?
    var baseURL: String = "http://localhost:8100"

    // MARK: - Call recording

    struct Call: Equatable {
        let method: String
        let args: [String]

        static func == (lhs: Call, rhs: Call) -> Bool {
            lhs.method == rhs.method && lhs.args == rhs.args
        }
    }

    private(set) var calls: [Call] = []

    // MARK: - Canned responses (set per-test)

    var registerResult: Result<RegisterResponse, Error> = .success(
        RegisterResponse(id: 1, username: "testuser")
    )
    var loginResult: Result<LoginResponse, Error> = .success(
        LoginResponse(token: "test-jwt-token", userId: 1)
    )
    var googleLoginResult: Result<LoginResponse, Error> = .success(
        LoginResponse(token: "google-jwt-token", userId: 2)
    )
    var registerDeviceResult: Result<DeviceResponse, Error> = .success(
        DeviceResponse(id: 10, name: "iPhone Client", type: "client", isOnline: false)
    )
    var listDevicesResult: Result<[DeviceResponse], Error> = .success([])
    var confirmPairResult: Result<PairResponse, Error> = .success(
        PairResponse(status: "paired", pairingId: 5, hostDeviceId: 20)
    )
    var sendSmsResult: Result<RelayResponse, Error> = .success(
        RelayResponse(status: "sent", reqId: "abc-123")
    )
    var makeCallResult: Result<RelayResponse, Error> = .success(
        RelayResponse(status: "sent", reqId: "call-123")
    )
    var getSimsResult: Result<RelayResponse, Error> = .success(
        RelayResponse(status: "sent", reqId: "sim-req-1")
    )
    var getHistoryResult: Result<[HistoryEntry], Error> = .success([])

    // MARK: - ApiClientProtocol

    func register(serverUrl: String, username: String, password: String) async throws -> RegisterResponse {
        calls.append(Call(method: "register", args: [serverUrl, username, password]))
        return try registerResult.get()
    }

    func login(serverUrl: String, username: String, password: String) async throws -> LoginResponse {
        calls.append(Call(method: "login", args: [serverUrl, username, password]))
        return try loginResult.get()
    }

    func googleLogin(serverUrl: String, idToken: String) async throws -> LoginResponse {
        calls.append(Call(method: "googleLogin", args: [serverUrl, idToken]))
        return try googleLoginResult.get()
    }

    func registerDevice(name: String) async throws -> DeviceResponse {
        calls.append(Call(method: "registerDevice", args: [name]))
        return try registerDeviceResult.get()
    }

    func listDevices() async throws -> [DeviceResponse] {
        calls.append(Call(method: "listDevices", args: []))
        return try listDevicesResult.get()
    }

    func confirmPair(code: String, clientDeviceId: Int) async throws -> PairResponse {
        calls.append(Call(method: "confirmPair", args: [code, String(clientDeviceId)]))
        return try confirmPairResult.get()
    }

    func sendSms(_ command: SmsCommand) async throws -> RelayResponse {
        calls.append(Call(method: "sendSms", args: [String(command.toDeviceId), String(command.sim), command.to, command.body]))
        return try sendSmsResult.get()
    }

    func makeCall(_ command: CallCommand) async throws -> RelayResponse {
        calls.append(Call(method: "makeCall", args: [String(command.toDeviceId), String(command.sim), command.to]))
        return try makeCallResult.get()
    }

    func getSims(hostDeviceId: Int) async throws -> RelayResponse {
        calls.append(Call(method: "getSims", args: [String(hostDeviceId)]))
        return try getSimsResult.get()
    }

    func getHistory(deviceId: Int?, limit: Int?) async throws -> [HistoryEntry] {
        var args: [String] = []
        if let d = deviceId { args.append(String(d)) }
        if let l = limit { args.append(String(l)) }
        calls.append(Call(method: "getHistory", args: args))
        return try getHistoryResult.get()
    }

    // MARK: - Helpers

    func reset() {
        calls = []
    }
}
