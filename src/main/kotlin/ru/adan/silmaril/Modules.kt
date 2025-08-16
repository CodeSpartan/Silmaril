package ru.adan.silmaril

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.adan.silmaril.model.LoreManager

import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.model.TextTriggerManager
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.scripting.ScriptingEngineImpl
import ru.adan.silmaril.viewmodel.MainViewModel

val appModule = module {
    singleOf (::SettingsManager)
    singleOf (::MapModel)
    singleOf(::ProfileManager)
    singleOf(::TextTriggerManager)
    singleOf(::LoreManager)

    factory { params ->
        Profile(
            profileName = params.get(),
            settingsManager = get(),
            mapModel = get()
        )
    }

    factory { params ->
        MudConnection(
            host = params[0],
            port = params.get(),
            profileName = params[2],
            onMessageReceived = params.get(),
            settingsManager = get(),
            loreManager = get(),
        )
    }

    factory { params ->
        MainViewModel(
            client = params[0],
            onSystemMessage = params[1],
            onInsertVariables = params[2],
            onProcessAliases = params[3],
            onMessageReceived = params[4],
            settingsManager = get()
        )
    }

    factory<ScriptingEngine> { params ->
        ScriptingEngineImpl(
            profileName = params.get(),
            mainViewModel = params.get(),
            isGroupActive = params.get(),
            settingsManager = get(),
            profileManager = get(),
            loreManager = get(),
        )
    }
}