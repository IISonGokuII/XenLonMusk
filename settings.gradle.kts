pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("file://${rootProject.projectDir}/.local-repo") }
        google()
        mavenCentral()
    }
}

rootProject.name = "InstaDownloader"
