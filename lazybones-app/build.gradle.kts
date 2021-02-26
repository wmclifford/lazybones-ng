import java.util.Objects

buildscript {
    repositories {
        // Keep this for testing dev versions of the lazybones-templates plugin
//        mavenCentral()
//        mavenLocal()
    }

    dependencies {
        // Keep this for testing dev versions of the lazybones-templates plugin
//        classpath "uk.co.cacoethes:lazybones-gradle:1.2.3"
    }
}

plugins {
    groovy
    application
    id("net.saliman.cobertura") version "4.0.0"
    id("uk.co.cacoethes.lazybones-templates") version "1.2.3"
    id("io.sdkman.vendors") version "2.0.0"
}

// apply plugin: "codenarc"

application {
    // These settings mimic the old client VM behavior. Should result in faster startup.
    applicationDefaultJvmArgs = mutableListOf(
            "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=3")
    applicationName = "lazybones"
    mainClass.set("uk.co.cacoethes.lazybones.LazybonesMain")
}

version = "0.8.4-SNAPSHOT"

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

ext {
    set("groovyVersion", "2.4.15")
    set("cachePath", file("""${System.getProperty("user.home")}/.lazybones/templates""").absoluteFile)
    set("isCiBuild", System.getProperty("drone.io", "false")?.toBoolean())
    set("testWorkDir", file("$buildDir/testWork").path)
}

configure<SourceSetContainer> {
    register("integTest") {
        withConvention(GroovySourceSet::class) {
            groovy.srcDirs("src/integTest/groovy")
        }
        compileClasspath += sourceSets.named("main").get().output
        resources.srcDirs("src/integTest/resources")
    }
}

configurations {
    named("integTestImplementation").get().resolutionStrategy {
        force("org.codehaus.goovy:groovy-all:${project.extra["groovyVersion"]}")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy:${project.extra["groovyVersion"]}")
    implementation("commons-io:commons-io:2.4")
    implementation("org.codehaus.groovy:groovy-json:${project.extra["groovyVersion"]}")
    implementation("org.codehaus.groovy:groovy-templates:${project.extra["groovyVersion"]}")
    implementation("org.apache.commons:commons-compress:1.5")
    implementation("com.github.groovy-wslite:groovy-wslite:1.1.2")
    implementation("net.sf.jopt-simple:jopt-simple:4.4")
    implementation("org.ini4j:ini4j:0.5.2")

    runtimeOnly("org.apache.ivy:ivy:2.3.0")

    testImplementation("org.spockframework:spock-core:0.7-groovy-2.0") {
        exclude(module = "groovy-all")
    }

    "integTestImplementation"("org.codehaus.groovy:groovy-all:${project.extra["groovyVersion"]}")
    "integTestImplementation"("commons-io:commons-io:2.4")
    "integTestImplementation"("co.freeside:betamax:1.1.2")
    "integTestImplementation"("org.littleshoot:littleproxy:1.1.0-beta1")

    "integTestImplementation"("org.spockframework:spock-core:1.0-groovy-2.4")
}

//idea {
//    module {
//        scopes["PROVIDED"].plusAssign(listOf(configurations.named("integTestImplementation")))
//    }
//}

tasks.jar {
    manifest {
        attributes(mapOf(
                "Implementation-Title" to "Lazybones",
                "Implementation-Version" to project.version
        ))
    }
}

configure<io.sdkman.vendors.model.SdkmanExtension> {
    api = "https://vendors.sdkman.io/"
    candidate = application.applicationName
    version = "${project.version}"
    url = "https://bintray.com/artifact/download/pledbrook/lazybones-templates/${application.applicationName}-${project.version}.zip"
    hashtag = "#lazybones"
}

configure<uk.co.cacoethes.gradle.lazybones.LazybonesConventions> {
    templateDirs = files(file("${projectDir}/src/integTest/templates").listFiles())
    template("subtemplates-tmpl").includes("controller", "entity", "bad")
    template("test-handlebars").version = "0.1.1"
}

tasks.named("packageAllTemplates").get().dependsOn("packageTemplate-Oops-stuff")
tasks.named("installAllTemplates").get().dependsOn("installTemplate-Oops-stuff")

tasks.register("configureCachePath") {
    if (project.extra["isCiBuild"] as Boolean) {
        // Load the default cache directory location from the default config file.
        // We have to defer the evaluation until after the test resources have been
        // copied onto the classpath.
        val defaultConfigFile = sourceSets.main.get().resources.filter {
            it.name == "uk.co.cacoethes.lazybones.config.defaultConfig.groovy"
        }.singleFile
        val defaultConfig = groovy.util.ConfigSlurper().parse(defaultConfigFile.readText())
        project.extra["cachePath"] = file("${defaultConfig["cache.dir"]}")
    } else {
        project.extra["cachePath"] = file("${project.extra["testWorkDir"]}/template-cache")
    }

    project.configure<uk.co.cacoethes.gradle.lazybones.LazybonesConventions> {
        installDir = file("${project.extra["cachePath"]}")
    }
}

//for a single test, you can run "gradle -DintegTest.single=<test name>"
tasks.register<Test>("integTest") {
    val mainSourceSet = sourceSets.main.get()
    val integTestSourceSet = sourceSets.named("integTest").get()

    dependsOn("installDist", "configureCachePath", "installAllTemplates")
    inputs.dir(files(mainSourceSet.output.classesDirs, mainSourceSet.output.resourcesDir))

    testClassesDirs = integTestSourceSet.output.classesDirs
    classpath = integTestSourceSet.runtimeClasspath
    shouldRunAfter("test")
    ignoreFailures = true

    systemProperty("integration.test", "true")
    systemProperty("lazybones.testWorkDir", "${project.extra["testWorkDir"]}")
    systemProperty("lazybones.installDir",
            tasks.named<Sync>("installDist").get().destinationDir.path)
    systemProperty("lzbtest.expected.version", version)

    // Allows us to disable tests that don't work on Drone.io, such as the
    // CreateFunctionalSpec feature test for --with-git.
    systemProperty("lazybones.config.file",
            file("${integTestSourceSet.output.resourcesDir}/test-config.groovy").absolutePath)

    if (project.extra["isCiBuild"] as Boolean) {
        systemProperty("drone.io", "true")
    } else {
        // Use the default cache location on the CI server, but a custom one
        // on local machines to avoid polluting the developer's own cache.
        systemProperty("lazybones.cache.dir", "${project.extra["cachePath"]}")
    }

    testClassesDirs = files(integTestSourceSet.output.classesDirs)
    classpath = integTestSourceSet.runtimeClasspath

    include("**/*Spec*")
    exclude("**/Abstract*Spec*")
}

tasks.register<Zip>("packageReports") {
    from("build/reports")
    archiveFileName.set("reports.zip")
    destinationDirectory.set(buildDir)
}

tasks.named("integTest").get().finalizedBy("packageReports")

//codenarc {
//    configFile = rootProject.file("codenarc.groovy")
//}
//codenarcMain.excludes = ["**/NameType.groovy"]
//codenarcTest.enabled = false
//codenarcIntegTest.enabled = false

/** Cobertura (Coverage) Configuration */
cobertura {
    coverageFormats = mutableSetOf("html", "xml")
    coverageSourceDirs = sourceSets.main.get().allSource.srcDirs
}

//distZip.dependsOn("test", "integTest")
//check.dependsOn("test", "integTest")

tasks.register<uk.co.cacoethes.gradle.tasks.BintrayGenericUpload>("uploadDist") {
    dependsOn("distZip")
    val distZip by tasks.existing(Zip::class)
    artifactFile = distZip.get().archiveFile.get().asFile
    artifactUrlPath = "lazybones/${project.version}/${distZip.get().archiveFileName}"
    repositoryName = "pledbrook/lazybones-templates"
    packageName = "lazybones"
    licenses = mutableListOf("Apache-2.0")
}

tasks.register("release").get().dependsOn("uploadDist", "sdkMajorRelease")
tasks.named("sdkMajorRelease").get().mustRunAfter("uploadDist")

// Lazy initialisation of uploadDist task so that not all build users need to
// set the repo.* project properties.
//
// We also lazily configure the cache directory system property when running
// the integration tests. This is to ensure that the test-tmpl template gets
// installed into the cache directory being used by the integration tests.

gradle.taskGraph.whenReady {
    val repoUsername = project.findProperty("repo.username")
    val repoApiKey = project.findProperty("repo.apiKey")
    if (hasTask(":lazybones-app:uploadDist") && Objects.nonNull(repoUsername) && Objects.nonNull(repoApiKey)) {
        tasks.named<uk.co.cacoethes.gradle.tasks.BintrayGenericUpload>("uploadDist") {
            username = "${repoUsername}"
            apiKey = "${repoApiKey}"
        }
    }

    val gvmConsumerKey = project.findProperty("gvm.consumerKey")
    val gvmConsumerToken = project.findProperty("gvm.consumerToken")
    if (hasTask(":lazybones-app:sdkMajorRelease") && Objects.nonNull(gvmConsumerKey) && Objects.nonNull(gvmConsumerToken)) {
        tasks.named<io.sdkman.vendors.tasks.SdkMajorRelease>("sdkMajorRelease") {
            consumerKey = "${gvmConsumerKey}"
            consumerToken = "${gvmConsumerToken}"
        }
    }
}
