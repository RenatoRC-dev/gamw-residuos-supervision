plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val integracionAislada = providers.environmentVariable("GAMW_ANDROID_INTEGRACION").orNull == "true"
val objetivoFlutter = providers.gradleProperty("target").getOrElse("lib/main.dart").replace('\\','/')
val pruebaGps = Regex("(^|/)integration_test/slice4a_[^/]+\\.dart$").containsMatchIn(objetivoFlutter)
require(!pruebaGps || integracionAislada) {
    "Slice 4A: active GAMW_ANDROID_INTEGRACION=true; se bloquea construir tests sobre la aplicacion normal."
}

android {
    namespace = "bo.gob.warnes.gamw.gamw_mobile"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        // Solo el arnés opt-in: UID, SQLite y KeyStore separados de la app normal.
        applicationId = if (integracionAislada) "bo.gob.warnes.gamw.gamw_mobile.it" else "bo.gob.warnes.gamw.gamw_mobile"
        manifestPlaceholders["gamwAppLabel"] = if (integracionAislada) "GAMW - pruebas aisladas" else "gamw_mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion // Almacenamiento seguro Android (KeyStore / AES-GCM).
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}
