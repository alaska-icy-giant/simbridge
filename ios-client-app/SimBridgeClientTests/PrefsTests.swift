import XCTest

final class PrefsTests: XCTestCase {

    var sut: StubPrefs!

    override func setUp() {
        super.setUp()
        sut = StubPrefs()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - Save and read token

    func test_save_and_read_token() {
        XCTAssertNil(sut.token)

        sut.token = "jwt-abc-123"

        XCTAssertEqual(sut.token, "jwt-abc-123")
    }

    func test_save_and_read_server_url() {
        sut.serverUrl = "http://example.com:8100"

        XCTAssertEqual(sut.serverUrl, "http://example.com:8100")
    }

    func test_save_and_read_device_id() {
        sut.deviceId = 42

        XCTAssertEqual(sut.deviceId, 42)
    }

    func test_save_and_read_device_name() {
        sut.deviceName = "Test iPhone"

        XCTAssertEqual(sut.deviceName, "Test iPhone")
    }

    // MARK: - isLoggedIn

    func test_is_logged_in_true() {
        sut.token = "some-valid-token"

        XCTAssertTrue(sut.isLoggedIn)
    }

    func test_is_logged_in_false_when_nil() {
        sut.token = nil

        XCTAssertFalse(sut.isLoggedIn)
    }

    func test_is_logged_in_false_when_empty() {
        sut.token = ""

        XCTAssertFalse(sut.isLoggedIn)
    }

    // MARK: - Clear removes all

    func test_clear_removes_all() {
        sut.serverUrl = "http://localhost:8100"
        sut.token = "jwt-token"
        sut.deviceId = 10
        sut.deviceName = "My Device"
        sut.pairedHostId = 20
        sut.pairedHostName = "Host Phone"

        sut.clear()

        XCTAssertNil(sut.serverUrl)
        XCTAssertNil(sut.token)
        XCTAssertNil(sut.deviceId)
        XCTAssertNil(sut.deviceName)
        XCTAssertNil(sut.pairedHostId)
        XCTAssertNil(sut.pairedHostName)
    }

    // MARK: - Paired host ID persists

    func test_paired_host_id_persists() {
        // Use a known suite name so we can create a second StubPrefs pointing to the same store
        let sharedSuite = "com.simbridge.tests.persistence.\(UUID().uuidString)"
        let prefs1 = StubPrefs(suiteName: sharedSuite)

        prefs1.pairedHostId = 99

        // Create a second instance pointing to the same suite (simulates app restart)
        let prefs2 = StubPrefs(suiteName: sharedSuite)

        XCTAssertEqual(prefs2.pairedHostId, 99, "pairedHostId should survive 'app restart' via same suite")

        // Cleanup
        UserDefaults.standard.removePersistentDomain(forName: sharedSuite)
    }

    func test_paired_host_name_persists() {
        let sharedSuite = "com.simbridge.tests.persistence.\(UUID().uuidString)"
        let prefs1 = StubPrefs(suiteName: sharedSuite)

        prefs1.pairedHostName = "Android Host"

        let prefs2 = StubPrefs(suiteName: sharedSuite)

        XCTAssertEqual(prefs2.pairedHostName, "Android Host")

        UserDefaults.standard.removePersistentDomain(forName: sharedSuite)
    }

    // MARK: - Test isolation

    func test_instances_are_isolated() {
        let prefs1 = StubPrefs()
        let prefs2 = StubPrefs()

        prefs1.token = "token-1"
        prefs2.token = "token-2"

        XCTAssertEqual(prefs1.token, "token-1")
        XCTAssertEqual(prefs2.token, "token-2")
        XCTAssertNotEqual(prefs1.token, prefs2.token)
    }
}
