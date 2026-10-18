import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.digimob.android.application)
    alias(libs.plugins.digimob.android.compose)
    alias(libs.plugins.digimob.koin)
    alias(libs.plugins.digimob.kotlinx.serialization)
}

android {
    namespace = "de.mhh.digimob"

    defaultConfig {
        applicationId = "de.mhh.digimob"
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.presentation)
    implementation(projects.core.designSystem)
    implementation(projects.feature.menu.presentation)
    implementation(projects.feature.training.domain)
    implementation(projects.feature.training.data)
    implementation(projects.feature.training.presentation)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kermit)
}

/**
 * Die 3D-Demos und das Pose-Modell kommen aus der Desktop-App (die mp4 in python/demos,
 * python/models/pose_landmarker_full.task) und werden beim Bauen in die Assets kopiert,
 * ohne zweite Kopie in git. Nur die Videos selbst, nicht demos/assets/ oder demos/versions/.
 */
abstract class CopyDesktopAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val demos: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val model: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        fileSystem.sync {
            from(demos) { into("demos") }
            from(model)
            into(outputDir)
        }
    }
}

val desktopApp: File = rootDir.parentFile.resolve("python")
val copyDesktopAssets = tasks.register<CopyDesktopAssets>("copyDesktopAssets") {
    demos.from(fileTree(desktopApp.resolve("demos")) { include("*.mp4") })
    model.set(desktopApp.resolve("models/pose_landmarker_full.task"))
    outputDir.set(layout.buildDirectory.dir("generated/desktopAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyDesktopAssets, CopyDesktopAssets::outputDir)
    }
}
