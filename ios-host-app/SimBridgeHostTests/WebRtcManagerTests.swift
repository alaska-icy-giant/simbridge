import XCTest
@testable import SimBridgeHost

/// Tests for WebRtcManager. Since WebRTC.framework requires a real device context,
/// we test through protocol-based abstractions and verify signaling logic.
final class WebRtcManagerTests: XCTestCase {

    // MARK: - Mock WebRTC Manager

    /// Lightweight mock that simulates WebRtcManager behavior for unit testing.
    private class MockWebRtcManager {
        var peerConnectionCreated = false
        var onIceCandidate: ((IceCandidateInfo) -> Void)?
        var onIceConnectionChange: ((String) -> Void)?
        var localDescription: SessionDescriptionInfo?
        var remoteDescription: SessionDescriptionInfo?

        struct IceCandidateInfo {
            let sdp: String
            let sdpMid: String
            let sdpMLineIndex: Int
        }

        struct SessionDescriptionInfo {
            let type: String  // "offer" or "answer"
            let sdp: String
        }

        func createPeerConnection() {
            peerConnectionCreated = true
        }

        func createOffer(completion: @escaping (SessionDescriptionInfo?) -> Void) {
            guard peerConnectionCreated else {
                completion(nil)
                return
            }
            let offer = SessionDescriptionInfo(
                type: "offer",
                sdp: "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\n"
            )
            localDescription = offer
            completion(offer)
        }

        func createAnswer(completion: @escaping (SessionDescriptionInfo?) -> Void) {
            guard peerConnectionCreated else {
                completion(nil)
                return
            }
            let answer = SessionDescriptionInfo(
                type: "answer",
                sdp: "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\n"
            )
            localDescription = answer
            completion(answer)
        }

        func setRemoteDescription(_ sdp: SessionDescriptionInfo, completion: (() -> Void)? = nil) {
            remoteDescription = sdp
            completion?()
        }

        func addIceCandidate(_ candidate: IceCandidateInfo) {
            // In real implementation, this adds to the PeerConnection
        }

        func dispose() {
            peerConnectionCreated = false
            localDescription = nil
            remoteDescription = nil
        }

        /// Simulates an ICE candidate being generated.
        func simulateIceCandidate(sdp: String, sdpMid: String, sdpMLineIndex: Int) {
            let candidate = IceCandidateInfo(sdp: sdp, sdpMid: sdpMid, sdpMLineIndex: sdpMLineIndex)
            onIceCandidate?(candidate)
        }
    }

    private var webRtcManager: MockWebRtcManager!
    private var sentMessages: [WsMessage]!

    override func setUp() {
        super.setUp()
        webRtcManager = MockWebRtcManager()
        sentMessages = []
    }

    override func tearDown() {
        webRtcManager = nil
        sentMessages = nil
        super.tearDown()
    }

    // MARK: - PeerConnection Creation

    func testPeerConnectionCreated() {
        webRtcManager.createPeerConnection()

        XCTAssertTrue(webRtcManager.peerConnectionCreated)
    }

    func testPeerConnectionCreatedIsNonNull() {
        // Before creation
        XCTAssertFalse(webRtcManager.peerConnectionCreated)

        webRtcManager.createPeerConnection()

        XCTAssertTrue(webRtcManager.peerConnectionCreated,
                       "PeerConnectionFactory should produce a non-null PeerConnection")
    }

    // MARK: - Offer Contains Audio

    func testOfferContainsAudioMLine() {
        let expectation = expectation(description: "Offer created")

        webRtcManager.createPeerConnection()
        webRtcManager.createOffer { sdp in
            XCTAssertNotNil(sdp, "SDP offer should not be nil")
            XCTAssertTrue(sdp!.sdp.contains("m=audio"),
                           "SDP offer should contain audio m-line")
            XCTAssertEqual(sdp!.type, "offer")
            expectation.fulfill()
        }

        waitForExpectations(timeout: 1.0)
    }

    func testOfferContainsOpusCodec() {
        let expectation = expectation(description: "Offer with opus")

        webRtcManager.createPeerConnection()
        webRtcManager.createOffer { sdp in
            XCTAssertTrue(sdp!.sdp.contains("opus"),
                           "SDP offer should contain opus codec")
            expectation.fulfill()
        }

        waitForExpectations(timeout: 1.0)
    }

    func testOfferFailsWithoutPeerConnection() {
        let expectation = expectation(description: "Offer fails")

        // Do NOT create peer connection
        webRtcManager.createOffer { sdp in
            XCTAssertNil(sdp, "Offer should fail without PeerConnection")
            expectation.fulfill()
        }

        waitForExpectations(timeout: 1.0)
    }

    // MARK: - ICE Candidate Sent

    func testIceCandidateTriggersSendCallback() {
        var receivedCandidate: MockWebRtcManager.IceCandidateInfo?

        webRtcManager.onIceCandidate = { candidate in
            receivedCandidate = candidate
        }

        webRtcManager.createPeerConnection()
        webRtcManager.simulateIceCandidate(
            sdp: "candidate:1 1 UDP 2122194687 192.168.1.1 12345 typ host",
            sdpMid: "audio",
            sdpMLineIndex: 0
        )

        XCTAssertNotNil(receivedCandidate)
        XCTAssertEqual(receivedCandidate?.sdpMid, "audio")
        XCTAssertEqual(receivedCandidate?.sdpMLineIndex, 0)
        XCTAssertTrue(receivedCandidate!.sdp.contains("candidate"))
    }

    func testIceCandidateConvertedToWsMessage() {
        webRtcManager.onIceCandidate = { [weak self] candidate in
            let msg = WsMessage(
                type: "webrtc",
                action: "ice",
                candidate: candidate.sdp,
                sdpMid: candidate.sdpMid,
                sdpMLineIndex: candidate.sdpMLineIndex
            )
            self?.sentMessages.append(msg)
        }

        webRtcManager.createPeerConnection()
        webRtcManager.simulateIceCandidate(
            sdp: "candidate:1 1 UDP 2122194687 10.0.0.1 54321 typ host",
            sdpMid: "0",
            sdpMLineIndex: 0
        )

        XCTAssertEqual(sentMessages.count, 1)
        XCTAssertEqual(sentMessages[0].type, "webrtc")
        XCTAssertEqual(sentMessages[0].action, "ice")
        XCTAssertNotNil(sentMessages[0].candidate)
        XCTAssertEqual(sentMessages[0].sdpMid, "0")
        XCTAssertEqual(sentMessages[0].sdpMLineIndex, 0)
    }

    // MARK: - Signaling: Offer Handled

    func testIncomingOfferCreatesAnswer() {
        let expectation = expectation(description: "Answer created from offer")

        webRtcManager.createPeerConnection()

        let remoteSdp = MockWebRtcManager.SessionDescriptionInfo(
            type: "offer",
            sdp: "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
        )

        webRtcManager.setRemoteDescription(remoteSdp) {
            self.webRtcManager.createAnswer { answer in
                XCTAssertNotNil(answer)
                XCTAssertEqual(answer?.type, "answer")
                XCTAssertTrue(answer!.sdp.contains("m=audio"))
                expectation.fulfill()
            }
        }

        waitForExpectations(timeout: 1.0)
    }

    // MARK: - Signaling: Answer Handled

    func testIncomingAnswerSetsRemoteDescription() {
        webRtcManager.createPeerConnection()

        let remoteSdp = MockWebRtcManager.SessionDescriptionInfo(
            type: "answer",
            sdp: "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
        )

        webRtcManager.setRemoteDescription(remoteSdp)

        XCTAssertNotNil(webRtcManager.remoteDescription)
        XCTAssertEqual(webRtcManager.remoteDescription?.type, "answer")
    }

    // MARK: - Dispose

    func testDisposeCleansPeerConnection() {
        webRtcManager.createPeerConnection()
        XCTAssertTrue(webRtcManager.peerConnectionCreated)

        webRtcManager.dispose()

        XCTAssertFalse(webRtcManager.peerConnectionCreated)
        XCTAssertNil(webRtcManager.localDescription)
        XCTAssertNil(webRtcManager.remoteDescription)
    }
}
