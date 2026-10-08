package ru.adan.silmaril.scripting

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ru.adan.silmaril.misc.getProgramDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.loadScriptFromJar
import kotlin.script.experimental.jvmhost.saveToJar

/**
 * Compiles each DSL script at most once per process (shared by all game windows),
 * and persists the compiled bytecode as jars in Documents/Silmaril/cache/dsl so later launches
 * skip compilation entirely when neither the script nor the client build changed.
 *
 * Every window loads its own copy of the script classes (a fresh classloader per [load] call),
 * so script-level state, including `object` declarations, is not shared between windows.
 */
object DslScriptCache {
    // Bump to invalidate all existing cached jars when the caching scheme changes.
    private const val CACHE_FORMAT = 1
    private val MAX_UNUSED_AGE_MS = TimeUnit.DAYS.toMillis(30)

    private val logger = KotlinLogging.logger {}

    private val compiler by lazy { JvmScriptCompiler() }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))
    )

    private val entries = ConcurrentHashMap<String, Deferred<Entry>>()

    private val cacheDir: File by lazy {
        File(getProgramDirectory(), "cache/dsl").apply {
            mkdirs()
            pruneStaleJars(this)
        }
    }

    /**
     * Fingerprint of everything compiled scripts link against. A rebuilt client changes jar sizes/timestamps,
     * which invalidates the cache and prevents NoSuchMethodError from stale script bytecode.
     */
    private val environmentFingerprint: String by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("format=$CACHE_FORMAT;kotlin=${KotlinVersion.CURRENT};".toByteArray())
        val classpath = MudScriptDefinition[ScriptCompilationConfiguration.dependencies]
            .orEmpty()
            .filterIsInstance<JvmDependency>()
            .flatMap { it.classpath }
        for (entry in classpath) {
            md.update(entry.absolutePath.toByteArray())
            if (entry.isDirectory) {
                // Dev runs use class directories; summarize their contents.
                var count = 0L
                var size = 0L
                var newest = 0L
                entry.walkTopDown().filter { it.isFile }.forEach {
                    count++
                    size += it.length()
                    newest = maxOf(newest, it.lastModified())
                }
                md.update(";dir:$count:$size:$newest;".toByteArray())
            } else {
                md.update(";file:${entry.length()}:${entry.lastModified()};".toByteArray())
            }
        }
        md.digest().toHex()
    }

    private class Entry(
        val jar: File?,
        // Only set when the jar couldn't be written; evaluated directly from memory then.
        val inMemory: CompiledScript?,
        val reports: List<ScriptDiagnostic>,
    )

    /** Compiles (or validates cached jars for) all given scripts in parallel. */
    suspend fun precompile(scriptFiles: List<File>) {
        coroutineScope {
            scriptFiles.map { async { entryFor(it.toScriptSource()) } }.awaitAll()
        }
    }

    /** Returns a compiled script ready to be evaluated by one window. */
    suspend fun load(scriptFile: File): ResultWithDiagnostics<CompiledScript> {
        val entry = entryFor(scriptFile.toScriptSource())
        entry.jar?.let { jar ->
            // Loading per call yields a fresh classloader on evaluation, isolating windows from each other.
            val script = runCatching { jar.loadScriptFromJar() }
                .onFailure { logger.warn { "Failed to load cached script ${jar.name}: ${it.message}" } }
                .getOrNull()
            if (script != null) return script.asSuccess(entry.reports)
        }
        entry.inMemory?.let { return it.asSuccess(entry.reports) }
        return ResultWithDiagnostics.Failure(entry.reports)
    }

    private suspend fun entryFor(source: SourceCode): Entry {
        val key = cacheKey(source)
        return entries.computeIfAbsent(key) { scope.async { prepare(key, source) } }.await()
    }

    private suspend fun prepare(key: String, source: SourceCode): Entry {
        val jar = File(cacheDir, "$key.jar")
        if (jar.isFile) {
            val valid = runCatching { jar.loadScriptFromJar() }.getOrNull() != null
            if (valid) {
                jar.setLastModified(System.currentTimeMillis())
                logger.info { "[SCRIPT]: ${source.name} loaded from cache" }
                return Entry(jar, null, emptyList())
            }
            jar.delete()
        }

        val started = System.currentTimeMillis()
        val result = compiler(source, MudScriptDefinition)
        val compiled = result.valueOrNull()
            ?: return Entry(null, null, result.reports)
        logger.info { "[SCRIPT]: ${source.name} compiled in ${System.currentTimeMillis() - started} ms" }

        val saved = runCatching {
            val tmp = File(cacheDir, "$key.${ProcessHandle.current().pid()}.tmp")
            (compiled as KJvmCompiledScript).saveToJar(tmp)
            Files.move(tmp.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            logger.warn { "Failed to cache compiled script ${source.name}: ${it.message}" }
        }.isSuccess

        return if (saved) Entry(jar, null, result.reports) else Entry(null, compiled, result.reports)
    }

    private fun cacheKey(source: SourceCode): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(environmentFingerprint.toByteArray())
        md.update((source.name ?: "").toByteArray())
        md.update(0)
        md.update(source.text.toByteArray())
        return md.digest().toHex().take(40)
    }

    private fun pruneStaleJars(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_UNUSED_AGE_MS
        dir.listFiles()?.forEach {
            if (it.name.endsWith(".tmp") || (it.name.endsWith(".jar") && it.lastModified() < cutoff)) {
                it.delete()
            }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
