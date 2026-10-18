plugins {
    alias(libs.plugins.digimob.android.feature)
}

android {
    namespace = "de.mhh.digimob.feature.training.presentation"
}

dependencies {
    implementation(projects.feature.training.domain)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kermit)
}
