import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Compose fuer ein App- oder Bibliotheksmodul (nach digimob.android.application/library anwenden). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType<CommonExtension>().buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.library("androidx-compose-bom"))
            add("implementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.library("androidx-compose-ui"))
            add("implementation", libs.library("androidx-compose-ui-graphics"))
            add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            add("implementation", libs.library("androidx-compose-material3"))
            add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        }
    }
}
