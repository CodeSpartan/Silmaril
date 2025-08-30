package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.mud_messages.Creature
import kotlin.getValue

class MobsModel(private val client: MudConnection, private val settingsManager: SettingsManager) : KoinComponent {

    val logger = KotlinLogging.logger {}
    val profileManager: ProfileManager by inject()

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val mobs: StateFlow<List<Creature>> = client.lastMonstersMessage
        .stateIn(
            scope = scopeDefault,
            started = SharingStarted.Eagerly, // is initialized and runs continuously
            initialValue = emptyList()
        )

    fun getMobs(): List<Creature> {
        return mobs.value
    }
    
    fun cleanup() {
        scopeDefault.cancel()
    }
}