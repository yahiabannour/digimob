plugins {
    alias(libs.plugins.digimob.android.library)
    alias(libs.plugins.digimob.android.compose)
}

android {
    namespace = "de.mhh.digimob.core.presentation"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
