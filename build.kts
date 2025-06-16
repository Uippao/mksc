#!/usr/bin/env kotlin

import java.io.File
import kotlin.system.exitProcess

val srcDir = File("src")
val srcFile = File(srcDir, "mksc.kt")
val buildDir = File("build")
val binaryName = "mksc"

fun ensureExists(file: File, isDir: Boolean = false) {
    if (!file.exists()) {
        if (isDir) file.mkdirs() else {
            System.err.println("Missing: ${file.path}")
            exitProcess(1)
        }
    }
    if (isDir && !file.isDirectory) {
        System.err.println("Expected directory: ${file.path}")
        exitProcess(1)
    }
    if (!isDir && !file.isFile) {
        System.err.println("Expected file: ${file.path}")
        exitProcess(1)
    }
}

fun detectHostTarget(): Pair<String, String> {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch")

    val targetOs = when {
        os.contains("linux") -> "linux"
        os.contains("mac") -> "macos"
        else -> {
            System.err.println("Unsupported host OS: $os")
            exitProcess(1)
        }
    }

    val targetArch = when (arch) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> {
            System.err.println("Unsupported architecture: $arch")
            exitProcess(1)
        }
    }

    return Pair(targetOs, targetArch)
}

fun compile(target: String): File {
    println("Compiling for $target...")

    val compile = ProcessBuilder(
        "kotlinc-native", srcFile.path,
        "-o", binaryName,
        "-target", target
    ).inheritIO().start()

    if (compile.waitFor() != 0) {
        System.err.println("Compilation failed for target: $target")
        exitProcess(1)
    }

    val kexeFile = File("$binaryName.kexe")
    if (!kexeFile.exists()) {
        System.err.println("Compiled binary not found.")
        exitProcess(1)
    }

    return kexeFile
}

fun zipBinary(file: File, zipName: String) {
    val zipFile = File(buildDir, zipName)
    if (zipFile.exists()) zipFile.delete()

        val zip = ProcessBuilder("zip", "-j", zipFile.absolutePath, file.absolutePath)
        .inheritIO().start()

        if (zip.waitFor() != 0) {
            System.err.println("Failed to create ZIP archive: $zipName")
            exitProcess(1)
        }

        println("Zipped: ${zipFile.name}")
}

fun main() {
    ensureExists(srcDir, isDir = true)
    ensureExists(srcFile)
    ensureExists(buildDir, isDir = true)

    val (os, arch) = detectHostTarget()
    val hostZipName = "mksc-${os}_${arch}.zip"

    val hostBinary = compile("host")
    val rootBinary = File(binaryName)
    if (rootBinary.exists()) rootBinary.delete()
        hostBinary.renameTo(rootBinary)
        ProcessBuilder("chmod", "+x", rootBinary.absolutePath).inheritIO().start().waitFor()
        zipBinary(rootBinary, hostZipName)

        val armBinary = compile("linux_arm64")
        zipBinary(armBinary, "mksc-linux_arm64.zip")
        armBinary.delete()
}

main()
