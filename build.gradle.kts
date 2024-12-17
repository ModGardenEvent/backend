import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

plugins {
    application
    java
    idea
    alias(libs.plugins.idea.ext) apply true
}

group = "net.modgarden"
version = project.properties["version"].toString()

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
	withJavadocJar()
}

repositories {
    mavenCentral()
    maven {
        name = "Reposilite Snapshots"
        url = uri("https://repo.reposilite.com/snapshots")
    }
}

dependencies {
	implementation(libs.javalin)
	implementation(libs.jackson.databind)
	implementation(libs.logback)
}

tasks {
	val expandProps = mapOf(
		"version" to version
	)

	val processResourcesTasks = listOf("processResources", "processTestResources")

	assemble.configure {
		dependsOn(processResourcesTasks)
	}

	withType<ProcessResources>().matching { processResourcesTasks.contains(it.name) }.configureEach {
		inputs.properties(expandProps)
		filesMatching("landing.json") {
			expand(expandProps)
		}
	}
}

application {
    mainClass = "net.modgarden.backend.ModGardenBackend"
}

idea {
    project {
        settings.runConfigurations {
            create("Run", Application::class.java) {
                mainClass = "net.modgarden.backend.ModGardenBackend"
                moduleName = project.idea.module.name + ".main"
                includeProvidedDependencies = true
            }
        }
    }
}