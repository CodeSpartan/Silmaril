package ru.adan.silmaril

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import ru.adan.silmaril.model.GroupModel
import ru.adan.silmaril.model.LoreManager

import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MobsModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.model.TextMacrosManager
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.scripting.ScriptingEngineImpl
import ru.adan.silmaril.viewmodel.MainViewModel

val appModule = module {
    singleOf (::SettingsManager)
    singleOf (::MapModel)
    singleOf(::ProfileManager)
    singleOf(::TextMacrosManager)
    singleOf(::LoreManager)
    singleOf(::OutputWindowModel)

    factory { params ->
        Profile(
            profileName = params.get(),
            settingsManager = get(),
            mapModel = get(),
            outputWindowModel = get(),
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

    factory { params ->
        GroupModel(
            client = params[0],
            settingsManager = get()
        )
    }

    factory { params ->
        MobsModel(
            client = params[0],
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