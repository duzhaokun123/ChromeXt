plugins {
  id("com.android.application")
  id("com.ncorti.ktfmt.gradle")
  id("com.google.devtools.ksp")
}

android {
  compileSdk = 37
  namespace = "org.matrix.chromext"

  defaultConfig {
    applicationId = "io.github.duzhaokun123.chromeyt"
    minSdk = 23
    targetSdk = 37
    versionCode = 17
    versionName = "4.0.0"

    buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    buildConfigField("String", "SCRIPT_HANDLER_NAME", "\"ChromeYt\"")
  }

  buildFeatures { buildConfig = true }

  buildTypes {
    release {
      isShrinkResources = true
      isMinifyEnabled = true
      proguardFiles("proguard-rules.pro")
    }
  }

  androidResources {
    additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x42")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  lint {
    disable +=
        listOf(
            "Internationalization",
            "UnsafeIntentLaunch",
            "SetJavaScriptEnabled",
            "UnspecifiedRegisterReceiverFlag",
            "Usability:Icons",
        )
  }
}

dependencies {
  compileOnly("de.robv.android.xposed:api:82")
  implementation("org.luckypray:dexkit:2.3.0")
  implementation("net.bytebuddy:byte-buddy-android:1.18.14")
  implementation("androidx.annotation:annotation:1.11.0")
  implementation("com.github.iamironz:unsafe:0.0.6")
  implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("androidx.room3:room3-runtime:3.0.3")
  ksp("androidx.room3:room3-compiler:3.0.3")
  implementation("androidx.core:core-ktx:1.19.1")
  implementation("io.ktor:ktor-client-core:3.6.0")
  implementation("io.ktor:ktor-client-android:3.6.0")
  implementation("androidx.browser:browser:1.10.0")
}