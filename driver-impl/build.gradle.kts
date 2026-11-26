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

tasks.withType<Test>().configureEach {
    // 真机集成测试参数透传（-Dmt.it.* → 测试 JVM）
    for (name in listOf("mt.it.id", "mt.it.model", "mt.it.ip", "mt.it.name", "mt.it.plu")) {
        System.getProperty(name)?.let { systemProperty(name, it) }
    }
}
