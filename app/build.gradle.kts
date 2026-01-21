import java.io.ByteArrayOutputStream
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.stocksignal"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.stocksignal"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    packaging {
        resources {
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
    assetPacks += listOf(":gemma3_270m_model")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    
    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // CSV Parsing
    implementation(libs.commons.csv)
    
    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Charts
    implementation(libs.vico.core)
    implementation(libs.vico.compose.m3)

    // HTML parsing
    implementation(libs.jsoup)

    // ML Kit GenAI (prompt)
    implementation(libs.mlkit.genai.prompt)
    // MediaPipe LLM Inference (local model)
    implementation(libs.mediapipe.tasks.genai)
    // Play Asset Delivery for offline model fallback
    implementation(libs.play.asset.delivery)
    
    // Core library desugaring for Java 8+ APIs on older Android versions
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.json)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}

// Updated to Gemma-3 1B for better translation quality
val localModelFile = rootProject.file("gemma3-1b-it-int4-kv1280.task")
val localModelDevicePath = "files/llm/gemma3-1b-it-int4-kv1280.task"
val localModelTempPath = "/data/local/tmp/gemma3-1b-it-int4-kv1280.task"
val localModelExpectedBytes = 657_000_000L
val localModelAssetDir = rootProject.file("gemma3_270m_model/src/main/assets")
val localModelAssetFile = rootProject.file("gemma3_270m_model/src/main/assets/gemma3-1b-it-int4-kv1280.task")
val appPackageName = "com.example.stocksignal"
val adbExecutable = run {
    val propsFile = rootProject.file("local.properties")
    val props = Properties()
    if (propsFile.exists()) {
        propsFile.inputStream().use { props.load(it) }
    }
    val sdkDir = props.getProperty("sdk.dir")
    val adbName = if (System.getProperty("os.name").lowercase().contains("win")) {
        "adb.exe"
    } else {
        "adb"
    }
    if (sdkDir.isNullOrBlank()) {
        adbName
    } else {
        val adbPath = rootProject.file("$sdkDir/platform-tools/$adbName")
        if (adbPath.exists()) adbPath.absolutePath else adbName
    }
}

fun execForOutput(vararg args: String): Pair<Int, String> {
    val output = ByteArrayOutputStream()
    val result = exec {
        commandLine(*args)
        isIgnoreExitValue = true
        standardOutput = output
        errorOutput = output
    }
    return result.exitValue to output.toString().trim()
}

tasks.register("pushLocalTranslationModel") {
    group = "verification"
    description = "Pushes the repo-root LLM model into app files before instrumentation tests."
    dependsOn("installDebug")
    doLast {
        if (!localModelFile.exists()) {
            throw GradleException(
                "Local model file not found at ${localModelFile.absolutePath}. " +
                    "Place gemma3-1b-it-int4-kv1280.task at repo root or let the app download it."
            )
        }

        if (!localModelAssetDir.exists() && !localModelAssetDir.mkdirs()) {
            throw GradleException(
                "Failed to create asset pack directory at ${localModelAssetDir.absolutePath}."
            )
        }
        val assetSize = localModelAssetFile.takeIf { it.exists() }?.length()
        if (assetSize != localModelExpectedBytes) {
            copy {
                from(localModelFile)
                into(localModelAssetDir)
            }
            logger.lifecycle(
                "Copied local model into asset pack at ${localModelAssetFile.absolutePath}."
            )
        }

        val (lsExit, lsOutput) = execForOutput(
            adbExecutable,
            "shell",
            "run-as",
            appPackageName,
            "ls",
            "-l",
            localModelDevicePath
        )
        if (lsExit == 0) {
            val line = lsOutput.lineSequence().lastOrNull().orEmpty()
            val size = line.split(Regex("\\s+")).getOrNull(4)?.toLongOrNull()
            if (size == localModelExpectedBytes) {
                logger.lifecycle(
                    "Local model already present on device at $localModelDevicePath ($size bytes)."
                )
                return@doLast
            }
            logger.lifecycle(
                "Local model present but size mismatch (${size ?: "unknown"} bytes). Re-pushing."
            )
        }

        exec { commandLine(adbExecutable, "shell", "run-as", appPackageName, "mkdir", "-p", "files/llm") }
        exec { commandLine(adbExecutable, "push", localModelFile.absolutePath, localModelTempPath) }
        exec {
            commandLine(
                adbExecutable,
                "shell",
                "run-as",
                appPackageName,
                "cp",
                localModelTempPath,
                localModelDevicePath
            )
        }
        exec { commandLine(adbExecutable, "shell", "rm", "-f", localModelTempPath) }
    }
}

tasks.matching { it.name == "connectedDebugAndroidTest" }
    .configureEach { dependsOn("pushLocalTranslationModel") }
