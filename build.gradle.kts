import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  id("com.android.application") version "9.4.1" apply false
  id("androidx.room3") version "3.0.3" apply false
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
  id("com.google.devtools.ksp") version "2.3.12"
}

tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree(rootDir)
  include("*.gradle.kts", "app/*.gradle.kts")
  dependsOn(":app:ktfmtFormat")
}
