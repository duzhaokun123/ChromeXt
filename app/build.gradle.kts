plugins {
  id("com.android.application")
  id("com.ncorti.ktfmt.gradle")
}

android {
  compileSdk = 35
  namespace = "org.matrix.chromext"

  defaultConfig {
    applicationId = "org.matrix.chromext"
    minSdk = 23
    targetSdk = 35
    versionCode = 16
    versionName = "3.8.3"

    buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
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
  implementation("org.luckypray:dexkit:2.2.0")
}
