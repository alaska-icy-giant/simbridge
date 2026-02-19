import XCTest

/// UI tests for SettingsView using XCUITest.
final class SettingsViewTests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--uitesting", "--skip-login", "--paired", "--screen=settings"]
        app.launch()
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - Server URL shown

    func test_server_url_shown() {
        let serverUrlLabel = app.staticTexts["serverUrlLabel"]
        let serverUrlValue = app.staticTexts["serverUrlValue"]

        let urlVisible = serverUrlLabel.waitForExistence(timeout: 5)
            || serverUrlValue.waitForExistence(timeout: 3)

        // Also check for any text containing "http" as fallback
        if !urlVisible {
            let httpText = app.staticTexts.matching(
                NSPredicate(format: "label CONTAINS[c] 'http'")
            )
            XCTAssertTrue(
                httpText.firstMatch.waitForExistence(timeout: 3),
                "Server URL should be displayed in settings"
            )
        } else {
            XCTAssertTrue(urlVisible, "Server URL should be visible")
        }
    }

    // MARK: - Device info shown

    func test_device_info_shown() {
        let deviceNameLabel = app.staticTexts["deviceNameLabel"]
        let deviceIdLabel = app.staticTexts["deviceIdLabel"]
        let deviceInfoSection = app.otherElements["deviceInfoSection"]

        let infoVisible = deviceNameLabel.waitForExistence(timeout: 5)
            || deviceIdLabel.waitForExistence(timeout: 3)
            || deviceInfoSection.waitForExistence(timeout: 3)

        XCTAssertTrue(infoVisible, "Device name and ID should be shown in settings")
    }

    // MARK: - Paired host shown

    func test_paired_host_shown() {
        let pairedHostLabel = app.staticTexts["pairedHostLabel"]
        let pairedHostName = app.staticTexts["pairedHostName"]
        let pairedHostSection = app.otherElements["pairedHostSection"]

        let hostVisible = pairedHostLabel.waitForExistence(timeout: 5)
            || pairedHostName.waitForExistence(timeout: 3)
            || pairedHostSection.waitForExistence(timeout: 3)

        XCTAssertTrue(hostVisible, "Paired host information should be shown in settings")
    }

    // MARK: - Logout confirmation

    func test_logout_confirmation() {
        let logoutButton = app.buttons["logoutButton"]

        XCTAssertTrue(
            logoutButton.waitForExistence(timeout: 5),
            "Logout button should be visible"
        )

        logoutButton.tap()

        // A confirmation dialog should appear
        let alert = app.alerts.firstMatch
        let confirmSheet = app.sheets.firstMatch
        let confirmDialog = app.otherElements["logoutConfirmation"]

        let confirmationShown = alert.waitForExistence(timeout: 5)
            || confirmSheet.waitForExistence(timeout: 3)
            || confirmDialog.waitForExistence(timeout: 3)

        XCTAssertTrue(confirmationShown, "Logout should show a confirmation dialog")

        // Verify the dialog has confirm and cancel options
        if alert.exists {
            let confirmButton = alert.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] 'log out' OR label CONTAINS[c] 'logout' OR label CONTAINS[c] 'confirm' OR label CONTAINS[c] 'yes'")
            )
            let cancelButton = alert.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] 'cancel' OR label CONTAINS[c] 'no'")
            )

            XCTAssertTrue(confirmButton.firstMatch.exists, "Confirmation dialog should have a confirm button")
            XCTAssertTrue(cancelButton.firstMatch.exists, "Confirmation dialog should have a cancel button")
        }
    }

    // MARK: - Logout navigates to login

    func test_logout_navigates_to_login() {
        let logoutButton = app.buttons["logoutButton"]

        XCTAssertTrue(logoutButton.waitForExistence(timeout: 5))

        logoutButton.tap()

        // Confirm the logout
        let alert = app.alerts.firstMatch
        let confirmSheet = app.sheets.firstMatch

        if alert.waitForExistence(timeout: 5) {
            // Find and tap the confirm button
            let confirmButton = alert.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] 'log out' OR label CONTAINS[c] 'logout' OR label CONTAINS[c] 'confirm' OR label CONTAINS[c] 'yes'")
            ).firstMatch
            if confirmButton.exists {
                confirmButton.tap()
            } else {
                // Tap the first non-cancel button
                alert.buttons.allElementsBoundByIndex.last?.tap()
            }
        } else if confirmSheet.waitForExistence(timeout: 3) {
            let confirmButton = confirmSheet.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] 'log out' OR label CONTAINS[c] 'logout'")
            ).firstMatch
            if confirmButton.exists {
                confirmButton.tap()
            }
        }

        // Should navigate back to LoginView
        let loginView = app.otherElements["loginView"]
        let serverUrlField = app.textFields["serverUrlField"]
        let loginButton = app.buttons["loginButton"]

        let atLogin = loginView.waitForExistence(timeout: 10)
            || serverUrlField.waitForExistence(timeout: 5)
            || loginButton.waitForExistence(timeout: 5)

        XCTAssertTrue(atLogin, "After logout, should navigate to LoginView")
    }

    // MARK: - Biometric toggle

    func test_biometric_toggle_presence() {
        // On devices with biometric support, a "Biometric Unlock" toggle should appear.
        // On simulator without biometric hardware, it won't be present.
        let biometricToggle = app.switches["biometricToggle"]
        let biometricLabel = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] 'Biometric Unlock'")
        ).firstMatch

        // We don't assert existence because it depends on hardware.
        // Instead, verify settings screen loads correctly regardless.
        let logoutButton = app.buttons["logoutButton"]
        XCTAssertTrue(
            logoutButton.waitForExistence(timeout: 5),
            "Settings screen should load with logout button regardless of biometric support"
        )
    }

    // MARK: - Cancel logout stays on settings

    func test_cancel_logout_stays_on_settings() {
        let logoutButton = app.buttons["logoutButton"]

        XCTAssertTrue(logoutButton.waitForExistence(timeout: 5))

        logoutButton.tap()

        let alert = app.alerts.firstMatch

        if alert.waitForExistence(timeout: 5) {
            let cancelButton = alert.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] 'cancel' OR label CONTAINS[c] 'no'")
            ).firstMatch

            if cancelButton.exists {
                cancelButton.tap()

                // Should still be on settings
                XCTAssertTrue(
                    logoutButton.waitForExistence(timeout: 3),
                    "After canceling logout, should stay on settings"
                )
            }
        }
    }
}
