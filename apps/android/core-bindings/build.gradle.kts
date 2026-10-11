plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pokecio.app.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

abstract class RustlsVerifierVersion : ValueSource<String, RustlsVerifierVersion.Params> {
    interface Params : ValueSourceParameters {
        val lockFile: RegularFileProperty
    }

    override fun obtain(): String {
        val lines = parameters.lockFile.get().asFile.readLines()
        val nameIdx = lines.indexOfFirst { it.trim() == "name = \"rustls-platform-verifier-android\"" }
        check(nameIdx >= 0) { "rustls-platform-verifier-android not found in Cargo.lock" }

        return lines.drop(nameIdx + 1)
            .first { it.trimStart().startsWith("version = ") }
            .substringAfter('"')
            .substringBefore('"')
    }
}

val rustlsVerifierVersion = providers.of(RustlsVerifierVersion::class.java) {
    parameters.lockFile.set(rootProject.layout.projectDirectory.file("../../Cargo.lock"))
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api("${libs.jna.get()}@aar")
    implementation("${libs.rustls.platform.verifier.get()}:${rustlsVerifierVersion.get()}")
}

val repoRoot = rootDir.resolve("../..")

val buildRustCore = tasks.register<Exec>("buildRustCore") {
    workingDir = repoRoot
    commandLine("bash", "scripts/build-android.sh")
    inputs.dir(repoRoot.resolve("core"))
    inputs.file(repoRoot.resolve("Cargo.toml"))
    inputs.property("profile", providers.environmentVariable("POCKY_PROFILE").orElse("android-dev"))
    inputs.property("abis", providers.environmentVariable("POCKY_ABIS").orElse("arm64-v8a"))
    outputs.dir(layout.projectDirectory.dir("src/main/jniLibs"))
    outputs.dir(layout.projectDirectory.dir("src/main/kotlin/com/pokecio/app/core"))
}

tasks.named("preBuild") {
    dependsOn(buildRustCore)
}
