import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.net.URL

val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")

object Versions {
    const val scalaBinary = "2.12"
    const val lagom = "1.4.6" // "1.5.0-RC1"
    const val ktlint = "0.41.0"
    const val `kotlin-logging` = "1.6.22"
    const val config4k = "0.4.2"
    const val javassist = "3.21.0-GA"
    const val `play-soap` = "1.1.5"
    const val junit5 = "5.3.2"
    const val assertj = "3.11.1"
    const val jacoco = "0.8.2"
}
val lagomVersion = project.properties["lagomVersion"] as String? ?: Versions.lagom
val scalaBinaryVersion = project.properties["scalaBinaryVersion"] as String? ?: Versions.scalaBinary

plugins {
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.dokka") version "0.9.17"
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    `maven-publish`
    signing
    jacoco
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "1.8"
compileKotlin.kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"

dependencies {
    api(kotlin("stdlib-jdk8"))
    api("com.lightbend.lagom", "lagom-javadsl-server_$scalaBinaryVersion", lagomVersion)
    api("com.typesafe.play", "play-soap-client_$scalaBinaryVersion", Versions.`play-soap`)
    api("org.javassist", "javassist", Versions.javassist)
    api("io.github.microutils", "kotlin-logging", Versions.`kotlin-logging`)
    api("io.github.config4k", "config4k", Versions.config4k)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", Versions.junit5)
    testImplementation("org.junit.jupiter", "junit-jupiter-params", Versions.junit5)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", Versions.junit5)
    testImplementation("org.assertj", "assertj-core", Versions.assertj)
}

ktlint {
    version.set(Versions.ktlint)
    outputToConsole.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy("jacocoTestReport")
}

jacoco {
    toolVersion = Versions.jacoco
}
tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    classifier = "javadoc"
    from(tasks.dokka)
}

tasks.dokka {
    outputFormat = "javadoc"
    outputDirectory = "$buildDir/javadoc"
    jdkVersion = 8
    reportUndocumented = true
    impliedPlatforms = mutableListOf("JVM")
    externalDocumentationLink(
        delegateClosureOf<DokkaConfiguration.ExternalDocumentationLink.Builder> {
            url = URL("https://www.lagomframework.com/documentation/1.4.x/java/api/")
        }
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "${project.name}_$scalaBinaryVersion"
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
            pom {
                name.set("Taymyr: Lagom Soap Client")
                description.set("Lagom Soap Client")
                url.set("https://taymyr.org")
                organization {
                    name.set("Digital Economy League")
                    url.set("https://www.digitalleague.ru/")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("taymyr")
                        name.set("Taymyr Contributors")
                        email.set("contributors@taymyr.org")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/taymyr/lagom-soap-client.git")
                    developerConnection.set("scm:git:https://github.com/taymyr/lagom-soap-client.git")
                    url.set("https://github.com/taymyr/lagom-soap-client")
                    tag.set("HEAD")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    isRequired = isReleaseVersion
    sign(publishing.publications["maven"])
}
