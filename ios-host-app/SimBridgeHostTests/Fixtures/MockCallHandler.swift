import Foundation
@testable import SimBridgeHost

// MARK: - CallHandlerProtocol

/// Protocol extracted from CallHandler for testability.
protocol CallHandlerProtocol {
    func makeCall(to number: String, sim: Int?, reqId: String?)
    func hangUp(reqId: String?)
}

// MARK: - MockCallHandler

/// Recording mock for CallHandler. Captures all call actions for assertion.
final class MockCallHandler: CallHandlerProtocol {

    struct MakeCallRecord {
        let to: String
        let sim: Int?
        let reqId: String?
    }

    struct HangUpRecord {
        let reqId: String?
    }

    private(set) var makeCallRecords: [MakeCallRecord] = []
    private(set) var hangUpRecords: [HangUpRecord] = []

    var makeCallError: Error?
    var hangUpError: Error?

    func makeCall(to number: String, sim: Int?, reqId: String?) {
        makeCallRecords.append(MakeCallRecord(to: number, sim: sim, reqId: reqId))
        if let error = makeCallError {
            // In real implementation this would send an error event
            _ = error
        }
    }

    func hangUp(reqId: String?) {
        hangUpRecords.append(HangUpRecord(reqId: reqId))
        if let error = hangUpError {
            _ = error
        }
    }

    func reset() {
        makeCallRecords.removeAll()
        hangUpRecords.removeAll()
        makeCallError = nil
        hangUpError = nil
    }
}
