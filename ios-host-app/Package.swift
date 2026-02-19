// swift-tools-version: 5.10
// Package.swift
// SimBridgeHost
//
// NOTE ON XCODE PROJECT SETUP:
//
// This Package.swift is provided as a reference for Swift Package Manager dependencies.
// The primary way to build this project is via Xcode:
//
// 1. Open Xcode and create a new project:
//    File -> New -> Project -> iOS -> App
//    Product Name: SimBridgeHost
//    Organization: com.simbridge
//    Interface: SwiftUI
//    Language: Swift
//    Minimum deployment target: iOS 16.0
//
// 2. Replace the generated files with the files in SimBridgeHost/
//
// 3. Add the WebRTC SPM dependency:
//    File -> Add Packages -> Enter URL:
//    https://github.com/nicolo-ribaudo/webrtc-swiftpm
//    or use CocoaPods: pod 'GoogleWebRTC'
//
// 4. Add required frameworks (Build Phases -> Link Binary With Libraries):
//    - CallKit.framework
//    - AVFoundation.framework
//    - CoreTelephony.framework
//    - MessageUI.framework
//
// 5. Configure signing & capabilities:
//    - Background Modes: Voice over IP, Background fetch
//    - Push Notifications (for VoIP push)
//
// 6. Add Info.plist entries (see Info.plist in this directory)
//
// 7. Build and run on a physical device (many features require real hardware).

import PackageDescription

let package = Package(
    name: "SimBridgeHost",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "SimBridgeHost",
            targets: ["SimBridgeHost"]
        ),
    ],
    dependencies: [
        // WebRTC framework for peer-to-peer audio
        // Uncomment when building with SPM:
        // .package(url: "https://github.com/nicolo-ribaudo/webrtc-swiftpm", from: "114.0.0"),
    ],
    targets: [
        .target(
            name: "SimBridgeHost",
            dependencies: [
                // Uncomment when building with SPM:
                // .product(name: "WebRTC", package: "webrtc-swiftpm"),
            ],
            path: "SimBridgeHost"
        ),
        .testTarget(
            name: "SimBridgeHostTests",
            dependencies: ["SimBridgeHost"],
            path: "SimBridgeHostTests"
        ),
    ]
)
