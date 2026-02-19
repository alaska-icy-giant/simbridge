import XCTest
@testable import SimBridgeHost

final class SecureTokenStoreTests: XCTestCase {

    private var store: SecureTokenStore!

    override func setUp() {
        super.setUp()
        store = SecureTokenStore()
        store.clear()
    }

    override func tearDown() {
        store.clear()
        store = nil
        super.tearDown()
    }

    func testGetTokenReturnsNilWhenEmpty() {
        XCTAssertNil(store.getToken())
    }

    func testSaveAndGetTokenRoundTrip() {
        let token = "jwt.token.value"
        store.saveToken(token)
        XCTAssertEqual(store.getToken(), token)
    }

    func testClearRemovesSavedToken() {
        store.saveToken("some-token")
        store.clear()
        XCTAssertNil(store.getToken())
    }

    func testSaveTokenOverwritesPreviousValue() {
        store.saveToken("first-token")
        store.saveToken("second-token")
        XCTAssertEqual(store.getToken(), "second-token")
    }
}
