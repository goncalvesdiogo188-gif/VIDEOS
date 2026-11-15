// Módulo "nucleo": base de dados e simulação. Kotlin puro, testável sem telemóvel.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
}

// FERRAMENTA: volta a criar a base de dados fictícia (apaga edições a equipas e jogadores!).
//   ./gradlew :nucleo:gerarBaseDados
tasks.register<JavaExec>("gerarBaseDados") {
    group = "ferramentas"
    description = "Cria de raiz as equipas e os jogadores fictícios."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("pt.futebolcarreira.nucleo.ferramentas.GerarBaseDadosKt")
    args(rootProject.file("app/src/main/assets/dados").absolutePath)
}
