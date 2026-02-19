import Foundation

/// A URLProtocol subclass that intercepts all HTTP requests for testing.
/// Configure `requestHandler` before each test to return canned responses.
final class MockURLProtocol: URLProtocol {

    /// Closure invoked for every intercepted request.
    /// Return `(HTTPURLResponse, Data?)` or throw to simulate a network error.
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data?))?

    /// Records every request that passes through for assertion.
    static var capturedRequests: [URLRequest] = []

    // MARK: - URLProtocol overrides

    override class func canInit(with request: URLRequest) -> Bool {
        return true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        return request
    }

    override func startLoading() {
        Self.capturedRequests.append(request)

        guard let handler = Self.requestHandler else {
            let error = NSError(
                domain: "MockURLProtocol",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "No requestHandler set"]
            )
            client?.urlProtocol(self, didFailWithError: error)
            return
        }

        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            if let data = data {
                client?.urlProtocol(self, didLoad: data)
            }
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {
        // No-op
    }

    // MARK: - Helpers

    /// Reset captured state between tests.
    static func reset() {
        requestHandler = nil
        capturedRequests = []
    }
}
