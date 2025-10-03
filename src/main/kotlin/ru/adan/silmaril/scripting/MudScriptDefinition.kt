package ru.adan.silmaril.scripting

import java.io.Serializable
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassloader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.util.classpathFromClass

// The @KotlinScript annotation is what the IDE and compiler read.
@KotlinScript(
    fileExtension = "mud.kts",
    compilationConfiguration = MudScriptDefinition::class,
)

// This abstract class is a convention for script definitions.
object MudScriptDefinition : ScriptCompilationConfiguration({
    // It declares that scripts will have a ScriptingEngine as their `this`.
    baseClass(MudScriptHost::class)

    // Imports
    defaultImports(
        "ru.adan.silmaril.scripting.*",
        "ru.adan.silmaril.misc.*",
        "kotlin.text.MatchResult",
        "ru.adan.silmaril.mud_messages.*",
        "kotlinx.coroutines.*",
        "kotlin.random.Random",
        "org.koin.core.component.KoinComponent"
    )

    jvm {
        dependenciesFromClassContext(MudScriptDefinition::class, wholeClasspath = true)
        //dependenciesFromClassloader(wholeClasspath = true)
        //classpathFromClass(MudScriptDefinition::class)
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
}), Serializable { // implementing Serializable
    private fun readResolve(): Any = MudScriptDefinition
}