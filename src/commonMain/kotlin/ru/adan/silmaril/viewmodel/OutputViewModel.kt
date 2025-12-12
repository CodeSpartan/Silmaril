package ru.adan.silmaril.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.adan.silmaril.misc.AnsiColor

/**
 * Represents a chunk of colored text
 */
data class TextChunk(
    val text: String,
    val fgColor: AnsiColor = AnsiColor.None,
    val bgColor: AnsiColor = AnsiColor.None,
    val isBright: Boolean = false
)

/**
 * Represents a line of output with colored text
 */
data class OutputLine(
    val chunks: List<TextChunk>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Shared ViewModel for MUD text output
 */
open class OutputViewModel(
    private val maxLines: Int = 1000
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _outputLines = MutableStateFlow<List<OutputLine>>(emptyList())
    val outputLines: StateFlow<List<OutputLine>> = _outputLines.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private var historyIndex = -1

    fun addLine(line: OutputLine) {
        val currentLines = _outputLines.value.toMutableList()
        currentLines.add(line)

        // Trim to max lines
        if (currentLines.size > maxLines) {
            val excess = currentLines.size - maxLines
            repeat(excess) { currentLines.removeAt(0) }
        }

        _outputLines.value = currentLines
    }

    fun addSimpleLine(text: String, color: AnsiColor = AnsiColor.None, isBright: Boolean = false) {
        addLine(OutputLine(
            chunks = listOf(TextChunk(text, color, AnsiColor.None, isBright))
        ))
    }

    fun addCommand(command: String) {
        if (command.isNotBlank()) {
            val history = _commandHistory.value.toMutableList()
            // Remove if already exists to move to end
            history.remove(command)
            history.add(command)
            // Limit history size
            if (history.size > 100) {
                history.removeAt(0)
            }
            _commandHistory.value = history
            historyIndex = -1
        }
    }

    fun getPreviousCommand(): String? {
        val history = _commandHistory.value
        if (history.isEmpty()) return null

        historyIndex = if (historyIndex == -1) {
            history.lastIndex
        } else {
            (historyIndex - 1).coerceAtLeast(0)
        }
        return history.getOrNull(historyIndex)
    }

    fun getNextCommand(): String? {
        val history = _commandHistory.value
        if (history.isEmpty() || historyIndex == -1) return null

        historyIndex = (historyIndex + 1).coerceAtMost(history.lastIndex)
        return history.getOrNull(historyIndex)
    }

    fun resetHistoryIndex() {
        historyIndex = -1
    }

    fun clearOutput() {
        _outputLines.value = emptyList()
    }

    fun clearHistory() {
        _commandHistory.value = emptyList()
        historyIndex = -1
    }
}
