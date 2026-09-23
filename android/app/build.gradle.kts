import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val releaseProperties = Properties().apply {
    val file = rootProject.file("../.secrets/release.env")
    if (file.exists()) {
        file.readLines().filter { it.contains('=') }.forEach {
            val (key, value) = it.split('=', limit = 2)
            setProperty(key, value)
        }
    }
}

val zhitiBaseUrl = (
    releaseProperties.getProperty("ZHITI_BASE_URL")
        ?: providers.gradleProperty("zhitiBaseUrl").orNull
        ?: "https://example.invalid"
).trimEnd('/')

val releaseSigningKeys = listOf(
    "ZHITI_KEYSTORE",
    "ZHITI_KEYSTORE_PASSWORD",
    "ZHITI_KEY_ALIAS",
    "ZHITI_KEY_PASSWORD",
)
val releaseSigningConfigured = releaseSigningKeys.all { !releaseProperties.getProperty(it).isNullOrBlank() }

val localTrustDirectory = rootProject.file("../.secrets")
val sampleTrustDirectory = rootProject.file("config-sample")
val zhitiTrustDirectory = providers.gradleProperty("zhitiTrustDir").orNull?.let { file(it) }
    ?: localTrustDirectory.takeIf {
        it.resolve("ca.crt").isFile && it.resolve("content-ed25519-public.pem").isFile
    }
    ?: sampleTrustDirectory
val generatedTrustResources = layout.buildDirectory.dir("generated/zhiti-trust-res")
val prepareZhitiTrust by tasks.registering(Copy::class) {
    from(zhitiTrustDirectory) {
        include("ca.crt", "content-ed25519-public.pem")
        rename("ca.crt", "zhiti_ca.crt")
        rename("content-ed25519-public.pem", "content_signing_public.pem")
    }
    into(generatedTrustResources.map { it.dir("raw") })
}

android {
    namespace = "com.xiaoyunduo.zhiti"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xiaoyunduo.zhiti"
        minSdk = 31
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"
        buildConfigField("String", "ZHITI_BASE_URL", "\"$zhitiBaseUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseProperties.getProperty("ZHITI_KEYSTORE"))
                storePassword = releaseProperties.getProperty("ZHITI_KEYSTORE_PASSWORD")
                keyAlias = releaseProperties.getProperty("ZHITI_KEY_ALIAS")
                keyPassword = releaseProperties.getProperty("ZHITI_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").res.srcDir(generatedTrustResources.get().asFile)
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

tasks.named("preBuild") { dependsOn(prepareZhitiTrust) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.86")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
