plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.spotless)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

android {
  namespace = "com.jasonarends.forklore"
  compileSdk = 37
  compileSdkMinor = 2
  defaultConfig {
    applicationId = "com.jasonarends.forklore"
    minSdk = 30
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  // Robolectric runs Room against real SQLite on the JVM, so the acceptance suite runs in
  // CI with no emulator. The emulated SDK is set in src/test/resources/robolectric.properties.
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Robolectric's FileDescriptor interceptor calls jdk.internal.access.SharedSecrets,
      // which java.base does not export. Without this the sandbox fails to start on JDK 21
      // with "Failed to interact with raw FileDescriptor internals".
      all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(21)
}

// Exported schemas are committed (app/schemas) so migrations can be tested against the
// real previous version rather than a hand-written guess. The Room plugin registers the
// directory as a proper task input, which the raw KSP argument does not.
room { schemaDirectory("$projectDir/schemas") }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Persistence
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.androidx.room.testing)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  // Compose component tests run under Robolectric, so they gate `./gradlew test` with no device.
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  // ui-test-junit4 drags in espresso-core 3.5.0, which reflects on InputManager.getInstance().
  // That method is gone from the SDK 37 Robolectric image, so without this pin every Compose
  // test dies at rule setup.
  testImplementation(libs.androidx.test.espresso.core)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktfmt().googleStyle()
  }
  kotlinGradle {
    target("*.kts")
    ktfmt().googleStyle()
  }
}
