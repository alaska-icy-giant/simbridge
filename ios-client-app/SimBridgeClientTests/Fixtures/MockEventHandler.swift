import Foundation

// MARK: - EventHandlerProtocol

/// Protocol for the event handler so we can substitute a mock.
protocol EventHandlerProtocol {
    func handle(_ message: WsMessage)
}

// MARK: - MockEventHandler

/// Records all handled events for assertion in tests.
final class MockEventHandler: EventHandlerProtocol {

    struct HandledEvent {
        let type: String
        let message: WsMessage
    }

    private(set) var handledEvents: [HandledEvent] = []
    private(set) var notificationRequests: [String] = []
    private(set) var alertMessages: [String] = []

    // State that a real EventHandler would update
    var lastSmsStatus: String?
    var lastCallState: String?
    var simList: [[String: Any]] = []
    var errorMessage: String?

    func handle(_ message: WsMessage) {
        let eventType = message.type ?? "unknown"
        handledEvents.append(HandledEvent(type: eventType, message: message))

        switch eventType {
        case "INCOMING_SMS":
            let body = message.body ?? "New message"
            notificationRequests.append(body)
        case "SMS_SENT":
            lastSmsStatus = message.status ?? "sent"
        case "CALL_STATE":
            lastCallState = message.state
        case "SIM_INFO":
            // Store raw sims data
            break
        case "ERROR":
            let msg = message.message ?? "Unknown error"
            errorMessage = msg
            alertMessages.append(msg)
        default:
            // Unknown event — do nothing
            break
        }
    }

    func reset() {
        handledEvents = []
        notificationRequests = []
        alertMessages = []
        lastSmsStatus = nil
        lastCallState = nil
        simList = []
        errorMessage = nil
    }
}
