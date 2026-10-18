plugins {
    alias(libs.plugins.digimob.android.library)
    alias(libs.plugins.digimob.koin)
}

android {
    namespace = "de.mhh.digimob.core.data"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kermit)
}
