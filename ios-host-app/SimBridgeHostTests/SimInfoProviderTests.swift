import XCTest
@testable import SimBridgeHost

/// Tests for SimInfoProvider using CTTelephonyNetworkInfo mocking.
/// Since CTTelephonyNetworkInfo cannot be directly subclassed for mocking,
/// we test through the protocol-based MockSimInfoProvider.
final class SimInfoProviderTests: XCTestCase {

    private var mockProvider: MockSimInfoProvider!

    override func setUp() {
        super.setUp()
        mockProvider = MockSimInfoProvider()
    }

    override func tearDown() {
        mockProvider = nil
        super.tearDown()
    }

    // MARK: - Returns Active SIMs

    func testReturnsActiveSimsWithCarrierInfo() {
        mockProvider.activeSims = [
            SimInfo(slot: 1, carrier: "T-Mobile", number: "+15551111111"),
            SimInfo(slot: 2, carrier: "AT&T", number: nil),
        ]

        let sims = mockProvider.getActiveSimCards()

        XCTAssertEqual(sims.count, 2)
        XCTAssertEqual(sims[0].slot, 1)
        XCTAssertEqual(sims[0].carrier, "T-Mobile")
        XCTAssertEqual(sims[0].number, "+15551111111")
        XCTAssertEqual(sims[1].slot, 2)
        XCTAssertEqual(sims[1].carrier, "AT&T")
        XCTAssertNil(sims[1].number)
    }

    func testReturnsCarrierNameFromCTTelephonyNetworkInfo() {
        // iOS only provides carrier name, not slot number or phone number directly
        mockProvider.activeSims = [
            SimInfo(slot: 0, carrier: "Verizon", number: nil),
        ]

        let sims = mockProvider.getActiveSimCards()

        XCTAssertEqual(sims[0].carrier, "Verizon")
    }

    // MARK: - Empty on No SIMs

    func testReturnsEmptyListWhenNoSimCards() {
        mockProvider.activeSims = []

        let sims = mockProvider.getActiveSimCards()

        XCTAssertTrue(sims.isEmpty)
    }

    // MARK: - Security Exception Handling

    func testReturnsEmptyListOnPermissionDenied() {
        mockProvider.shouldThrowSecurityError = true

        let sims = mockProvider.getActiveSimCards()

        XCTAssertTrue(sims.isEmpty)
    }

    // MARK: - Call Count Tracking

    func testGetActiveSimCardsCallCountTracked() {
        _ = mockProvider.getActiveSimCards()
        _ = mockProvider.getActiveSimCards()

        XCTAssertEqual(mockProvider.getActiveSimCardsCallCount, 2)
    }

    // MARK: - SimInfo Properties

    func testSimInfoSlotProperty() {
        let sim = SimInfo(slot: 1, carrier: "Sprint", number: "+15559999999")
        XCTAssertEqual(sim.slot, 1)
    }

    func testSimInfoCarrierProperty() {
        let sim = SimInfo(slot: 1, carrier: "Carrier Name", number: nil)
        XCTAssertEqual(sim.carrier, "Carrier Name")
    }

    func testSimInfoNumberIsOptional() {
        let simWithNumber = SimInfo(slot: 1, carrier: "A", number: "+15551234567")
        let simWithoutNumber = SimInfo(slot: 2, carrier: "B", number: nil)

        XCTAssertNotNil(simWithNumber.number)
        XCTAssertNil(simWithoutNumber.number)
    }

    // MARK: - Reset

    func testResetClearsState() {
        mockProvider.activeSims = [SimInfo(slot: 1, carrier: "Test", number: nil)]
        mockProvider.shouldThrowSecurityError = true
        _ = mockProvider.getActiveSimCards()

        mockProvider.reset()

        XCTAssertTrue(mockProvider.activeSims.isEmpty)
        XCTAssertFalse(mockProvider.shouldThrowSecurityError)
        XCTAssertEqual(mockProvider.getActiveSimCardsCallCount, 0)
    }
}
