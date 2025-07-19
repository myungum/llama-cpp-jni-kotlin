plugins {
    kotlin("jvm") version "1.9.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.traycer.llama"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.traycer.llama.Main")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.named<JavaExec>("run") {
    // Set library path for JNI library
    systemProperty("java.library.path", "${projectDir}/../native/build")
    
    // Set working directory to project root for model access
    workingDir = projectDir.parentFile
    
    // Enable JNI debugging if needed
    jvmArgs("-Djava.awt.headless=true")
}

tasks.register("runWithModel") {
    group = "application"
    description = "Run the application with model path"
    dependsOn("run")
}

tasks.register<JavaExec>("runPromptSuite") {
    group = "application"
    description = "Run automated prompt suite tests"
    dependsOn("build")
    
    mainClass.set("com.traycer.llama.PromptSuite")
    classpath = sourceSets.main.get().runtimeClasspath
    
    // Set library path for JNI library
    systemProperty("java.library.path", "${projectDir}/../native/build")
    
    // Set working directory to project root for model access
    workingDir = projectDir.parentFile
    
    // Enable headless mode
    jvmArgs("-Djava.awt.headless=true")
}

// Configure Shadow JAR task
tasks.shadowJar {
    archiveBaseName.set("llama-app")
    archiveClassifier.set("all")
    archiveVersion.set("")
    
    manifest {
        attributes("Main-Class" to "com.traycer.llama.Main")
    }
    
    // Enable service file merging for proper dependency handling
    mergeServiceFiles()
}

// Add Deployment Tasks
tasks.register("packageDeployment") {
    dependsOn(tasks.shadowJar)
    
    doLast {
        val deployDir = project.rootDir.resolve("deploy")
        deployDir.mkdirs()
        
        // Copy fat JAR to deployment directory
        val shadowJarTask = tasks.shadowJar.get()
        val jarFile = shadowJarTask.archiveFile.get().asFile
        jarFile.copyTo(deployDir.resolve("llama-app-all.jar"), overwrite = true)
        
        println("Deployment artifacts copied to: ${deployDir.absolutePath}")
    }
}

tasks.register("smokeTest") {
    dependsOn("packageDeployment")
    
    doLast {
        val deployDir = project.rootDir.resolve("deploy")
        val smokeTestScript = deployDir.resolve("smoke_test.sh")
        
        if (smokeTestScript.exists()) {
            exec {
                workingDir = deployDir
                commandLine("bash", "smoke_test.sh")
            }
        } else {
            println("Smoke test script not found. Run full build first.")
        }
    }
}

// Update build dependencies
tasks.build {
    dependsOn(tasks.shadowJar)
}
