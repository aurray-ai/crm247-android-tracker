import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

group = providers.gradleProperty("crm247.group").orNull ?: "uk.co.aurray"
version = providers.gradleProperty("crm247.version").orNull ?: "0.1.0"

android {
    namespace = "ai.crm247.tracker"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
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

mavenPublishing {
    val hasSigningKey = !(
        providers.gradleProperty("signingInMemoryKey").orNull
            ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
    ).isNullOrBlank()
    val hasSigningPassword = !(
        providers.gradleProperty("signingInMemoryKeyPassword").orNull
            ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
    ).isNullOrBlank()

    coordinates(
        providers.gradleProperty("crm247.group").orNull ?: "uk.co.aurray",
        providers.gradleProperty("crm247.artifact").orNull ?: "tracker-android",
        project.version.toString(),
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    if (hasSigningKey && hasSigningPassword) {
        signAllPublications()
    }

    pom {
        name.set(providers.gradleProperty("crm247.pom.name").orNull ?: "CRM247 Android Tracker")
        description.set(
            providers.gradleProperty("crm247.pom.description").orNull
                ?: "Android SDK for CRM247 visitor and event tracking by Aurray"
        )
        url.set(
            providers.gradleProperty("crm247.pom.url").orNull
                ?: "https://github.com/aurray-ai/crm247-android-tracker"
        )

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
                name.set(providers.gradleProperty("crm247.pom.developer.name").orNull ?: "Aurray")
                email.set(
                    providers.gradleProperty("crm247.pom.developer.email").orNull
                        ?: "engineering@crm247.ai"
                )
            }
        }

        scm {
            url.set(
                providers.gradleProperty("crm247.pom.scm.url").orNull
                    ?: "https://github.com/aurray-ai/crm247-android-tracker"
            )
            connection.set(
                providers.gradleProperty("crm247.pom.scm.connection").orNull
                    ?: "scm:git:git://github.com/aurray-ai/crm247-android-tracker.git"
            )
            developerConnection.set(
                providers.gradleProperty("crm247.pom.scm.devConnection").orNull
                    ?: "scm:git:ssh://git@github.com:aurray-ai/crm247-android-tracker.git"
            )
        }
    }
}
