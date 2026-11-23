import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":communication"))
    implementation(project(":driver-api"))
    implementation(project(":driver-impl"))
    implementation(project(":db"))
    implementation(project(":service"))

    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.datetime)

    implementation(libs.sqlite.jdbc)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
}

compose.desktop {
    application {
        mainClass = "com.scaleplex.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            modules("java.naming", "java.sql", "java.management", "jdk.unsupported")
            packageName = "ScalePlex"
            packageVersion = "1.0.0"
            vendor = "ScalePlex"
            description = "Multi-brand barcode scale management software"
            windows {
                menu = true
                shortcut = true
                dirChooser = true
            }
        }
    }
}
