import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies {
            add("implementation", platform(libs.library("koin-bom")))
            add("implementation", libs.library("koin-android"))
        }
    }
}
