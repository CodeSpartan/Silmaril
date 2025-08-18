package ru.adan.silmaril.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class GroupModel(private val client: MudConnection, private val settingsManager: SettingsManager) : KoinComponent {

    val logger = KotlinLogging.logger {}
    val profileManager: ProfileManager by inject()

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var myName = ""
    private var myMaxHp = -1

    val myNameRegex = """^Вы \p{L}+ (\p{L}+), \p{L}+ \d+ уровня\.$""".toRegex()
    val myMaxHpRegex = """^Вы имеете \d+\((\d+)\) единиц здоровья, \d+\(\d+\) энергетических единиц\.$""".toRegex()
    val othersMaxHpRegex = """^(\p{L}+) сообщи(?:л|ла|ло|ли) группе: \d+\/(\d+)H, \d+\/\d+V$""".toRegex()

    fun init() {
        scopeDefault.launch {
            client.unformattedTextMessages.collect { textMessage ->
                if (myName == "") {
                    val myNameMatch = myNameRegex.find(textMessage)
                    if (myNameMatch != null) {
                        logger.info { "My name is $myName" }
                        myName = myNameMatch.groupValues[1]
                    }
                }
                if (myName != "" && myMaxHp == -1) {
                    val myMaxHpMatch = myMaxHpRegex.find(textMessage)
                    if (myMaxHpMatch != null) {
                        myMaxHp = myMaxHpMatch.groupValues[1].toInt()
                        logger.info { "$myName has max hp: $myMaxHp" }
                        profileManager.addKnownHp(myName, myMaxHp)
                    }
                }

                val othersMaxHpMatch = othersMaxHpRegex.find(textMessage)
                if (othersMaxHpMatch != null) {
                    val groupMateName = othersMaxHpMatch.groupValues[1]
                    val groupMateMaxHp = othersMaxHpMatch.groupValues[2].toInt()
                    logger.info { "$groupMateName has max hp: $groupMateMaxHp" }
                    profileManager.addKnownHp(groupMateName, groupMateMaxHp)
                }
            }
        }
    }

    fun cleanup() {
        scopeDefault.cancel()
    }
}