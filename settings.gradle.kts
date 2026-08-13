pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradleup.nmcp.settings") version "1.6.1"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven("https://api.xposed.info")
  }
}

rootProject.name = "libhook"

nmcpSettings {
  centralPortal {
    username = providers.gradleProperty("mavenCentralUsername").orElse("").get()
    password = providers.gradleProperty("mavenCentralPassword").orElse("").get()
    publishingType = "AUTOMATIC"
    publicationName = "libhook:${providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()}"
  }
}
