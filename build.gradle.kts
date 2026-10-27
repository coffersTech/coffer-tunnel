plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "tech.coffers"
// 支持外部传入 -PpluginVersion 动态指定发布版本号，默认 1.0.0
version = (project.findProperty("pluginVersion") as? String)?.takeIf { it.isNotBlank() } ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // 内置轻量 JSON 支持，用于配置的导入与备份导出
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

// IntelliJ Platform 配置
intellij {
    // 兼容基线设为 2023.1.5，确保向下兼容 2023 全系列并在 2024.1+ 正常运行
    version.set("2023.1.5")
    type.set("IC")
    downloadSources.set(true)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    }

    patchPluginXml {
        // 向下兼容至 2023.1 (Build 231)，向上兼容至 2024.1+ (Build 241+) 及后续版本
        sinceBuild.set("231")
        untilBuild.set("251.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        // 沙盒启动最大堆内存配置与启动优化
        maxHeapSize = "2g"
        // 缩短 macOS shell 环境加载等待，避免 Conda 初始化阻塞沙盒启动
        systemProperty("idea.shell.env.loader.timeout", "3000")
    }
}
