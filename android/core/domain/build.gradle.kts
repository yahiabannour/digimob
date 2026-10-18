plugins {
    alias(libs.plugins.digimob.jvm.library)
    `jvm-test-suite`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

// Systemtest wie python/systest.py: spielt die aufgezeichneten Keypoints der Aufnahmen in
// systest.txt in die Uebungen und vergleicht mit der Ground Truth. Liest Dateien, ist also kein
// Unit-Test: eigene Suite, "check" fuehrt ihn mit aus.
val desktopApp = rootDir.parentFile.resolve("python")

testing {
    suites {
        register<JvmTestSuite>("systemTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(project())
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.assertk)
            }
            targets.all {
                testTask.configure {
                    systemProperty("digimob.python", desktopApp.absolutePath)
                    inputs.file(desktopApp.resolve("systest.txt"))
                    inputs.files(
                        fileTree(desktopApp.resolve("recordings")) {
                            include("**/keypoints_full.jsonl", "**/summary.json")
                        }
                    ).withPathSensitivity(PathSensitivity.RELATIVE)
                    testLogging {
                        events("passed", "failed")
                        showStandardStreams = true
                    }
                    shouldRunAfter(tasks.named("test"))
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("systemTest"))
}
