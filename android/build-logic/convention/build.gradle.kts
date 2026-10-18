plugins {
    `kotlin-dsl`
}

group = "de.mhh.digimob.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.android.junit5.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.digimob.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.digimob.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.digimob.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.digimob.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.digimob.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("koin") {
            id = libs.plugins.digimob.koin.get().pluginId
            implementationClass = "KoinConventionPlugin"
        }
        register("kotlinxSerialization") {
            id = libs.plugins.digimob.kotlinx.serialization.get().pluginId
            implementationClass = "KotlinxSerializationConventionPlugin"
        }
    }
}
