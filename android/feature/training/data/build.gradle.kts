plugins {
    alias(libs.plugins.digimob.android.library)
    alias(libs.plugins.digimob.koin)
    alias(libs.plugins.digimob.kotlinx.serialization)
}

android {
    namespace = "de.mhh.digimob.feature.training.data"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.feature.training.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kermit)
}
