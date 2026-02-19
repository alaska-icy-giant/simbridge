// Prefs.swift
// SimBridgeHost
//
// UserDefaults wrapper matching Android Prefs.kt.
// Provides computed properties for persistent app state.

import Foundation

final class Prefs: ObservableObject {

    private let defaults: UserDefaults

    private enum Keys {
        static let serverUrl = "server_url"
        static let token = "token"
        static let deviceId = "device_id"
        static let deviceName = "device_name"
        static let biometricEnabled = "biometric_enabled"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var serverUrl: String {
        get { defaults.string(forKey: Keys.serverUrl) ?? "" }
        set {
            let trimmed = newValue.hasSuffix("/") ? String(newValue.dropLast()) : newValue
            defaults.set(trimmed, forKey: Keys.serverUrl)
            objectWillChange.send()
        }
    }

    var token: String {
        get { defaults.string(forKey: Keys.token) ?? "" }
        set {
            defaults.set(newValue, forKey: Keys.token)
            objectWillChange.send()
        }
    }

    var deviceId: Int {
        get {
            let value = defaults.integer(forKey: Keys.deviceId)
            // UserDefaults returns 0 for missing keys; use -1 as sentinel
            return defaults.object(forKey: Keys.deviceId) != nil ? value : -1
        }
        set {
            defaults.set(newValue, forKey: Keys.deviceId)
            objectWillChange.send()
        }
    }

    var deviceName: String {
        get { defaults.string(forKey: Keys.deviceName) ?? "" }
        set {
            defaults.set(newValue, forKey: Keys.deviceName)
            objectWillChange.send()
        }
    }

    var biometricEnabled: Bool {
        get { defaults.bool(forKey: Keys.biometricEnabled) }
        set {
            defaults.set(newValue, forKey: Keys.biometricEnabled)
            objectWillChange.send()
        }
    }

    var isLoggedIn: Bool {
        !token.isEmpty && !serverUrl.isEmpty
    }

    func clear() {
        defaults.removeObject(forKey: Keys.serverUrl)
        defaults.removeObject(forKey: Keys.token)
        defaults.removeObject(forKey: Keys.deviceId)
        defaults.removeObject(forKey: Keys.deviceName)
        defaults.removeObject(forKey: Keys.biometricEnabled)
        objectWillChange.send()
    }
}
