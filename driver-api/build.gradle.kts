dependencies {
    api(project(":core"))
    api(project(":communication"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
