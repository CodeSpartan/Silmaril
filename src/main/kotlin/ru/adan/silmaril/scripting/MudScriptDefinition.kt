package ru.adan.silmaril.scripting

import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.jvm.util.classpathFromClass

// The @KotlinScript annotation is what the IDE and compiler read.
@KotlinScript(
    // This is the file extension for our scripts.
    fileExtension = "mud.kts",

    // This points to our compilation configuration class below.
    compilationConfiguration = MudScriptDefinition::class,
    //evaluationConfiguration = MudScriptEValuationConfiguration::class
)
// This abstract class is a convention for script definitions.
object MudScriptDefinition : ScriptCompilationConfiguration({
    // This is the single source of truth for the IDE and the runtime compiler.
    // It unambiguously declares that scripts will have a ScriptingEngine as their `this`.
    implicitReceivers(ScriptingEngine::class)

    // Imports
    defaultImports(
        "ru.adan.silmaril.scripting.*",
        "ru.adan.silmaril.misc.*",
        "kotlin.text.MatchResult",
    )

    jvm {
        dependenciesFromClassloader(wholeClasspath = true)
        classpathFromClass(MudScriptDefinition::class)
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
}) { // implementing Serializable
    private fun readResolve(): Any = MudScriptDefinition
}