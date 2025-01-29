import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

plugins {
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    configure<com.lagradost.cloudstream3.gradle.CloudstreamExtension> {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
        authors = listOf("sarapcanagii")
    }

    configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.sarapcanagii"

        compileSdk = 33

        defaultConfig {
            minSdk = 21
            targetSdk = 33
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    dependencies {
        "apk"("com.lagradost:cloudstream3:pre-release")

        "implementation"(kotlin("stdlib"))
        "implementation"("com.github.Blatzar:NiceHttp:0.4.11")
        "implementation"("org.jsoup:jsoup:1.18.3")
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
        "implementation"("com.fasterxml.jackson.core:jackson-databind:2.16.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

tasks.register("make") {
    doLast {
        println("Custom 'make' task executed")
    }
}