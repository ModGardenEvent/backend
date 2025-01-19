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
    maven("https://repo.glaremasters.me/repository/public/") {
        name = "GlareMasters"
    }

}

dependencies {
    implementation(libs.dfu)
	implementation(libs.javalin)
    implementation(libs.logback)
    implementation(libs.sqlite)
    implementation(libs.snowflakeid)
	implementation(libs.dotenv)
	implementation(libs.jwt.api)
	implementation(libs.jwt.impl)
	implementation(libs.jwt.gson)
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
                workingDirectory = "${rootProject.projectDir}/run"
                mainClass = "net.modgarden.backend.ModGardenBackend"
                moduleName = project.idea.module.name + ".main"
                includeProvidedDependencies = true
            }
        }
    }
}
