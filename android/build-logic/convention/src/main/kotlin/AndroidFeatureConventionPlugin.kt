import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Presentation-Modul eines Features: Bibliothek + Compose + Koin + Navigation + die Core-Module. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("digimob.android.library")
        pluginManager.apply("digimob.android.compose")
        pluginManager.apply("digimob.koin")
        pluginManager.apply("digimob.kotlinx.serialization")
        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:presentation"))
            add("implementation", project(":core:design-system"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-navigation-compose"))
            add("implementation", libs.library("koin-androidx-compose"))
        }
    }
}
