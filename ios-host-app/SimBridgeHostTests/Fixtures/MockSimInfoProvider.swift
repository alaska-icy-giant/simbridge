import Foundation
@testable import SimBridgeHost

// MARK: - SimInfoProviderProtocol

/// Protocol extracted from SimInfoProvider for testability.
protocol SimInfoProviderProtocol {
    func getActiveSimCards() -> [SimInfo]
}

// MARK: - MockSimInfoProvider

/// Recording mock for SimInfoProvider. Returns canned SIM data for tests.
final class MockSimInfoProvider: SimInfoProviderProtocol {

    /// Canned SIM data to return from getActiveSimCards().
    var activeSims: [SimInfo] = []

    /// If set, getActiveSimCards() will throw this error instead of returning data.
    var shouldThrowSecurityError = false

    private(set) var getActiveSimCardsCallCount = 0

    func getActiveSimCards() -> [SimInfo] {
        getActiveSimCardsCallCount += 1
        if shouldThrowSecurityError {
            // In the real implementation, SecurityException returns empty list
            return []
        }
        return activeSims
    }

    func reset() {
        activeSims.removeAll()
        shouldThrowSecurityError = false
        getActiveSimCardsCallCount = 0
    }
}
