plugins {
    alias(libs.plugins.digimob.android.feature)
}

android {
    namespace = "de.mhh.digimob.feature.menu.presentation"
}

dependencies {
    implementation(libs.kermit)
}
