plugins {
    alias(libs.plugins.digimob.jvm.library)
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.core)
}
