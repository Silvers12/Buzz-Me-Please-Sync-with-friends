plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.osala.BuzzMePlease"
    compileSdk = 37

    // Deux langues embarquées, pas les cent-quarante d'AndroidX : l'application pèse moins.
    androidResources {
        localeFilters += listOf("fr", "en")
    }

    defaultConfig {
        applicationId = "com.osala.buzzmeplease"
        minSdk = 26
        targetSdk = 36
        versionCode = 114
        versionName = "1.14"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Élagage et obscurcissement : le Play Console mesure ce dernier, et une part de
            // zéro pèse sur la visibilité de la fiche. Les règles de proguard-rules.pro gardent
            // ce que R8 ne peut pas deviner — les sérialiseurs du protocole et les noms
            // d'énumération qui voyagent en clair dans le JSON.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Lets `./gradlew assembleRelease` produce an installable APK without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Les sons sont lus par un descripteur de fichier : compressés dans l'APK, ils
        // seraient illisibles tels quels.
        noCompress += listOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
