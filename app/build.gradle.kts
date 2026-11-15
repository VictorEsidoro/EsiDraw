plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.victoresidoro.esidraw"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.victoresidoro.esidraw"
        // 26 = java.time sem desugaring e ícones adaptativos sem fallback em PNG.
        minSdk = 26
        targetSdk = 37
        // O CI de release sobrescreve os dois (-PappVersionName=0.1.0-test.2 -PappVersionCode=<nº da execução>):
        // o APK mostra a versão exata e um build de teste novo sempre instala por cima do anterior.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.1.0"
    }

    // A chave nunca vai pro Git (*.keystore está no .gitignore). Sem ela, o release sai sem assinatura,
    // para o CI de testes compilar sem segredos.
    val keystoreFile = rootProject.file(System.getenv("KEYSTORE_PATH") ?: "android.keystore")
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    val keystoreAlias: String? = System.getenv("KEYSTORE_ALIAS")
    val signingAvailable = keystoreFile.exists() && !keystorePassword.isNullOrBlank() && !keystoreAlias.isNullOrBlank()

    signingConfigs {
        if (signingAvailable) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = System.getenv("KEYSTORE_KEY_PASSWORD") ?: keystorePassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(21)
}

// O esquema do banco é exportado para app/schemas e deve ser versionado no Git:
// ele é a base para validar migrações quando o banco mudar de versão.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
