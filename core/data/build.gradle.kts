plugins { id("com.android.library"); }
android {
 namespace = "studio.cyberbeads.core.data"
 compileSdk { version = release(37) { minorApiLevel = 2 } }
 buildToolsVersion = "36.0.0"
 defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { buildConfig = false;  }
 packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
 implementation(project(":core:model"))
 implementation(project(":core:imaging"))
 implementation("androidx.core:core-ktx:1.19.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2")
}
