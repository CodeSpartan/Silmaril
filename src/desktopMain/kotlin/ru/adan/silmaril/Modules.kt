package ru.adan.silmaril

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import ru.adan.silmaril.model.GroupModel
import ru.adan.silmaril.model.KnownHpTracker
import ru.adan.silmaril.model.LoreDisplayCallback
import ru.adan.silmaril.model.LoreManager

import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.MobsModel
import ru.adan.silmaril.model.MudConnection
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.ProfileManagerInterface
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.model.SettingsProvider
import ru.adan.silmaril.model.SystemMessageDisplay
import ru.adan.silmaril.model.TextMacrosManager
import ru.adan.silmaril.scripting.DesktopScriptingEngineImpl
import ru.adan.silmaril.scripting.ScriptingEngine
import ru.adan.silmaril.scripting.TransientScriptData
import ru.adan.silmaril.viewmodel.MainViewModel
import ru.adan.silmaril.viewmodel.MapViewModel
import ru.adan.silmaril.viewmodel.UnifiedMapsViewModel

val appModule = module {
    singleOf (::SettingsManager)
    single<SettingsProvider> { get<SettingsManager>() }
    singleOf (::MapModel)
    singleOf(::ProfileManager)
    single<ProfileManagerInterface> { get<ProfileManager>() }
    single<SystemMessageDisplay> { get<ProfileManager>() }
    single<LoreDisplayCallback> { get<ProfileManager>() }
    single<KnownHpTracker> { get<ProfileManager>() }
    singleOf(::TextMacrosManager)
    singleOf(::LoreManager)
    singleOf(::OutputWindowModel)
    singleOf(::RoomDataManager)
    singleOf(::UnifiedMapsViewModel)

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
            settingsProvider = get(),
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
            onRunSubstitutes = params[5],
            loreManager = get(),
            settingsProvider = get()
        )
    }

    factory { params ->
        MapViewModel(
            client = params[0],
            groupModel = params[1],
            onDisplayTaggedString = params[2],
            onSendMessageToServer = params[3],
            mapModel = get(),
            settingsProvider = get(),
        )
    }

    factory { params ->
        GroupModel(
            client = params[0]
        )
    }

    factory { params ->
        MobsModel(
            client = params[0],
            onMobsReceived = params[1]
        )
    }

    factory<ScriptingEngine> { params ->
        DesktopScriptingEngineImpl(
            profileName = params.get(),
            mainViewModel = params.get(),
            isGroupActive = params.get(),
            scriptData = TransientScriptData(),
            settingsProvider = get(),
            profileManager = get(),
            loreManager = get(),
        )
    }
}