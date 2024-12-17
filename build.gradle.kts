plugins {
    id("java")
}

group = "net.modgarden"
version = project.properties["version"].toString()

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
	withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
	implementation(libs.javalin.get())
	implementation(libs.jackson.databind.get())
	implementation(libs.logback.get())
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
