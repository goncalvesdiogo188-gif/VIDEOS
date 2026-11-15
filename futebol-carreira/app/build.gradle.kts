// Módulo "app": a aplicação Android (ecrãs feitos com Jetpack Compose).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pt.futebolcarreira"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.futebolcarreira"
        minSdk = 26          // Android 8.0 ou mais recente
        targetSdk = 35
        versionCode = 2
        versionName = "0.2 (Fase 2)"
    }

    // Chave de TESTE, partilhada no repositório para as atualizações instalarem por cima
    // das versões anteriores. Para publicar na Play Store cria-se uma chave privada (Fase 12).
    signingConfigs {
        create("teste") {
            storeFile = rootProject.file("chaves/teste.keystore")
            storePassword = "futebol-teste"
            keyAlias = "teste"
            keyPassword = "futebol-teste"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("teste")
        }
        release {
            // Versão otimizada: APK pequeno e mais rápido.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("teste")
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
}

dependencies {
    implementation(project(":nucleo"))

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
}
