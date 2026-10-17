plugins {
    id("luma.android.library")
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.luma.core.model"
}
dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
}
