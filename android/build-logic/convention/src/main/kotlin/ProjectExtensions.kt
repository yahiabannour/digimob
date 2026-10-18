import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** SDK-Versionen und Java/Kotlin 17 fuer App- und Bibliotheksmodule. */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.apply {
            minSdk = libs.version("minSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlinJvmTarget()
}

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

/** JUnit5 + AssertK + Turbine + Coroutines-Test fuer die Unit-Tests eines Moduls. */
internal fun Project.configureUnitTests() {
    dependencies {
        add("testImplementation", platform(libs.library("junit-bom")))
        add("testImplementation", libs.library("junit-jupiter-api"))
        add("testImplementation", libs.library("junit-jupiter-params"))
        add("testImplementation", libs.library("assertk"))
        add("testImplementation", libs.library("turbine"))
        add("testImplementation", libs.library("kotlinx-coroutines-test"))
        add("testRuntimeOnly", libs.library("junit-jupiter-engine"))
        // Gradle 9 laedt den Launcher nicht mehr selbst
        add("testRuntimeOnly", libs.library("junit-platform-launcher"))
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed") }
    }
}
