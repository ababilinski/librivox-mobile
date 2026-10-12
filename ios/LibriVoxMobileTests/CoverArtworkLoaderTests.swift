import UIKit
import XCTest
@testable import LibriVoxMobile

@MainActor
final class CoverArtworkLoaderTests: XCTestCase {
    func testFullScreenResolutionDoesNotReuseThumbnailDecode() async throws {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let data = UIGraphicsImageRenderer(size: CGSize(width: 3_000, height: 2_000), format: format).jpegData(withCompressionQuality: 0.8) { context in
            UIColor.systemRed.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 3_000, height: 2_000))
        }
        let url = try XCTUnwrap(URL(string: "https://example.invalid/\(UUID().uuidString).jpg"))
        let thumbnail = CoverImageLoader(dataLoader: { _ in data })
        await thumbnail.load(urls: [url])
        XCTAssertEqual(try XCTUnwrap(thumbnail.image?.cgImage).width, 1_024)

        let fullScreen = CoverImageLoader(maxPixelSize: 2_560, dataLoader: { _ in data })
        await fullScreen.load(urls: [url])
        XCTAssertEqual(try XCTUnwrap(fullScreen.image?.cgImage).width, 2_560)
        XCTAssertEqual(try XCTUnwrap(thumbnail.image?.cgImage).width, 1_024)
    }
}
