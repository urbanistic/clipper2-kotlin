import java.util.*

plugins {
    id("maven-publish")
    kotlin("multiplatform") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.2"
//    id("io.kotest.multiplatform") version "5.5.5"
}

group = "de.urbanistic"
version = "1.2.29"

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        all {
            languageSettings.apply {
                optIn("kotlin.js.ExperimentalJsExport")
            }
        }
    }

    jvm {
        jvmToolchain(14)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                enabled = true
                useMocha {
                    timeout = "5000"
                }
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
//                implementation("io.kotest:kotest-framework-engine:5.5.5")
//                implementation("io.kotest:kotest-framework-datatest:5.5.5")
//                implementation("io.kotest:kotest-assertions-core:5.5.5")
            }
        }
        val jvmMain by getting
        val jvmTest by getting {
//            dependencies {
//                implementation("io.kotest:kotest-runner-junit5-jvm:5.5.5")
//            }
        }
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    repositories {
        maven {
            val local = Properties()
            local.load(rootProject.file("local.properties").inputStream())
            val mavenUserName = local.getProperty("repro.user")
            val mavenUserPw = local.getProperty("repro.pw")
            val mavenUrl = local.getProperty("repro.url")
            val reproName = local.getProperty("repro.name")

            name = reproName
            url = uri(mavenUrl)
            credentials(PasswordCredentials::class) {
                username = mavenUserName
                password = mavenUserPw
            }
        }
    }
}
