// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "sdk-gen-swift",
    platforms: [.macOS(.v13)],
    products: [
        .library(name: "SdkGenSwift", targets: ["SdkGenSwift"])
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-protobuf.git", from: "1.28.0")
    ],
    targets: [
        .target(
            name: "SdkGenSwift",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf")
            ],
            path: "stream"
        )
    ]
)
