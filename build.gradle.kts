buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") {
            version {
                strictly("2.3.10")
            }
        }
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.4")
    }
}

plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false
}
