// Routes all plugin and dependency repositories through the Tencent mirror
// to avoid blocked hosts (services.gradle.org / github.com) on this network.
import java.net.URI

settingsEvaluated { settings ->
    settings.pluginManagement {
        repositories {
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
            maven { url = uri("https://mirrors.cloud.tencent.com/repository/gradle-plugin/") }
            gradlePluginPortal()
            mavenCentral()
        }
    }
    settings.dependencyResolutionManagement {
        repositories {
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
            maven { url = uri("https://mirrors.cloud.tencent.com/repository/gradle-plugin/") }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
