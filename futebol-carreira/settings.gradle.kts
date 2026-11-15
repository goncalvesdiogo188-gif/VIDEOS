// Lista os módulos do projeto e os sítios de onde se descarregam as bibliotecas.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FutebolCarreira"
include(":app")     // a aplicação Android (ecrãs)
include(":nucleo")  // dados e simulação, em Kotlin puro (não depende do Android)
