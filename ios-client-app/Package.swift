// swift-tools-version: 5.10
// Package.swift
// SimBridgeClient
//
// NOTE ON XCODE PROJECT SETUP:
//
// This Package.swift is provided as a reference for Swift Package Manager dependencies.
// The primary way to build this project is via Xcode:
//
// 1. Open Xcode and create a new project:
//    File -> New -> Project -> iOS -> App
//    Product Name: SimBridgeClient
//    Organization: com.simbridge
//    Interface: SwiftUI
//    Language: Swift
//    Minimum deployment target: iOS 16.0
//
// 2. Replace the generated files with the files in SimBridgeClient/
//
// 3. Add Info.plist entries for:
//    - NSMicrophoneUsageDescription (WebRTC audio)
//    - NSLocalNetworkUsageDescription (relay server)
//    - UIBackgroundModes: fetch
//
// 4. Configure signing & capabilities
//
// 5. Build and run on a physical device or simulator.

import PackageDescription

let package = Package(
    name: "SimBridgeClient",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "SimBridgeClient",
            targets: ["SimBridgeClient"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/google/GoogleSignIn-iOS", from: "8.0.0"),
    ],
    targets: [
        .target(
            name: "SimBridgeClient",
            dependencies: [
                .product(name: "GoogleSignIn", package: "GoogleSignIn-iOS"),
            ],
            path: "SimBridgeClient"
        ),
        .testTarget(
            name: "SimBridgeClientTests",
            dependencies: ["SimBridgeClient"],
            path: "SimBridgeClientTests"
        ),
    ]
)
