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

    // All the other configuration remains the same.
    defaultImports("ru.adan.silmaril.scripting.*", "kotlin.text.MatchResult")

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

//// This object defines the actual configuration, just like you did in `loadScript`.
//object MudScriptCompilationConfiguration : ScriptCompilationConfiguration({
//    // 1. Tell the IDE the script has a `ScriptingEngine` as its `this`.
//    implicitReceivers(ScriptingEngine::class)
//
//    // 2. Tell the IDE to import your DSL functions automatically.
//    defaultImports("ru.adan.silmaril.scripting.*", "kotlin.text.MatchResult")
//
//    // 3. Tell the IDE how to handle dependencies (this is crucial).
//    jvm {
//        dependenciesFromClassloader(wholeClasspath = true)
//        classpathFromClass(MudScriptDefinition::class)
//    }
//    ide {
//        acceptedLocations(ScriptAcceptedLocation.Everywhere)
//    }
//}) {
//    private fun readResolve(): Any = MudScriptEValuationConfiguration
//}
//
//object MudScriptEValuationConfiguration : ScriptEvaluationConfiguration() {
//    private fun readResolve(): Any = MudScriptEValuationConfiguration
//}