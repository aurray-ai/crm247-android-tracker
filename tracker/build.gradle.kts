plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

group = providers.gradleProperty("crm247.group").orNull ?: "ai.crm247"
version = providers.gradleProperty("crm247.version").orNull ?: "0.1.0"

android {
    namespace = "ai.crm247.tracker"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = providers.gradleProperty("crm247.artifact").orNull ?: "tracker-android"
                version = project.version.toString()

                pom {
                    name.set(providers.gradleProperty("crm247.pom.name").orNull ?: "CRM247 Android Tracker")
                    description.set(
                        providers.gradleProperty("crm247.pom.description").orNull
                            ?: "Android SDK for CRM247 visitor and event tracking"
                    )
                    url.set(providers.gradleProperty("crm247.pom.url").orNull ?: "https://github.com/crm247/tracker-android")

                    licenses {
                        license {
                            name.set(providers.gradleProperty("crm247.pom.license.name").orNull ?: "MIT")
                            url.set(
                                providers.gradleProperty("crm247.pom.license.url").orNull
                                    ?: "https://opensource.org/licenses/MIT"
                            )
                        }
                    }

                    developers {
                        developer {
                            id.set(providers.gradleProperty("crm247.pom.developer.id").orNull ?: "crm247")
                            name.set(providers.gradleProperty("crm247.pom.developer.name").orNull ?: "CRM247")
                            email.set(
                                providers.gradleProperty("crm247.pom.developer.email").orNull
                                    ?: "engineering@crm247.ai"
                            )
                        }
                    }

                    scm {
                        url.set(providers.gradleProperty("crm247.pom.scm.url").orNull ?: "https://github.com/crm247/tracker-android")
                        connection.set(
                            providers.gradleProperty("crm247.pom.scm.connection").orNull
                                ?: "scm:git:git://github.com/crm247/tracker-android.git"
                        )
                        developerConnection.set(
                            providers.gradleProperty("crm247.pom.scm.devConnection").orNull
                                ?: "scm:git:ssh://git@github.com:crm247/tracker-android.git"
                        )
                    }
                }
            }
        }

        repositories {
            mavenLocal()

            val releasesUrl = providers.gradleProperty("crm247.maven.releasesUrl").orNull
                ?: System.getenv("CRM247_MAVEN_RELEASES_URL")
            val snapshotsUrl = providers.gradleProperty("crm247.maven.snapshotsUrl").orNull
                ?: System.getenv("CRM247_MAVEN_SNAPSHOTS_URL")
            val targetUrl = if (project.version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            val username = providers.gradleProperty("crm247.maven.username").orNull
                ?: System.getenv("CRM247_MAVEN_USERNAME")
            val password = providers.gradleProperty("crm247.maven.password").orNull
                ?: System.getenv("CRM247_MAVEN_PASSWORD")

            if (!targetUrl.isNullOrBlank()) {
                maven {
                    name = "crm247Remote"
                    url = uri(targetUrl)
                    if (!username.isNullOrBlank() || !password.isNullOrBlank()) {
                        credentials {
                            this.username = username
                            this.password = password
                        }
                    }
                }
            }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
        ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
    val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
        ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
    val signingKeyId = providers.gradleProperty("signingInMemoryKeyId").orNull
        ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId")
    val shouldSign = !signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()

    if (shouldSign) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(publishing.publications)
    }
}
