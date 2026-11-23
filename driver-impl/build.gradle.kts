dependencies {
    api(project(":driver-api"))
    implementation(project(":communication"))
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.slf4j.api)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
