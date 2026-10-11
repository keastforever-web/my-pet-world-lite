plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose"); }
android {
 namespace = "studio.cyberbeads.app"
 compileSdk { version = release(37) { minorApiLevel = 2 } }
 buildToolsVersion = "36.0.0"
 defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; applicationId = "studio.cyberbeads.app"; targetSdk = 37; versionCode = 21; versionName = "0.16.3"; }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { buildConfig = true; resValues = true; compose = true; }
 flavorDimensions += "edition"
 productFlavors {
  create("lite") { dimension="edition"; applicationIdSuffix=".lite"; buildConfigField("boolean","HAS_PARK","false"); resValue("string","app_name","我的宠物世界·伙伴版") }
 }
 splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64"); isUniversalApk = false } }
 lint { checkDependencies = true }
 // Maintainer-only upgrade signing. No keystore is bundled or required for contributors.
 providers.environmentVariable("PETWORLD_DEBUG_KEYSTORE").orNull?.let { path ->
  signingConfigs.getByName("debug") {
   storeFile = file(path)
   storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
  }
 }
 packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
 implementation(project(":core:model"))
 implementation(project(":core:imaging"))
 implementation(project(":core:data"))
 implementation(project(":core:ui"))
 implementation(project(":feature:studio"))
 implementation(project(":feature:library"))
 implementation(project(":feature:pet"))
 implementation(project(":feature:settings"))
 implementation(project(":feature:pixel"))
 implementation("androidx.core:core-ktx:1.19.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2")

 val bom = platform("androidx.compose:compose-bom:2026.08.00")
 implementation(bom); androidTestImplementation(bom)
 implementation("androidx.activity:activity-compose:1.13.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 debugImplementation("androidx.compose.ui:ui-tooling")

 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}

