plugins {
    alias(libs.plugins.digimob.jvm.library)
    `jvm-test-suite`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

// Systemtests: spielen die aufgezeichneten Keypoints von Aufnahmen in die Uebungen und vergleichen mit
// der Ground Truth. Lesen Dateien, sind also keine Unit-Tests: eigene Suite, "check" fuehrt beide aus.
//   systemTest  python/systest.txt, die Aufnahmen der PC-Webcam, wie python/systest.py
//   tabletTest  systest_tablet.txt, die Aufnahmen vom Tablet (Aufnehmen im Debug-Build)
val desktopApp = rootDir.parentFile.resolve("python")

/** Welche Liste ein Systemtest abspielt: [root]/[list] mit den Aufnahmen unter [root]/recordings. */
fun Test.replays(root: File, list: String) {
    systemProperty("digimob.root", root.absolutePath)
    systemProperty("digimob.list", list)
    systemProperty("digimob.android", rootDir.absolutePath)
    inputs.file(root.resolve(list))
    inputs.files(
        fileTree(root.resolve("recordings")) { include("**/keypoints_full.jsonl", "**/summary.json") }
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
    shouldRunAfter(tasks.named("test"))
}

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
                testTask.configure { replays(desktopApp, "systest.txt") }
            }
        }
    }
}

// Dieselben Testklassen, nur mit der Tablet-Liste
val tabletTest = tasks.register<Test>("tabletTest") {
    description = "Spielt nur die Tablet-Aufnahmen aus systest_tablet.txt ab."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets["systemTest"].output.classesDirs
    classpath = sourceSets["systemTest"].runtimeClasspath
    useJUnitPlatform()
    replays(rootDir, "systest_tablet.txt")
}

tasks.named("check") {
    dependsOn(testing.suites.named("systemTest"), tabletTest)
}
