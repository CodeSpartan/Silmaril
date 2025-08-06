package ru.adan.silmaril

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

import ru.adan.silmaril.model.MapModel
import ru.adan.silmaril.model.SettingsManager

val appModule = module {
    singleOf (::SettingsManager)
    singleOf (::MapModel)
}