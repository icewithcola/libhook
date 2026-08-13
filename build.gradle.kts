import java.net.URI
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.dokka)
  `maven-publish`
  signing
}

group = "uk.kagurach"
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()

android {
  namespace = "uk.kagurach.libhook"
  compileSdk = 36

  defaultConfig {
    minSdk = 29
    consumerProguardFiles("proguard-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    buildConfig = true
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }
}

dependencies {
  compileOnly(libs.xposed.api)
  compileOnly(libs.libxposed.api)

  testImplementation(libs.junit)
}

dokka {
  dokkaPublications.html {
    moduleName.set("libhook")
    outputDirectory.set(layout.projectDirectory.dir("docs"))
  }
  // AGP registers Dokka's Android source sets after this block, so configure future variants too.
  // The publication task documents only the release variant.
  dokkaSourceSets.configureEach {
    sourceLink {
      localDirectory.set(file("src/main/java"))
      remoteUrl.set(URI("https://github.com/icewithcola/libhook/tree/main/src/main/java"))
      remoteLineSuffix.set("#L")
    }
  }
}

val dokkaHtmlJar by tasks.registering(Jar::class) {
  group = "documentation"
  description = "Packages Dokka HTML documentation for Maven Central."
  archiveClassifier.set("javadoc")
  from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

afterEvaluate {
  publishing {
    publications {
      register<MavenPublication>("release") {
        from(components["release"])
        artifact(dokkaHtmlJar)

        pom {
          name.set("libhook")
          description.set("Kotlin APIs for building Xposed and libxposed Android modules.")
          url.set("https://github.com/icewithcola/libhook")

          licenses {
            license {
              name.set("The Apache License, Version 2.0")
              url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
              distribution.set("repo")
            }
          }

          developers {
            developer {
              id.set("icewithcola")
              name.set("icewithcola")
              email.set("me@kagurach.uk")
            }
          }

          scm {
            connection.set("scm:git:git://github.com/icewithcola/libhook.git")
            developerConnection.set("scm:git:ssh://git@github.com/icewithcola/libhook.git")
            url.set("https://github.com/icewithcola/libhook")
          }
        }
      }
    }
  }

  val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
  if (!signingKey.isNullOrBlank()) {
    signing {
      useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingInMemoryKeyPassword").orNull)
      sign(publishing.publications)
    }
  }
}
