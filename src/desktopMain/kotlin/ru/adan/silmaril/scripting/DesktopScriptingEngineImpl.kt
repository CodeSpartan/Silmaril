package ru.adan.silmaril.scripting

import ru.adan.silmaril.misc.AnsiColor
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.ProfileManagerInterface
import ru.adan.silmaril.model.SettingsProvider
import ru.adan.silmaril.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

/**
 * Desktop-specific implementation of ScriptingEngine that supports loading .kts scripts.
 * This uses the kotlin-scripting library which is only available on desktop JVM.
 */
class DesktopScriptingEngineImpl(
    profileName: String,
    mainViewModel: MainViewModel,
    isGroupActive: (String) -> Boolean,
    scriptData: TransientScriptData,
    settingsProvider: SettingsProvider,
    profileManager: ProfileManagerInterface,
    loreManager: LoreManager,
) : ScriptingEngineImpl(
    profileName,
    mainViewModel,
    isGroupActive,
    scriptData,
    settingsProvider,
    profileManager,
    loreManager
) {
    companion object {
        private val evaluator by lazy { BasicJvmScriptEvaluator() }
    }

    /**
     * Loads and executes a user's .kts script file using kotlin-scripting.
     * Compilation is shared between windows and cached on disk, see [DslScriptCache].
     * @return number of triggers loaded
     */
    override fun loadScript(scriptFile: File): Int {
        currentlyLoadingScript = scriptFile.name.replace(".mud.kts", "").uppercase()
        settingsProvider.addGroup(currentlyLoadingScript)

        try {
            val evaluationConfig = ScriptEvaluationConfiguration {
                constructorArgs(this@DesktopScriptingEngineImpl)
                jvm {
                    // Resolve app classes (MudScriptHost etc.) from the app itself, never from a second copy.
                    baseClassLoader(MudScriptHost::class.java.classLoader)
                    loadDependencies(false)
                }
            }
            val result = runBlocking {
                DslScriptCache.load(scriptFile).onSuccess { evaluator(it, evaluationConfig) }
            }

            // Process any resulting errors or warnings.
            result.reports.forEach { report ->
                if (report.severity >= ScriptDiagnostic.Severity.WARNING) {
                    val location = report.location?.let { "at line ${it.start.line}, col ${it.start.col}" } ?: ""
                    logger.warn { "[SCRIPT]: ${report.message} $location" }
                    echoCommand("[$scriptFile]: ${report.message} $location", AnsiColor.Red, true)
                }
            }

            val triggersLoaded = result.reports.filter { report -> report.severity < ScriptDiagnostic.Severity.ERROR }.size
            return triggersLoaded

        } catch (e: Exception) {
            logger.error { "An exception occurred while setting up the script engine: ${e.message}" }
            return 0
        }
    }
}
