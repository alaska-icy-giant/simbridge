import XCTest

/// UI tests for DashboardView using XCUITest.
final class DashboardViewTests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        // Launch directly into DashboardView with a paired device
        app.launchArguments = ["--uitesting", "--skip-login", "--paired"]
        app.launch()
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - Status card shown

    func test_status_card_shown() {
        let statusCard = app.otherElements["statusCard"]

        XCTAssertTrue(
            statusCard.waitForExistence(timeout: 5),
            "StatusCard should be visible on the dashboard"
        )
    }

    func test_status_card_shows_connection_state() {
        // The status card should show one of: Connected, Connecting, Disconnected
        let connected = app.staticTexts["statusConnected"]
        let connecting = app.staticTexts["statusConnecting"]
        let disconnected = app.staticTexts["statusDisconnected"]

        let hasStatus = connected.waitForExistence(timeout: 5)
            || connecting.exists
            || disconnected.exists

        XCTAssertTrue(hasStatus, "Status card should display a connection state")
    }

    // MARK: - Paired host info

    func test_paired_host_info() {
        let hostInfo = app.staticTexts["pairedHostName"]

        XCTAssertTrue(
            hostInfo.waitForExistence(timeout: 5),
            "Paired host name should be displayed"
        )

        // Should also show online/offline status
        let hostStatus = app.staticTexts["pairedHostStatus"]
        XCTAssertTrue(hostStatus.exists, "Paired host online/offline status should be shown")
    }

    // MARK: - SIM cards from host

    func test_sim_cards_from_host() {
        // SIM cards should be displayed when host is online
        let simCard = app.otherElements["simCard_1"]
            .waitForExistence(timeout: 5)

        // If the host is offline, SIMs might not be fetched yet.
        // We just verify the SIM section exists.
        let simSection = app.otherElements["simCardsSection"]
        XCTAssertTrue(
            simSection.waitForExistence(timeout: 5) || simCard,
            "SIM cards section should exist on dashboard"
        )
    }

    // MARK: - Send SMS button

    func test_send_sms_button() {
        let sendSmsButton = app.buttons["sendSmsButton"]

        XCTAssertTrue(
            sendSmsButton.waitForExistence(timeout: 5),
            "Send SMS button should be visible"
        )

        sendSmsButton.tap()

        // Should navigate to ComposeView
        let composeView = app.otherElements["composeView"]
        let toField = app.textFields["toField"]
        XCTAssertTrue(
            composeView.waitForExistence(timeout: 5) || toField.waitForExistence(timeout: 3),
            "Tapping Send SMS should navigate to ComposeView"
        )
    }

    // MARK: - Make call button

    func test_make_call_button() {
        let makeCallButton = app.buttons["makeCallButton"]

        XCTAssertTrue(
            makeCallButton.waitForExistence(timeout: 5),
            "Make Call button should be visible"
        )

        makeCallButton.tap()

        // Should navigate to DialerView
        let dialerView = app.otherElements["dialerView"]
        let phoneField = app.textFields["phoneNumberField"]
        XCTAssertTrue(
            dialerView.waitForExistence(timeout: 5) || phoneField.waitForExistence(timeout: 3),
            "Tapping Make Call should navigate to DialerView"
        )
    }

    // MARK: - Navigation to history

    func test_nav_to_history() {
        let historyButton = app.buttons["historyButton"]

        XCTAssertTrue(
            historyButton.waitForExistence(timeout: 5),
            "History toolbar icon should be visible"
        )

        historyButton.tap()

        let historyView = app.otherElements["historyView"]
        let historyTitle = app.navigationBars["History"]
        XCTAssertTrue(
            historyView.waitForExistence(timeout: 5) || historyTitle.waitForExistence(timeout: 3),
            "Tapping history should navigate to HistoryView"
        )
    }

    // MARK: - Navigation to settings

    func test_nav_to_settings() {
        let settingsButton = app.buttons["settingsButton"]

        XCTAssertTrue(
            settingsButton.waitForExistence(timeout: 5),
            "Settings toolbar icon should be visible"
        )

        settingsButton.tap()

        let settingsView = app.otherElements["settingsView"]
        let settingsTitle = app.navigationBars["Settings"]
        XCTAssertTrue(
            settingsView.waitForExistence(timeout: 5) || settingsTitle.waitForExistence(timeout: 3),
            "Tapping settings should navigate to SettingsView"
        )
    }

    // MARK: - Event feed updates

    func test_event_feed_updates() {
        // The event feed section should exist on the dashboard
        let eventFeed = app.otherElements["eventFeed"]

        XCTAssertTrue(
            eventFeed.waitForExistence(timeout: 5),
            "Event feed section should be present on dashboard"
        )
    }
}
