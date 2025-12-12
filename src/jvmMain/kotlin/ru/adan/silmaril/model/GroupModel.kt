package ru.adan.silmaril.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.adan.silmaril.platform.createLogger

class GroupModel(private val client: GroupDataSource) : KoinComponent {

    val logger = createLogger("ru.adan.silmaril.model.GroupModel")
    val knownHpTracker: KnownHpTracker by inject()

    private val scopeDefault = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var myName = ""
    private var myMaxHp = -1

    val myNameRegex = """^Вы [\p{L}\-\s]+ (\p{L}+), (?:(?:[\p{L}\-\s,]+?,) )?(\p{L}+) \d+ уровня\.$""".toRegex()
    val myNameRegex2 = """^Аккaунт \[\p{L}+\] Персонаж \[(\p{L}+)\]$""".toRegex()
    val myMaxHpRegex = """^Вы имеете \d+\((\d+)\) единиц здоровья, \d+\(\d+\) энергетических единиц\.$""".toRegex()
    val othersMaxHpRegex = """^(\p{L}+) сообщи(?:л|ла|ло|ли) группе: \d+\/(\d+)H, \d+\/\d+V(, Зап\:\d+\:\d+)?$""".toRegex()
    val petRegex1 = """^Имя:\s+([\p{L}\s]+), Где находится: В комнате <.+>$""".toRegex()
    val petRegex2 = """^Состояние: \d+\/(\d+)H, \d+\/\d+V$""".toRegex()

    var groupPetName = ""
    var petOnNextLine = false

    val groupMates: StateFlow<List<Creature>> = client.lastGroupMessage
        .stateIn(
            scope = scopeDefault,
            started = SharingStarted.Eagerly, // is initialized and runs continuously
            initialValue = emptyList()
        )

    fun getGroupMates(): List<Creature> {
        return groupMates.value
    }

    fun init() {
        scopeDefault.launch {
            client.unformattedTextMessages.collect { textMessage ->
                val myNameMatch = myNameRegex.find(textMessage)
                if (myNameMatch != null) {
                    myName = myNameMatch.groupValues[1]
                    logger.debug { "My name is $myName" }
                } else {
                    val myNameMatch2 = myNameRegex2.find(textMessage)
                    if (myNameMatch2 != null) {
                        myName = myNameMatch2.groupValues[1]
                        logger.debug { "My name is $myName" }
                    }
                }

                if (myName != "") {
                    val myMaxHpMatch = myMaxHpRegex.find(textMessage)
                    if (myMaxHpMatch != null) {
                        myMaxHp = myMaxHpMatch.groupValues[1].toInt()
                        logger.debug { "$myName has max hp: $myMaxHp" }
                        knownHpTracker.addKnownHp(myName, myMaxHp)
                    }
                }

                val othersMaxHpMatch = othersMaxHpRegex.find(textMessage)
                if (othersMaxHpMatch != null) {
                    val groupMateName = othersMaxHpMatch.groupValues[1]
                    val groupMateMaxHp = othersMaxHpMatch.groupValues[2].toInt()
                    logger.debug { "$groupMateName has max hp: $groupMateMaxHp" }
                    knownHpTracker.addKnownHp(groupMateName, groupMateMaxHp)
                }

                if (petOnNextLine) {
                    petOnNextLine = false
                    val petHpRegexMatch = petRegex2.find(textMessage)
                    if (petHpRegexMatch != null) {
                        val petHp = petHpRegexMatch.groupValues[1].toInt()
                        logger.debug { "$groupPetName has max hp: $petHp" }
                        knownHpTracker.addKnownHp(groupPetName, petHp)
                    }
                    groupPetName = ""
                }

                val petNameRegexMatch = petRegex1.find(textMessage)
                if (petNameRegexMatch != null) {
                    groupPetName = petNameRegexMatch.groupValues[1]
                    petOnNextLine = true
                    logger.debug { "Found pet: $groupPetName, checking next line for pet hp"}
                }
            }
        }
    }

    fun cleanup() {
        scopeDefault.cancel()
    }

    // Return null if we're uncertain
    fun isLeader() : Boolean? {
        if (groupMates.value.isEmpty()) return null
        if (myName == "") return null
        return groupMates.value.first().name == myName
    }

    fun getMyStamina() : Int? {
        if (groupMates.value.isEmpty()) return null
        if (myName == "") return null
        return groupMates.value.first().movesPercent.toInt()
    }

    fun getMyName() : String = myName
}
