plugins {
    // Support Groovy sources
    groovy

    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Support Maven publications
    `maven-publish`
}

group = "uk.co.cacoethes"
version = "1.2.5-SNAPSHOT"

tasks.named<Jar>("jar").get().archiveBaseName.set("lazybones-gradle")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // gradleApi() drags in the Groovy bundled with Gradle, so always a good idea
    // to specify localGroovy() too
    implementation(localGroovy())
    implementation(gradleApi())

    testImplementation("org.spockframework:spock-core:0.7-groovy-2.0") {
        exclude(module = "groovy-all")
    }
    testImplementation("cglib:cglib-nodep:2.2.2")
}

// Disable publishing task(s) if no credentials are defined.
tasks.withType(PublishToMavenRepository::class) {
    onlyIf {
        project.hasProperty("repo.username") && project.hasProperty("repo.apiKey")
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginLibrary") {
            from(components["java"])
        }
    }

    // Only include repositories for publication if credentials are defined.
    if (project.hasProperty("repo.username") && project.hasProperty("repo.apiKey")) {
        repositories {
            maven {
                credentials {
                    username = "${project.findProperty("repo.username")}"
                    password = "${project.findProperty("repo.apiKey")}"
                }
                name = "lazybonesBintrayRepo"
                url = uri("https://api.bintray.com/maven/pledbrook/plugins/lazybones-gradle")
            }
        }
    }
}

gradlePlugin {
    plugins {
        create("autoUpdate") {
            id = "uk.co.cacoethes.lazybones-templates"
            implementationClass = "uk.co.cacoethes.gradle.LazybonesTemplatesPlugin"
        }
    }
}
