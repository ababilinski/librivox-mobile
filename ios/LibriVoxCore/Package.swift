// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "LibriVoxCore",
    platforms: [
        .iOS("26.0"),
        .macOS("15.0"),
    ],
    products: [
        .library(
            name: "LibriVoxCore",
            targets: ["LibriVoxCore"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", from: "0.9.19"),
    ],
    targets: [
        .target(
            name: "LibriVoxCore",
            dependencies: ["ZIPFoundation"]
        ),
        .testTarget(
            name: "LibriVoxCoreTests",
            dependencies: ["LibriVoxCore", "ZIPFoundation"]
        ),
    ]
)
