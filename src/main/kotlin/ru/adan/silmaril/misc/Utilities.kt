package ru.adan.silmaril.misc

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import androidx.compose.runtime.*

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

fun getProgramDirectory(): String {
    val userHome = System.getProperty("user.home")
    val os = getOperatingSystem()
    val programPath: String = when {
        // for Windows, we're in user's Documents\Silmaril -- C:\Users\<YourUsername>\Documents\Silmaril\
        // for macOS, we're in ~/Documents/Silmaril -- /Users/<YourUsername>/Documents/Silmaril/
        // for Linux, we're in ~/.Silmaril -- /home/<YourUsername>/Documents/Silmaril/
        os == "Windows" || os == "MacOS" || os == "Linux" -> Paths.get(userHome, "Documents", "Silmaril").toString()
        else -> Paths.get(userHome, "Silmaril").toString()
    }

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
    val path = Paths.get(getProgramDirectory(), "subsitutes").toString()
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

/**
 * A helper function to find an enum value by name, ignoring case.
 * Returns a default value if no match is found.
 */
// Case-insensitive enum lookup with fallback
inline fun <reified T : Enum<T>> enumValueOfIgnoreCase(name: String?, default: T): T {
    if (name.isNullOrBlank()) return default
    val n = name.trim()
    return enumValues<T>().firstOrNull { it.name.equals(n, ignoreCase = true) } ?: default
}

// Nullable version (no fallback)
inline fun <reified T : Enum<T>> enumValueOfIgnoreCaseOrNull(name: String?): T? {
    if (name.isNullOrBlank()) return null
    val n = name.trim()
    return enumValues<T>().firstOrNull { it.name.equals(n, ignoreCase = true) }
}

fun List<String>.joinOrNone(): String {
    return if (this.isEmpty()) {
        "NOBITS"
    } else {
        this.joinToString(" ")
    }
}

fun minutesToDaysFormatted(minutes: Int): String {
    val days = minutes / 1440
    val lastDigit = days % 10
    val lastTwoDigits = days % 100

    if (lastTwoDigits in 11..14) {
        return "$days дней"
    }

    return when (lastDigit) {
        1 -> "$days день"
        2, 3, 4 -> "$days дня"
        else -> "$days дней"
    }
}

fun Double.toSmartString(): String {
    // Check if the double has no fractional part.
    // (e.g., 12.0 % 1.0 == 0.0)
    return if (this % 1.0 == 0.0) {
        // If it's a whole number, convert to Int to remove the decimal, then to String.
        this.toInt().toString()
    } else {
        // Otherwise, return the default string representation.
        this.toString()
    }
}

// returns duration in hours if less than a day, or days and hours otherwise
fun formatDuration(totalHours: Int): String {
    if (totalHours <= 0) {
        return "0 час."
    }

    return if (totalHours < 24) {
        "$totalHours час."
    } else {
        val days = totalHours / 24
        val hours = totalHours % 24
        "$days дн. $hours час."
    }
}

fun formatMem(seconds: Int): String {
    val minutes = (seconds / 60).toString().padStart(2, '0')
    val remainingSeconds = (seconds % 60).toString().padStart(2, '0')
    return "$minutes:$remainingSeconds"
}

// a wrapper to carry a stable, monotonic id
@Immutable
data class OutputItem(val id: Long, val message: ColorfulTextMessage) {
    companion object {
        private var nextId = 0L
        fun new(message: ColorfulTextMessage) = OutputItem(nextId++, message)
    }
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

//fun Modifier.doubleClickOrSingle(
//    doubleClickTimeoutMs: Long = 250,
//    doubleClickSlopDp: Float = 6f,
//    onDoubleClick: (Offset) -> Unit,
//    onSingleClick: (Offset) -> Unit
//) = pointerInput(doubleClickTimeoutMs, doubleClickSlopDp) {
//    awaitEachGesture {
//        // Use the density from PointerInputScope
//        val slopPx = with(density) { doubleClickSlopDp.dp.toPx() }
//
//        val firstDown = awaitFirstDown(requireUnconsumed = false)
//        val firstUp = waitForUpOrCancellation() ?: return@awaitEachGesture
//
//        val secondDown = withTimeoutOrNull(doubleClickTimeoutMs) {
//            awaitFirstDown(requireUnconsumed = false)
//        }
//
//        if (secondDown != null &&
//            (secondDown.position - firstDown.position).getDistance() <= slopPx
//        ) {
//            waitForUpOrCancellation()
//            onDoubleClick(secondDown.position)
//        } else {
//            onSingleClick(firstUp.position)
//        }
//    }
//}

fun Modifier.doubleClickOrSingle(
    doubleClickTimeoutMs: Long = 250,
    doubleClickSlopDp: Float = 6f,
    onDoubleClick: (Offset) -> Unit,
    onSingleClick: (Offset) -> Unit
) = composed {
// Always call the latest lambdas (prevents stale captures)
    val latestOnDouble by rememberUpdatedState(onDoubleClick)
    val latestOnSingle by rememberUpdatedState(onSingleClick)


    pointerInput(doubleClickTimeoutMs, doubleClickSlopDp) {
        awaitEachGesture {
            val slopPx = with(density) { doubleClickSlopDp.dp.toPx() }

            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val firstUp = waitForUpOrCancellation() ?: return@awaitEachGesture

            val secondDown = withTimeoutOrNull(doubleClickTimeoutMs) {
                awaitFirstDown(requireUnconsumed = false)
            }

            if (secondDown != null &&
                (secondDown.position - firstDown.position).getDistance() <= slopPx
            ) {
                val secondUp = waitForUpOrCancellation()
                latestOnDouble(secondUp?.position ?: secondDown.position)
            } else {
                latestOnSingle(firstUp.position)
            }
        }
    }
}

@Composable
fun rememberIsAtBottom(state: LazyListState, fullyVisible: Boolean = false): State<Boolean> {
    return remember(state, fullyVisible) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) true
            else {
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                val isLastIndex = last.index == total - 1
                if (!isLastIndex) return@derivedStateOf false

                if (fullyVisible) {
                    // last item fully within viewport
                    (last.offset + last.size) <= info.viewportEndOffset
                } else {
                    // last item at least partially visible
                    true
                }
            }
        }
    }
}

object BuildInfo {
    val version: String =
        BuildInfo::class.java.`package`?.implementationVersion ?: "dev"
}