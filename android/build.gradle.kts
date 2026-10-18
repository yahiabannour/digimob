// Top-level build file: die Plugins hier nur auf den Klassenpfad, angewendet werden sie ueber
// die Convention Plugins in build-logic/
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.junit5) apply false
}
