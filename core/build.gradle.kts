import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        explicitApi()
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.gson)
}

// Accuracy against the labelled cases in benchmark/data (benchmark/LABELS.md). Not part of the ship gate: misses are expected.
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs the rules over benchmark/data and writes benchmark/results."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sweepiesoftie.earmark.core.BenchmarkKt")
    // -PbenchmarkRoot=benchmark/holdout runs the held-out cases; -PrulesOnly leaves the message model out.
    args(rootProject.file(project.findProperty("benchmarkRoot")?.toString() ?: "benchmark").path, if (project.hasProperty("rulesOnly")) "rules" else "all")
}

// Trains MessageModel on model/data and writes its weights to src/main/resources (model/README.md). Not the benchmark's data.
tasks.register<JavaExec>("trainModel") {
    group = "build"
    description = "Trains the message model on model/data and writes message-model.bin."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.sweepiesoftie.earmark.core.TrainModelKt")
    args(rootProject.projectDir.path)
}
