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
    targets: [
        .target(name: "LibriVoxCore"),
        .testTarget(
            name: "LibriVoxCoreTests",
            dependencies: ["LibriVoxCore"]
        ),
    ]
)
