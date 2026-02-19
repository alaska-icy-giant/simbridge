import XCTest

/// UI tests for PairView using XCUITest.
final class PairViewTests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        // Launch directly into PairView by providing a valid token but no pairing
        app.launchArguments = ["--uitesting", "--skip-login", "--no-pairing"]
        app.launch()
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - Code input visible

    func test_code_input_visible() {
        let codeInput = app.textFields["pairCodeField"]

        XCTAssertTrue(
            codeInput.waitForExistence(timeout: 5),
            "6-digit code input field should be visible"
        )
    }

    // MARK: - Pair button exists

    func test_pair_button_exists() {
        let pairButton = app.buttons["pairButton"]

        XCTAssertTrue(
            pairButton.waitForExistence(timeout: 5),
            "'Pair' button should be visible"
        )
    }

    // MARK: - Pair button disabled when empty

    func test_pair_button_disabled_when_empty() {
        let pairButton = app.buttons["pairButton"]
        let codeInput = app.textFields["pairCodeField"]

        XCTAssertTrue(pairButton.waitForExistence(timeout: 5))

        // Ensure code field is empty
        if let value = codeInput.value as? String, !value.isEmpty {
            codeInput.tap()
            // Clear existing text
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count)
            codeInput.typeText(deleteString)
        }

        XCTAssertFalse(pairButton.isEnabled, "Pair button should be disabled when code field is empty")
    }

    // MARK: - Pair success navigates

    func test_pair_success_navigates() {
        // This test requires a running server with a valid pairing code
        let codeInput = app.textFields["pairCodeField"]
        let pairButton = app.buttons["pairButton"]

        XCTAssertTrue(codeInput.waitForExistence(timeout: 5))

        codeInput.tap()
        codeInput.typeText("123456")

        pairButton.tap()

        // On success, should navigate to DashboardView
        let dashboardView = app.otherElements["dashboardView"]
        XCTAssertTrue(
            dashboardView.waitForExistence(timeout: 10),
            "Successful pairing should navigate to Dashboard"
        )
    }

    // MARK: - Pair error shown

    func test_pair_error_shown() {
        let codeInput = app.textFields["pairCodeField"]
        let pairButton = app.buttons["pairButton"]

        XCTAssertTrue(codeInput.waitForExistence(timeout: 5))

        codeInput.tap()
        codeInput.typeText("000000") // Invalid code

        pairButton.tap()

        // Should show error message
        let errorText = app.staticTexts["pairErrorText"]
        XCTAssertTrue(
            errorText.waitForExistence(timeout: 10),
            "Error message should be shown for invalid/expired code"
        )

        // Verify the error text contains relevant information
        if let label = errorText.label as String? {
            let containsRelevantText = label.lowercased().contains("invalid")
                || label.lowercased().contains("expired")
                || label.lowercased().contains("error")
            XCTAssertTrue(containsRelevantText, "Error should mention invalid or expired code")
        }
    }

    // MARK: - Numeric only input

    func test_code_input_numeric_only() {
        let codeInput = app.textFields["pairCodeField"]

        XCTAssertTrue(codeInput.waitForExistence(timeout: 5))

        codeInput.tap()
        codeInput.typeText("12ab56")

        // The field should only contain digits
        let value = codeInput.value as? String ?? ""
        let isNumericOnly = value.allSatisfy { $0.isNumber }
        XCTAssertTrue(isNumericOnly, "Code input should only accept numeric characters, got: '\(value)'")
    }

    // MARK: - Instructional text

    func test_instruction_text_visible() {
        let instructionText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] '6-digit'")
        )

        XCTAssertTrue(
            instructionText.firstMatch.waitForExistence(timeout: 5),
            "Instruction text mentioning 6-digit code should be visible"
        )
    }
}
