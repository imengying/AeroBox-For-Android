buildscript {
    val kotlinVersion = "2.3.21"
    val kspVersion = "2.3.7"

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") {
            version {
                strictly(kotlinVersion)
            }
        }
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:$kspVersion")
    }
}

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.7" apply false
}
