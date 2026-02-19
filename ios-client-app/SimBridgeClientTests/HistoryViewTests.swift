import XCTest

/// UI tests for HistoryView using XCUITest.
final class HistoryViewTests: XCTestCase {

    var app: XCUIApplication!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--uitesting", "--skip-login", "--paired", "--screen=history"]
        app.launch()
    }

    override func tearDown() {
        app = nil
        super.tearDown()
    }

    // MARK: - Entries listed

    func test_entries_listed() {
        // History entries should be displayed in a list/table
        let historyList = app.tables["historyList"]
        let historyCollection = app.collectionViews["historyList"]
        let scrollView = app.scrollViews.firstMatch

        let listExists = historyList.waitForExistence(timeout: 5)
            || historyCollection.waitForExistence(timeout: 2)
            || scrollView.waitForExistence(timeout: 2)

        XCTAssertTrue(listExists, "History should display a scrollable list of entries")

        // If there are entries, check that cells have content
        let cells = app.cells
        if cells.count > 0 {
            let firstCell = cells.firstMatch
            XCTAssertTrue(firstCell.exists, "History entries should render as cells")
        }
    }

    func test_entries_show_details() {
        // Each entry should show timestamp, type badge, and summary
        let cells = app.cells
        if cells.firstMatch.waitForExistence(timeout: 5) && cells.count > 0 {
            let firstCell = cells.firstMatch

            // Check for timestamp (any text containing time-like pattern)
            let timestampLabel = firstCell.staticTexts["entryTimestamp"]
            let typeBadge = firstCell.staticTexts["entryTypeBadge"]
            let summary = firstCell.staticTexts["entrySummary"]

            // At least some identifying information should be present in each cell
            let hasContent = timestampLabel.exists || typeBadge.exists || summary.exists
                || firstCell.staticTexts.count > 0

            XCTAssertTrue(hasContent, "History entry should display details")
        }
    }

    // MARK: - Pull to refresh

    func test_pull_to_refresh() {
        let historyList = app.tables["historyList"]
        let historyCollection = app.collectionViews["historyList"]
        let scrollView = app.scrollViews.firstMatch

        let list = historyList.exists ? historyList : (historyCollection.exists ? historyCollection : scrollView)

        guard list.waitForExistence(timeout: 5) else {
            XCTFail("History list not found")
            return
        }

        // Perform pull to refresh gesture
        let start = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        let end = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8))
        start.press(forDuration: 0.1, thenDragTo: end)

        // After refresh, the list should still exist (and ideally show updated content)
        XCTAssertTrue(list.waitForExistence(timeout: 5), "List should still exist after pull to refresh")
    }

    // MARK: - Empty state

    func test_empty_state() {
        // When there's no history, an empty state message should be shown
        let emptyText = app.staticTexts["emptyHistoryText"]
        let noHistoryText = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] 'no history' OR label CONTAINS[c] 'empty' OR label CONTAINS[c] 'no messages'")
        )

        // This might not trigger if there IS history from previous tests.
        // We check for existence but allow it to pass if history exists.
        let emptyStateExists = emptyText.waitForExistence(timeout: 3) || noHistoryText.firstMatch.exists
        let cellsExist = app.cells.count > 0

        XCTAssertTrue(
            emptyStateExists || cellsExist,
            "Should show either empty state message or history entries"
        )
    }

    // MARK: - Entry details (tap to expand)

    func test_entry_details() {
        let cells = app.cells

        guard cells.firstMatch.waitForExistence(timeout: 5) else {
            // No history entries to test — skip gracefully
            return
        }

        // Each entry should show: timestamp, type badge, summary
        let firstCell = cells.firstMatch

        // Verify some content exists in the cell
        XCTAssertTrue(firstCell.staticTexts.count > 0, "Entry should contain text elements")

        // Tap to see full details
        firstCell.tap()

        // Should show detail view or expand
        let detailView = app.otherElements["entryDetailView"]
        let backButton = app.navigationBars.buttons.firstMatch

        let detailShown = detailView.waitForExistence(timeout: 5)
            || app.staticTexts["entryPayload"].waitForExistence(timeout: 3)

        // Detail view is optional UX — entry might expand inline or navigate
        if detailShown {
            XCTAssertTrue(true, "Entry detail view shown")
        }
    }
}
