// Prefs.swift
// UserDefaults wrapper for persistent app preferences.

import Foundation

enum Prefs {

    private static let defaults = UserDefaults.standard

    // MARK: - Keys

    private enum Key: String {
        case serverUrl
        case token
        case deviceId
        case deviceName
        case pairedHostId
        case pairedHostName
        case isLoggedIn
        case isPaired
    }

    // MARK: - Server

    static var serverUrl: String {
        get { defaults.string(forKey: Key.serverUrl.rawValue) ?? "" }
        set { defaults.set(newValue, forKey: Key.serverUrl.rawValue) }
    }

    // MARK: - Auth

    static var token: String {
        get { defaults.string(forKey: Key.token.rawValue) ?? "" }
        set { defaults.set(newValue, forKey: Key.token.rawValue) }
    }

    static var isLoggedIn: Bool {
        get { defaults.bool(forKey: Key.isLoggedIn.rawValue) }
        set { defaults.set(newValue, forKey: Key.isLoggedIn.rawValue) }
    }

    // MARK: - Device

    static var deviceId: Int {
        get { defaults.integer(forKey: Key.deviceId.rawValue) }
        set { defaults.set(newValue, forKey: Key.deviceId.rawValue) }
    }

    static var deviceName: String {
        get { defaults.string(forKey: Key.deviceName.rawValue) ?? "" }
        set { defaults.set(newValue, forKey: Key.deviceName.rawValue) }
    }

    // MARK: - Pairing

    static var pairedHostId: Int {
        get { defaults.integer(forKey: Key.pairedHostId.rawValue) }
        set { defaults.set(newValue, forKey: Key.pairedHostId.rawValue) }
    }

    static var pairedHostName: String {
        get { defaults.string(forKey: Key.pairedHostName.rawValue) ?? "" }
        set { defaults.set(newValue, forKey: Key.pairedHostName.rawValue) }
    }

    static var isPaired: Bool {
        get { defaults.bool(forKey: Key.isPaired.rawValue) }
        set { defaults.set(newValue, forKey: Key.isPaired.rawValue) }
    }

    // MARK: - Clear All

    static func clear() {
        for key in Key.allCases {
            defaults.removeObject(forKey: key.rawValue)
        }
    }
}

// MARK: - CaseIterable conformance for Key

extension Prefs.Key: CaseIterable {}
