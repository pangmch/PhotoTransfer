pluginManagement {
    repositories {
        // Alibaba Cloud mirror (faster in China)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // Original repositories as fallback
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Alibaba Cloud mirror (faster in China)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // Original repositories as fallback
        google()
        mavenCentral()
    }
}

rootProject.name = "PhotoTransfer"
include(":app")
