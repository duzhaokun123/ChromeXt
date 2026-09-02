import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  id("com.android.application") version "9.4.0" apply false
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree(rootDir)
  include("*.gradle.kts", "app/*.gradle.kts")
  dependsOn(":app:ktfmtFormat")
}
