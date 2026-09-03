plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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
        versionCode = 115
        versionName = "1.15"
    }

    buildTypes {
        debug {
            // `applicationIdSuffix = ".debug"` retiré : le plugin google-services
            // résout l'application par son applicationId, et `google-services.json`
            // ne déclare que `com.osala.buzzmeplease`. Avec le suffixe, le build
            // debug échouait faute d'entrée correspondante dans le fichier.
            // Conséquence à connaître : debug et release ne peuvent plus être
            // installés côte à côte, l'un remplace l'autre. Sans risque de conflit
            // de signature, les deux étant signés avec la clé de debug.
            versionNameSuffix = "-debug"
            // Rien à désobfusquer en debug, et l'upload ralentirait chaque build.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
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
            // R8 obscurcit et élague ce build : sans l'envoi du mapping, les stack
            // traces arrivent illisibles dans Crashlytics. D'autant plus ici que les
            // règles ci-dessus laissent R8 renommer tout sauf le protocole.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
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
        // `BuildConfig.DEBUG` est une constante de compilation : la section
        // « Diagnostic » des réglages est ainsi éliminée du bytecode en release,
        // là où un test à l'exécution laisserait le bouton de crash volontaire
        // présent et joignable dans le dex publié.
        buildConfig = true
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

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // Fragment ne sert à rien ici : tout est en Compose sur une ComponentActivity. Il entre
    // quand même dans l'APK par la chaîne firebase-crashlytics -> play-services-tasks ->
    // play-services-basement, qui le réclame en 1.1.0 (2019). Faute d'appcompat pour remonter
    // cette résolution — CallMePlease en a un, pas ce projet — c'est bien cette version-là qui
    // était embarquée, et le Play Console la signalait comme SDK obsolète. La déclarer ici
    // aligne la résolution sur la version courante. À ne pas exclure : basement charge des
    // classes Fragment à l'exécution.
    implementation(libs.androidx.fragment)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
