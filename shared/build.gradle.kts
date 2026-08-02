import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // The iOS app lives in a separate sibling repo (D11), so it cannot use
    // embedAndSignAppleFrameworkForXcode — that assumes a single project. Instead we publish an
    // XCFramework covering device + simulator, which RecompTracker-IOS/scripts/sync-shared.sh
    // copies into its Frameworks/ directory. Build with:
    //   ./gradlew :shared:assembleSharedDebugXCFramework
    val xcf = XCFramework("Shared")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            // `api`, not `implementation`: kotlinx-datetime types (LocalDate, …) appear in the
            // public signatures of the moved domain packages, so consumers must see them.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Existing JUnit4 domain tests live here: JVM-only, and `internal` in commonMain is
        // visible because test compilations are associated with main compilations of the module.
        val androidUnitTest by getting {
            // Test-only fixtures shared with :app's test source set (see app/build.gradle.kts).
            // Kept out of androidUnitTest/kotlin so :app can add just this directory without
            // pulling in — and re-running — the whole shared test suite.
            kotlin.srcDir("src/testFixtures/kotlin")
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

android {
    namespace = "com.zack.recomptracker.shared"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
