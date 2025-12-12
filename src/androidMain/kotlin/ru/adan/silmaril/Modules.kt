package ru.adan.silmaril

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import ru.adan.silmaril.model.AndroidProfile
import ru.adan.silmaril.model.AndroidProfileManager
import ru.adan.silmaril.model.AndroidSettingsManager
import ru.adan.silmaril.model.LoreManager
import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.OutputWindowModel
import ru.adan.silmaril.model.ProfileManagerInterface
import ru.adan.silmaril.model.RoomDataManager
import ru.adan.silmaril.model.SettingsProvider
import ru.adan.silmaril.model.TextMacrosManager
import ru.adan.silmaril.model.SystemMessageDisplay
import ru.adan.silmaril.model.KnownHpTracker
import ru.adan.silmaril.model.LoreDisplayCallback
import ru.adan.silmaril.viewmodel.UnifiedMapsViewModel

val androidModule = module {
    singleOf(::AndroidSettingsManager)
    single<SettingsProvider> { get<AndroidSettingsManager>() }
    singleOf(::MapModel)
    singleOf(::TextMacrosManager)
    singleOf(::LoreManager)
    singleOf(::OutputWindowModel)
    singleOf(::RoomDataManager)
    singleOf(::UnifiedMapsViewModel)

    // AndroidProfileManager needs to be created after singletons are available
    single {
        AndroidProfileManager(
            settingsManager = get(),
            mapModel = get(),
            outputWindowModel = get()
        )
    }
    single<ProfileManagerInterface> { get<AndroidProfileManager>() }
    single<SystemMessageDisplay> { get<AndroidProfileManager>() }
    single<KnownHpTracker> { get<AndroidProfileManager>() }
    single<LoreDisplayCallback> { get<AndroidProfileManager>() }

    // AndroidProfile creates MudConnection, MainViewModel, MapViewModel, GroupModel,
    // MobsModel, and ScriptingEngine directly to avoid Koin parameter casting issues with lambdas
    factory { params ->
        AndroidProfile(
            profileName = params.get(),
            settingsManager = get(),
            mapModel = get(),
            outputWindowModel = get()
        )
    }
}
