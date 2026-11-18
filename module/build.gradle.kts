import org.apache.tools.ant.filters.FixCrLfFilter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.library)
}

apply(from = rootProject.file("module.gradle.kts"))

val moduleLibraryName = extra["moduleLibraryName"] as String
val magiskModuleId = extra["magiskModuleId"] as String
val moduleName = extra["moduleName"] as String
val moduleAuthor = extra["moduleAuthor"] as String
val moduleDescription = extra["moduleDescription"] as String
val moduleVersion = extra["moduleVersion"] as String
val moduleVersionCode = extra["moduleVersionCode"] as Int

android {
    namespace = "com.example.zygisk"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DMODULE_NAME=$moduleLibraryName"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

val adbFileProvider = androidComponents.sdkComponents.adb
val outDir = rootProject.layout.projectDirectory.dir("out")

androidComponents.onVariants { variant ->
    val variantLowered = variant.name.lowercase()
    val variantCapped = variantLowered.replaceFirstChar { it.uppercase() }

    val zipName = "${magiskModuleId.replace('_', '-')}-$moduleVersion-$variantLowered.zip"
    val magiskDir = outDir.dir("magisk_module_$variantLowered")
    val templateDir = rootProject.layout.projectDirectory.dir("template/magisk_module")
    val strippedLibsDir = layout.buildDirectory.dir(
        "intermediates/stripped_native_libs/${variant.name}/strip${variantCapped}DebugSymbols/out/lib"
    )

    val prepareTask = tasks.register<Sync>("prepareMagiskFiles$variantCapped") {
        dependsOn("strip${variantCapped}DebugSymbols")
        into(magiskDir)
        from(templateDir) {
            exclude("module.prop")
        }
        from(templateDir) {
            include("module.prop")
            expand(
                mapOf(
                    "id" to magiskModuleId,
                    "name" to moduleName,
                    "version" to moduleVersion,
                    "versionCode" to moduleVersionCode.toString(),
                    "author" to moduleAuthor,
                    "description" to moduleDescription,
                )
            )
            filter(
                mapOf("eol" to FixCrLfFilter.CrLf.newInstance("lf")),
                FixCrLfFilter::class.java,
            )
        }
        from(strippedLibsDir) {
            into("lib")
        }
        doLast {
            val magiskRoot = magiskDir.asFile
            val zygiskDir = magiskRoot.resolve("zygisk").apply { mkdirs() }
            val libDir = magiskRoot.resolve("lib")
            if (libDir.exists()) {
                libDir.listFiles()?.filter { it.isDirectory }?.forEach { abiDir ->
                    val src = abiDir.resolve("lib$moduleLibraryName.so")
                    if (src.exists()) {
                        Files.move(
                            src.toPath(),
                            zygiskDir.resolve("${abiDir.name}.so").toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                }
                libDir.deleteRecursively()
            }
        }
    }

    val zipTask = tasks.register<Zip>("zip$variantCapped") {
        dependsOn(prepareTask)
        from(magiskDir)
        archiveFileName.set(zipName)
        destinationDirectory.set(outDir)
    }

    val pushTask = tasks.register<Exec>("push$variantCapped") {
        dependsOn(zipTask)
        workingDir(outDir.asFile)
        doFirst {
            executable = adbFileProvider.get().asFile.absolutePath
            args("push", zipName, "/data/local/tmp/")
        }
    }

    val flashTask = tasks.register<Exec>("flash$variantCapped") {
        dependsOn(pushTask)
        val rootMethod = (findProperty("root") as? String)?.lowercase() ?: "apatch"
        val installCmd = when (rootMethod) {
            "magisk" -> "magisk --install-module /data/local/tmp/$zipName"
            "ksu", "kernelsu" -> "ksud module install /data/local/tmp/$zipName"
            "ap", "apatch" -> "/data/adb/apd module install /data/local/tmp/$zipName"
            else -> throw GradleException(
                "Unsupported root method: $rootMethod. Supported: magisk, ksu/kernelsu, ap/apatch"
            )
        }
        doFirst {
            executable = adbFileProvider.get().asFile.absolutePath
            args("shell", "su", "-c", installCmd)
        }
    }

    tasks.register<Exec>("flashAndReboot$variantCapped") {
        dependsOn(flashTask)
        doFirst {
            executable = adbFileProvider.get().asFile.absolutePath
            args("shell", "reboot")
        }
    }

    tasks.matching { it.name == "assemble$variantCapped" }.configureEach {
        finalizedBy(zipTask)
    }
}
