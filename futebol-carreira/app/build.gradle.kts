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
        versionCode = 12
        versionName = "0.12 (Fase 12)"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Testes dos ecrãs num telemóvel simulado (Robolectric), que também guardam capturas de ecrã.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "2g"
                it.testLogging {
                    events("passed", "failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}

dependencies {
    implementation(project(":nucleo"))

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    // Só para os testes dos ecrãs (não entram no APK).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")   // declara a ComponentActivity que os testes de ecrã usam
}
