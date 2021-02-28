pluginManagement {
    repositories {
        // For development versions which are locally installed
        mavenLocal()
        // Public releases
        gradlePluginPortal()
    }
}

rootProject.name = "lazybones-ng"

// Due to the nature of the sub-project dependencies, the build of the Gradle
// plugin should be done separately and published (locally while under
// development) before building the app and templates.
include("templates")
include("app")
