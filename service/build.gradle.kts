dependencies {
    api(project(":core"))
    api(project(":db"))
    api(project(":driver-api"))
    implementation(project(":communication"))
    implementation(project(":driver-impl"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
