import Foundation

public struct CatalogHTTPResponse: Sendable {
    public var data: Data
    public var statusCode: Int
    public var headers: [String: String]

    public init(data: Data, statusCode: Int, headers: [String: String] = [:]) {
        self.data = data
        self.statusCode = statusCode
        self.headers = headers
    }
}

public protocol CatalogHTTPTransport: Sendable {
    func response(for request: URLRequest) async throws -> CatalogHTTPResponse
}

public struct URLSessionCatalogHTTPTransport: CatalogHTTPTransport {
    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public func response(for request: URLRequest) async throws -> CatalogHTTPResponse {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        let headers: [String: String] = Dictionary(uniqueKeysWithValues: http.allHeaderFields.compactMap { key, value in
            guard let key = key as? String else { return nil }
            return (key.lowercased(), String(describing: value))
        })
        return CatalogHTTPResponse(data: data, statusCode: http.statusCode, headers: headers)
    }
}

public extension CatalogHTTPTransport {
    func successfulData(for request: URLRequest) async throws -> Data {
        let response = try await response(for: request)
        guard (200..<300).contains(response.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return response.data
    }

    func catalogData(
        from url: URL,
        accept: String,
        timeoutInterval: TimeInterval = 12
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.timeoutInterval = timeoutInterval
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        return try await successfulData(for: request)
    }

    func catalogText(
        from url: URL,
        accept: String = "text/html",
        timeoutInterval: TimeInterval = 12
    ) async throws -> String {
        let data = try await catalogData(from: url, accept: accept, timeoutInterval: timeoutInterval)
        guard let text = String(data: data, encoding: .utf8) else { throw URLError(.cannotDecodeContentData) }
        return text
    }
}
