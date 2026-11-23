rootProject.name = "ScalePlex"

pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.cloud.tencent.com/repository/gradle-plugin/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.cloud.tencent.com/repository/gradle-plugin/")
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":core")
include(":communication")
include(":driver-api")
include(":driver-impl")
include(":db")
include(":service")
include(":app")
