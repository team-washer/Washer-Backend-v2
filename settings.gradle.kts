pluginManagement {
    repositories {
        maven { url = uri("https://repo.spring.io/snapshot") }
        gradlePluginPortal()
    }
}
buildCache {
    local {
        directory = file("$rootDir/.gradle/build-cache")
    }
}
rootProject.name = "Washer-Backend-v2"
