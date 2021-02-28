buildscript {
    repositories {
        mavenLocal()
        maven {
            setUrl("https://dl.bintray.com/pledbrook/plugins")
        }
    }

    dependencies {
        classpath("uk.co.cacoethes:lazybones-gradle:1.2.3")
    }
}

plugins {
    id("uk.co.cacoethes.lazybones-templates") version "1.2.3"
}

configure<uk.co.cacoethes.gradle.lazybones.LazybonesConventions> {
    licenses = mutableListOf("Apache-2.0")
    publish = false
    repositoryName = "pledbrook/lazybones-templates"
    packageExclude("**/*.swp", "**/*.swo", "**/.gradle")
}

// Lazy initialisation of Bintray upload tasks so that not all build users need
// to set the repo.* project properties. The properties are only required when
// executing the publish tasks.
gradle.taskGraph.whenReady {
    tasks.withType(uk.co.cacoethes.gradle.tasks.BintrayGenericUpload::class) {
        onlyIf {
            project.hasProperty("repo.username") && project.hasProperty("repo.apiKey")
        }
        if (name.startsWith("publish")) {
            username = project.findProperty("repo.username").toString()
            apiKey = project.findProperty("repo.apiKey").toString()
        }
    }
}
