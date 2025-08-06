package ru.adan.silmaril

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.Profile
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.scripting.ScriptingEngine

val appModule = module {
    singleOf (::SettingsManager)
    singleOf (::MapModel)
    singleOf(::ProfileManager)

    factory { params -> // 'params' holds our runtime parameters
        Profile(
            profileName = params[0], // Get the first parameter (a String)
            settingsManager = get(),   // Get this dependency from Koin
            mapModel = get()       // Get this dependency from Koin
        )
    }

    factory { params ->
        ScriptingEngine(
            profileName = params[0],       // Get 1st parameter by index
            mainViewModel = params[1],     // Get 2nd parameter
            isGroupActive = params[2],     // Get 3rd parameter
            settingsManager = get(),           // Get this from Koin's singletons
            profileManager = get()             // Get this from Koin's singletons
        )
    }
}