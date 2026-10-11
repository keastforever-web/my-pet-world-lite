plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose"); }
android {
 namespace = "studio.cyberbeads.feature.pixel"
 compileSdk { version = release(37) { minorApiLevel = 2 } }
 buildToolsVersion = "36.0.0"
 defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { buildConfig = false; compose = true; }
 packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
 implementation(project(":core:model"))
 implementation(project(":core:imaging"))
 implementation(project(":core:data"))
 implementation(project(":core:ui"))
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
}

