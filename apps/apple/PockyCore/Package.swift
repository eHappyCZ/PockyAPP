// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "PockyCore",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "PockyCore", targets: ["PockyCore"]),
    ],
    targets: [
        .binaryTarget(name: "PockyFFI", path: "PockyFFI.xcframework"),
        .target(
            name: "PockyCore",
            dependencies: ["PockyFFI"],
            path: "Sources/PockyCore",
            linkerSettings: [
                .linkedFramework("Security"),
                .linkedFramework("SystemConfiguration"),
            ]
        ),
    ]
)
