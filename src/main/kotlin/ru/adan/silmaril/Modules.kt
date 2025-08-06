package ru.adan.silmaril

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.viewmodel.MainViewModel

val appModule = module {
    singleOf (::SettingsManager)
    singleOf (::MapModel)
    singleOf(::ProfileManager)

    factory { params ->
        Profile(
            profileName = params.get(),
            settingsManager = get(),
            mapModel = get()
        )
    }

    factory { params ->
        MudConnection(
            host = params.get(),
            port = params.get(),
            onMessageReceived = params.get(),
            settingsManager = get()
        )
    }

    factory { params ->
        MainViewModel(
            client = params[0],
            onSystemMessage = params[1],
            onInsertVariables = params[2],
            onMessageReceived = params[3],
            settingsManager = get()
        )
    }

    factory { params ->
        ScriptingEngine(
            profileName = params.get(),
            mainViewModel = params.get(),
            isGroupActive = params.get(),
            settingsManager = get(),
            profileManager = get()
        )
    }
}