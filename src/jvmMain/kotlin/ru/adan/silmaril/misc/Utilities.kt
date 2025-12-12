package ru.adan.silmaril.misc

import ru.adan.silmaril.model.Creature
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream

fun unzipFile(zipFilePath: String, destDirectory: String) {
    val destDir = File(destDirectory)
    if (!destDir.exists()) destDir.mkdirs()

    ZipInputStream(FileInputStream(zipFilePath)).use { zipStream ->
        var entry = zipStream.nextEntry
        while (entry != null) {
            val entryPath = destDir.toPath().resolve(entry.name)
            if (entry.isDirectory) {
                Files.createDirectories(entryPath)
            } else {
                Files.createDirectories(entryPath.parent)
                FileOutputStream(entryPath.toFile()).use { outputStream ->
                    zipStream.copyTo(outputStream)
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.closeEntry()
    }
}

fun getOperatingSystem(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("win") -> "Windows"
        osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> "Linux"
        osName.contains("mac") -> "MacOS"
        else -> "Unknown"
    }
}

/**
 * On Windows: C:\Users\<username>\Documents\Silmaril\
 * On Linux: ~/Documents/Silmaril/
 * On MacOS: ~/Documents/Silmaril/
 * On Android in File Transfer mode: Android/data/ru.adan.silmaril/files/Silmaril/
 * On Android in ADB: /storage/emulated/0/Android/data/ru.adan.silmaril/files/Silmaril/
 */
fun getProgramDirectory(): String {
    // Use platform-specific getDocumentsDirectory() which handles Android vs Desktop paths
    val programPath = ru.adan.silmaril.platform.getDocumentsDirectory()

    // Ensure the program directory exists
    val programDir = File(programPath)
    if (!programDir.exists()) {
        programDir.mkdirs() // create all non-existent parent directories
    }

    return programPath
}

fun getAdanMapDataDirectory(): String {
    val userHome = System.getProperty("user.home")
    return Paths.get(userHome, "Documents", "Adan client", "Maps", "ZoneVisits").toString()
}

fun getSilmarilMapDataDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "maps").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getProfileDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "profiles").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getTriggersDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "triggers").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getDslScriptsDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "dsl").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getAliasesDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "aliases").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getSubstitutesDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "substitutes").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getHotkeysDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "hotkeys").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun getLoresDirectory(): String {
    val path = Paths.get(getProgramDirectory(), "lores").toString()
    val dir = File(path)
    if (!dir.exists())
        dir.mkdirs()
    return path
}

fun String.capitalized(): String {
    return this.replaceFirstChar { it.uppercaseChar() }
}

fun MatchGroupCollection.getOrNull(name: String): MatchGroup? {
    // This is the key: Wrap the potentially crashing call in a try-catch block.
    return try {
        this[name]
    } catch (e: IllegalArgumentException) {
        null
    }
}

// Nullable version (no fallback)
inline fun <reified T : Enum<T>> enumValueOfIgnoreCaseOrNull(name: String?): T? {
    if (name.isNullOrBlank()) return null
    val n = name.trim()
    return enumValues<T>().firstOrNull { it.name.equals(n, ignoreCase = true) }
}

fun formatMem(seconds: Int): String {
    val minutes = (seconds / 60).toString().padStart(2, '0')
    val remainingSeconds = (seconds % 60).toString().padStart(2, '0')
    return "$minutes:$remainingSeconds"
}

fun currentTime() : String {
    val currentTime = LocalTime.now()
    // Define the desired format
    // HH: 24-hour format (00-23)
    // mm: minutes with leading zero (00-59)
    // ss: seconds with leading zero (00-59)
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    return currentTime.format(formatter)
}

fun getCorrectTransitionWord(count: Int): String {
    // First, check for the special cases of 11-14, which always use the genitive plural.
    val lastTwoDigits = count % 100
    if (lastTwoDigits in 11..14) {
        return "переходов"
    }

    // Then, check the last digit of the number.
    val lastDigit = count % 10
    return when (lastDigit) {
        1 -> "переход"
        2, 3, 4 -> "перехода"
        else -> "переходов" // For 0, 5, 6, 7, 8, 9
    }
}

// Some strings in map data are russian, but have typos, where a latin letter is used instead of cyrillic. This object will fix it.
object CyrillicFixer {
    // Latin -> Cyrillic lookalike mappings (as code points)
    private val latinToCyrillic: Map<Int, Int> = mapOf(
        'A'.code to 'А'.code, 'a'.code to 'а'.code,
        'B'.code to 'В'.code,
        'C'.code to 'С'.code, 'c'.code to 'с'.code,
        'E'.code to 'Е'.code, 'e'.code to 'е'.code,
        'H'.code to 'Н'.code,
        'K'.code to 'К'.code,
        'M'.code to 'М'.code,
        'O'.code to 'О'.code, 'o'.code to 'о'.code,
        'P'.code to 'Р'.code, 'p'.code to 'р'.code,
        'T'.code to 'Т'.code,
        'X'.code to 'Х'.code, 'x'.code to 'х'.code,
        'y'.code to 'у'.code
    )

    private fun hasScript(text: String, script: Character.UnicodeScript): Boolean {
        val it = text.codePoints().iterator()
        while (it.hasNext()) {
            if (Character.UnicodeScript.of(it.nextInt()) == script) return true
        }
        return false
    }

    fun hasCyrillic(text: String) = hasScript(text, Character.UnicodeScript.CYRILLIC)
    fun hasLatin(text: String) = hasScript(text, Character.UnicodeScript.LATIN)

    // True if the string contains both Cyrillic and Latin
    fun containsLatinInCyrillicContext(text: String) =
        hasCyrillic(text) && hasLatin(text)

    // Replace only safe Latin lookalikes with their Cyrillic counterparts
    fun fixLatinHomoglyphsInRussian(text: String): String {
        if (!hasCyrillic(text)) return text
        val sb = StringBuilder(text.length)
        val it = text.codePoints().iterator()
        while (it.hasNext()) {
            val cp = it.nextInt()
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) {
                sb.appendCodePoint(latinToCyrillic[cp] ?: cp)
            } else {
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }
}

/** Utility functions that help get target by name like "part1.part2.part3" */
val debugTokenization = false
fun tokenizeQuery(q: String, ignoreCase: Boolean): List<String> {
    if (debugTokenization) println("\n>>> tokenizeQuery(q='$q', ignoreCase=$ignoreCase)")
    if (debugTokenization) println("q codepoints: ${showCodePoints(q)}")

    // Do the same normalization as tokenizeName
    val replaced = q.replace(Regex("""[^\p{L}\p{N}]+"""), " ")
    if (debugTokenization) println("after non-letter/digit -> ' $replaced '  codepoints: ${showCodePoints(replaced)}")
    val trimmed = replaced.trim()
    if (debugTokenization) println("trimmed -> '$trimmed'")
    val tokens = trimmed.split(Regex("""\s+""")).filter(String::isNotEmpty)
    if (debugTokenization) println("split on \\s+ -> tokens=${tokens.joinToString(prefix = "[", postfix = "]")}  details=${tokens.map { showCodePoints(it) }}")

    val out = if (ignoreCase) tokens.map { it.lowercase(Locale.ROOT) } else tokens
    if (debugTokenization) if (ignoreCase) { println("after lowercase -> ${out.joinToString(prefix = "[", postfix = "]")}  details=${out.map { showCodePoints(it) }}") }

    return out
}

fun tokenizeName(name: String, ignoreCase: Boolean): List<String> {
    if (debugTokenization) println("\n>>> tokenizeName(name='$name', ignoreCase=$ignoreCase)")
    if (debugTokenization) println("name codepoints: ${showCodePoints(name)}")
    val replaced = name.replace(Regex("""[^\p{L}\p{N}]+"""), " ")
    if (debugTokenization) println("after non-letter/digit -> ' $replaced '  codepoints: ${showCodePoints(replaced)}")
    val trimmed = replaced.trim()
    if (debugTokenization) println("trimmed -> '$trimmed'")
    val tokens = trimmed.split(Regex("""\s+""")).filter(String::isNotEmpty)
    if (debugTokenization) println("split on \\s+ -> tokens=${tokens.joinToString(prefix = "[", postfix = "]")}  details=${tokens.map { showCodePoints(it) }}")
    val out = if (ignoreCase) tokens.map { it.lowercase(Locale.ROOT) } else tokens
    if (debugTokenization) if (ignoreCase) { println("after lowercase -> ${out.joinToString(prefix = "[", postfix = "]")}  details=${out.map { showCodePoints(it) }}") }
    return out
}

fun matchesSequentialPrefixes(words: List<String>, parts: List<String>): Boolean {
    if (debugTokenization) println("\n>>> matchesSequentialPrefixes(words=${words}, parts=${parts})")
    if (debugTokenization) println("words details: ${words.map { showCodePoints(it) }}")
    if (debugTokenization) println("parts details: ${parts.map { showCodePoints(it) }}")

    if (parts.size > words.size) {
        if (debugTokenization) println("parts.size (${parts.size}) > words.size (${words.size}) -> false")
        return false
    }
    val lastStart = words.size - parts.size
    if (debugTokenization) println("lastStart=$lastStart (will try start in 0..$lastStart)")

    for (start in 0..lastStart) {
        if (debugTokenization) println(" start=$start -> checking window [${start}..${start + parts.size - 1}]")
        var ok = true
        for (i in parts.indices) {
            val w = words[start + i]
            val p = parts[i]
            val starts = w.startsWith(p)
            if (debugTokenization) println("   compare i=$i  word='$w' ${showCodePoints(w)}  part='$p' ${showCodePoints(p)}  -> startsWith=$starts")
            if (!starts) {
                ok = false
                if (debugTokenization) println("   mismatch at i=$i -> break")
                break
            }
        }
        if (ok) {
            if (debugTokenization) println(" window starting at $start matched -> return true")
            return true
        } else {
            if (debugTokenization) println(" window starting at $start did not match")
        }
    }
    if (debugTokenization) println("no window matched -> return false")
    return false
}

fun filterCreatures(creatures: List<Creature>, query: String, ignoreCase: Boolean = true): List<Creature> {
    if (debugTokenization) println("=== filterCreatures ===")
    if (debugTokenization) println("query='$query'  ignoreCase=$ignoreCase  creatures.size=${creatures.size}")
    val parts = tokenizeQuery(query, ignoreCase)
    if (debugTokenization) println("tokenizeQuery -> parts=${parts.joinToString(prefix = "[", postfix = "]")}  details=${parts.map { showCodePoints(it) }}")

    if (parts.isEmpty()) {
        if (debugTokenization) println("parts is empty -> returning emptyList()")
        return emptyList()
    }

    val result = creatures.filter { creature ->
        if (debugTokenization) println("\n-- Evaluating creature: '${creature.name}' (${showCodePoints(creature.name)})")
        val words = tokenizeName(creature.name, ignoreCase)
        if (debugTokenization) println("tokenizeName('${creature.name}') -> words=${words.joinToString(prefix = "[", postfix = "]")}  details=${words.map { showCodePoints(it) }}")
        val matched = matchesSequentialPrefixes(words, parts)
        if (debugTokenization) println("matchesSequentialPrefixes -> $matched for creature='${creature.name}'")
        matched
    }

    if (debugTokenization) println("\n=== filterCreatures result ===")
    if (debugTokenization) println("Matched ${result.size} creature(s): ${result.map { it.name }}")
    return result
}

// Helper to show Unicode code points for debugging mixed scripts, etc.
private fun showCodePoints(s: String): String =
    s.map { c -> "U+${c.code.toString(16).uppercase().padStart(4, '0')}('$c')" }
        .joinToString(" ")

/** End of: Utility functions that help get target by name like "part1.part2.part3" */

fun getTimeNowWithMillis() : String {
    val current = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss:SSS")
    return current.format(formatter)
}

fun isProbablyZip(file: File): Boolean {
    if (!file.exists() || file.length() < 4L) return false
    RandomAccessFile(file, "r").use { raf ->
        val sig = raf.readInt()
        // ZIP local file header, end of central directory (empty zip), or spanned archive
        return sig == 0x504B0304 || sig == 0x504B0506 || sig == 0x504B0708
    }
}
