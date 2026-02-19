import Foundation

// MARK: - PrefsProtocol

/// Protocol for user preferences so we can substitute an in-memory implementation.
protocol PrefsProtocol {
    var serverUrl: String? { get set }
    var token: String? { get set }
    var deviceId: Int? { get set }
    var deviceName: String? { get set }
    var pairedHostId: Int? { get set }
    var pairedHostName: String? { get set }

    var isLoggedIn: Bool { get }
    func clear()
}

// MARK: - StubPrefs

/// In-memory prefs implementation using a dedicated UserDefaults suite for test isolation.
/// Each instance uses a unique suite name to prevent cross-test contamination.
final class StubPrefs: PrefsProtocol {

    private let defaults: UserDefaults
    private let suiteName: String

    private enum Keys {
        static let serverUrl = "serverUrl"
        static let token = "token"
        static let deviceId = "deviceId"
        static let deviceName = "deviceName"
        static let pairedHostId = "pairedHostId"
        static let pairedHostName = "pairedHostName"
    }

    init(suiteName: String = "com.simbridge.tests.\(UUID().uuidString)") {
        self.suiteName = suiteName
        self.defaults = UserDefaults(suiteName: suiteName)!
    }

    deinit {
        defaults.removePersistentDomain(forName: suiteName)
    }

    var serverUrl: String? {
        get { defaults.string(forKey: Keys.serverUrl) }
        set { defaults.set(newValue, forKey: Keys.serverUrl) }
    }

    var token: String? {
        get { defaults.string(forKey: Keys.token) }
        set { defaults.set(newValue, forKey: Keys.token) }
    }

    var deviceId: Int? {
        get {
            let val = defaults.integer(forKey: Keys.deviceId)
            return val == 0 && defaults.object(forKey: Keys.deviceId) == nil ? nil : val
        }
        set {
            if let newValue = newValue {
                defaults.set(newValue, forKey: Keys.deviceId)
            } else {
                defaults.removeObject(forKey: Keys.deviceId)
            }
        }
    }

    var deviceName: String? {
        get { defaults.string(forKey: Keys.deviceName) }
        set { defaults.set(newValue, forKey: Keys.deviceName) }
    }

    var pairedHostId: Int? {
        get {
            let val = defaults.integer(forKey: Keys.pairedHostId)
            return val == 0 && defaults.object(forKey: Keys.pairedHostId) == nil ? nil : val
        }
        set {
            if let newValue = newValue {
                defaults.set(newValue, forKey: Keys.pairedHostId)
            } else {
                defaults.removeObject(forKey: Keys.pairedHostId)
            }
        }
    }

    var pairedHostName: String? {
        get { defaults.string(forKey: Keys.pairedHostName) }
        set { defaults.set(newValue, forKey: Keys.pairedHostName) }
    }

    var isLoggedIn: Bool {
        guard let t = token else { return false }
        return !t.isEmpty
    }

    func clear() {
        defaults.removeObject(forKey: Keys.serverUrl)
        defaults.removeObject(forKey: Keys.token)
        defaults.removeObject(forKey: Keys.deviceId)
        defaults.removeObject(forKey: Keys.deviceName)
        defaults.removeObject(forKey: Keys.pairedHostId)
        defaults.removeObject(forKey: Keys.pairedHostName)
    }
}
