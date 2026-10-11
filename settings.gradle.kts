pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "PetWorldLite"
include(":app", ":core:model", ":core:imaging", ":core:data", ":core:ui", ":feature:studio", ":feature:library", ":feature:pet", ":feature:settings", ":feature:pixel")
