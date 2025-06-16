@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
import kotlinx.cinterop.*
import platform.posix.*

fun printHelp() {
    println("""
    Usage: mksc <target> [output] [options]

    Creates a .desktop shortcut for the given file or folder.
    If no output path is given, creates [name].desktop in the current directory.

    Options:
    -h, --help       Show this help message
    -v, --version    Show version info
    -i, --icon       Specify custom icon name or path
    """.trimIndent())
}

fun printVersion() {
    println("mksc - Simple utility for creating shortcuts on Linux")
    println("v1.0.0")
    println("https://github.com/Uippao/mksc")
}

fun runCommand(command: String): String? {
    val pipe = popen(command, "r") ?: return null
    val output = StringBuilder()
    val buffer = ByteArray(1024)
    while (fgets(buffer.refTo(0), buffer.size, pipe) != null) {
        output.append(buffer.toKString())
    }
    pclose(pipe)
    return output.toString().trimEnd()
}

fun getMimeType(filePath: String): String? {
    val escaped = filePath.replace("\"", "\\\"")
    return runCommand("file --mime-type -b \"$escaped\"")
}

fun escapeExec(path: String): String =
"\"" + path.replace("\"", "\\\"") + "\""

fun getFileNameWithoutExtension(path: String): String {
    val file = path.substringAfterLast("/")
    val lastDot = file.lastIndexOf('.')
    return if (lastDot > 0) file.substring(0, lastDot) else file
}

fun fileExists(path: String): Boolean =
access(path, F_OK) == 0

fun isDirectory(path: String): Boolean {
    val statBuf = nativeHeap.alloc<stat>()
    val res = stat(path, statBuf.ptr)
    val result = res == 0 && (statBuf.st_mode and S_IFMT.toUInt() == S_IFDIR.toUInt())
    nativeHeap.free(statBuf)
    return result
}

fun readAllText(path: String): String? {
    val statBuf = nativeHeap.alloc<stat>()
    if (stat(path, statBuf.ptr) != 0) {
        nativeHeap.free(statBuf)
        return null
    }
    val size = statBuf.st_size.toInt()
    nativeHeap.free(statBuf)
    val file = fopen(path, "r") ?: return null
    val data = ByteArray(size + 1)
    fread(data.refTo(0), 1.convert(), size.convert(), file)
    fclose(file)
    return data.toKString()
}

fun writeAllText(path: String, text: String) {
    val file = fopen(path, "w") ?: return
    fputs(text, file)
    fclose(file)
}

fun copyFile(src: String, dst: String) {
    val inF = fopen(src, "r") ?: return
    val outF = fopen(dst, "w") ?: run { fclose(inF); return }
    val buf = ByteArray(4096)
    while (true) {
        val n = fread(buf.refTo(0), 1.convert(), buf.size.convert(), inF)
        if (n <= 0u) break
            fwrite(buf.refTo(0), 1.convert(), n.convert(), outF)
    }
    fclose(inF)
    fclose(outF)
}

fun chmod755(path: String) {
    chmod(path, 0b111_101_101u)
}

fun String.removePrefixIfHidden(): String =
if (startsWith('.')) removePrefix(".") else this

    fun main(args: Array<String>) {
        if (args.isEmpty() || args.contains("-h") || args.contains("--help")) {
            printHelp(); return
        }
        if (args.contains("-v") || args.contains("--version")) {
            printVersion(); return
        }

        val iconIdx = args.indexOfFirst { it == "-i" || it == "--icon" }
        val customIcon = iconIdx.takeIf { it != -1 }?.let { args.getOrNull(it + 1) }
        val positional = if (iconIdx != -1)
        args.filterIndexed { i, _ -> i != iconIdx && i != iconIdx + 1 }
        else
            args.toList()


        if (positional.isEmpty()) {
            println("Error: Missing target path")
            return
        }

        val rawTarget = positional[0]
        val target = realpath(rawTarget, null)?.toKString()
        ?: run { println("Error: Invalid target"); return }
        if (!fileExists(target)) {
            println("Error: Target not found: $rawTarget")
            return
        }

        val outputArg = positional.getOrNull(1)
        val baseNameRaw = getFileNameWithoutExtension(target)
        val baseName = outputArg?.let { baseNameRaw } ?: baseNameRaw.removePrefixIfHidden()
        val output = outputArg ?: "$baseName.desktop"
        if (fileExists(output)) {
            println("Error: Output exists: $output")
            return
        }

        val isDesktop = target.endsWith(".desktop", ignoreCase = true)
        val isDir = isDirectory(target)

        when {
            isDesktop -> makeDesktopShortcut(target, output, customIcon)
            isDir     -> makeFolderShortcut(target, output, customIcon)
            else      -> makeAppShortcut(target, output, customIcon)
        }

        chmod755(output)
        println("Shortcut created: $output")
    }

    fun makeDesktopShortcut(src: String, dst: String, icon: String?) {
        if (icon == null) {
            copyFile(src, dst)
            return
        }
        val text = readAllText(src)?.lines()?.toMutableList() ?: return
        val iconLine = "Icon=$icon"
        val idx = text.indexOfFirst { it.startsWith("Icon=", ignoreCase = true) }
        if (idx != -1) text[idx] = iconLine
            else {
                val hdr = text.indexOfFirst { it.trim() == "[Desktop Entry]" }
                .takeIf { it != -1 } ?: -1
                if (hdr >= 0) text.add(hdr + 1, iconLine) else text.add(iconLine)
            }
            writeAllText(dst, text.joinToString("\n", postfix = "\n"))
    }

    fun makeFolderShortcut(src: String, dst: String, icon: String?) {
        val name = src.substringAfterLast("/")
        val ico = icon ?: "folder"
        val entry = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Link")
            appendLine("Name=$name")
            appendLine("URL=file://$src")
            appendLine("Icon=$ico")
            appendLine("Terminal=false")
        }
        writeAllText(dst, entry)
    }

    fun makeAppShortcut(src: String, dst: String, icon: String?) {
        val name = src.substringAfterLast("/")
        val ico = icon ?: when {
            src.endsWith(".txt", ignoreCase = true) -> "text-plain"
            else -> getMimeType(src)?.replace("/", "-") ?: "applications-all"
        }
        val entry = buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Name=$name")
            appendLine("Exec=xdg-open \"${src.replace("\"", "\\\"")}\"")
            appendLine("Icon=$ico")
            appendLine("Terminal=false")
        }
        writeAllText(dst, entry)
    }
