dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.sqlite.jdbc)
    implementation(libs.slf4j.api)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
