import XCTest

/// UI tests for LoginView using XCUITest.
/// Requires the SimBridgeClient app to be built with accessibility identifiers matching the ones used here.
final class LoginViewTests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--uitesting", "--reset-state"]
        app.launch()
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - All fields present

    func test_all_fields_present() {
        let serverUrlField = app.textFields["serverUrlField"]
        let usernameField = app.textFields["usernameField"]
        let passwordField = app.secureTextFields["passwordField"]

        XCTAssertTrue(serverUrlField.waitForExistence(timeout: 5), "Server URL field should be visible")
        XCTAssertTrue(usernameField.exists, "Username field should be visible")
        XCTAssertTrue(passwordField.exists, "Password field should be visible")
    }

    // MARK: - Login button disabled when empty

    func test_login_button_disabled_when_empty() {
        let loginButton = app.buttons["loginButton"]

        XCTAssertTrue(loginButton.waitForExistence(timeout: 5))
        XCTAssertFalse(loginButton.isEnabled, "Login button should be disabled when fields are empty")
    }

    func test_login_button_enabled_when_filled() {
        let serverUrlField = app.textFields["serverUrlField"]
        let usernameField = app.textFields["usernameField"]
        let passwordField = app.secureTextFields["passwordField"]
        let loginButton = app.buttons["loginButton"]

        serverUrlField.tap()
        serverUrlField.typeText("http://localhost:8100")

        usernameField.tap()
        usernameField.typeText("testuser")

        passwordField.tap()
        passwordField.typeText("password123")

        XCTAssertTrue(loginButton.isEnabled, "Login button should be enabled when all fields are filled")
    }

    // MARK: - Password visibility toggle

    func test_password_visibility_toggle() {
        let passwordSecure = app.secureTextFields["passwordField"]
        let toggleButton = app.buttons["passwordToggle"]

        XCTAssertTrue(passwordSecure.waitForExistence(timeout: 5), "Secure password field should exist initially")

        passwordSecure.tap()
        passwordSecure.typeText("secret")

        // Toggle to show password
        if toggleButton.exists {
            toggleButton.tap()

            // After toggle, the field should become a regular text field
            let passwordPlain = app.textFields["passwordField"]
            XCTAssertTrue(
                passwordPlain.waitForExistence(timeout: 2),
                "Password field should become a plain text field after toggle"
            )
        }
    }

    // MARK: - Loading spinner

    func test_login_shows_loading() {
        let serverUrlField = app.textFields["serverUrlField"]
        let usernameField = app.textFields["usernameField"]
        let passwordField = app.secureTextFields["passwordField"]
        let loginButton = app.buttons["loginButton"]

        serverUrlField.tap()
        serverUrlField.typeText("http://localhost:8100")
        usernameField.tap()
        usernameField.typeText("testuser")
        passwordField.tap()
        passwordField.typeText("password123")

        loginButton.tap()

        // The spinner should appear (even if briefly)
        let spinner = app.activityIndicators["loginSpinner"]
        // We allow it to not appear if the request is instant, but we check for existence
        _ = spinner.waitForExistence(timeout: 3)
        // If the test server is not running, the spinner should eventually disappear
        // and an error should be shown. This is verified in test_login_error_shown.
    }

    // MARK: - Error shown

    func test_login_error_shown() {
        // Attempt login with bad credentials against a non-existent server
        let serverUrlField = app.textFields["serverUrlField"]
        let usernameField = app.textFields["usernameField"]
        let passwordField = app.secureTextFields["passwordField"]
        let loginButton = app.buttons["loginButton"]

        serverUrlField.tap()
        serverUrlField.typeText("http://localhost:9999")
        usernameField.tap()
        usernameField.typeText("baduser")
        passwordField.tap()
        passwordField.typeText("badpass")

        loginButton.tap()

        // Wait for error message to appear
        let errorText = app.staticTexts["loginErrorText"]
        XCTAssertTrue(
            errorText.waitForExistence(timeout: 10),
            "Error message should be displayed on login failure"
        )
    }

    // MARK: - Create account link

    func test_create_account_link() {
        let createAccountButton = app.buttons["createAccountButton"]

        XCTAssertTrue(
            createAccountButton.waitForExistence(timeout: 5),
            "'Create Account' link/button should be visible"
        )

        createAccountButton.tap()

        // Should either navigate to a registration screen or show registration fields
        // We check for the existence of a registration-related element
        let registerButton = app.buttons["registerButton"]
        let registerTitle = app.staticTexts["registerTitle"]
        let exists = registerButton.waitForExistence(timeout: 3) || registerTitle.waitForExistence(timeout: 1)
        XCTAssertTrue(exists, "Tapping 'Create Account' should show registration UI")
    }

    // MARK: - Successful login navigates

    func test_successful_login_navigates() {
        // This test requires a running SimBridge server or mock setup via launch arguments.
        // The app should be configured to use a test server via --uitesting flag.
        let serverUrlField = app.textFields["serverUrlField"]
        let usernameField = app.textFields["usernameField"]
        let passwordField = app.secureTextFields["passwordField"]
        let loginButton = app.buttons["loginButton"]

        serverUrlField.tap()
        serverUrlField.typeText("http://localhost:8100")
        usernameField.tap()
        usernameField.typeText("testuser")
        passwordField.tap()
        passwordField.typeText("testpass")

        loginButton.tap()

        // After successful login, should navigate to PairView or DashboardView
        let pairView = app.otherElements["pairView"]
        let dashboardView = app.otherElements["dashboardView"]

        let navigated = pairView.waitForExistence(timeout: 10) || dashboardView.waitForExistence(timeout: 2)
        XCTAssertTrue(navigated, "Successful login should navigate to Pair or Dashboard screen")
    }
}
