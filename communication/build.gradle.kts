dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.jserialcomm)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
