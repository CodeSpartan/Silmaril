package ru.adan.silmaril.scripting

import androidx.compose.ui.input.key.KeyEvent
import ru.adan.silmaril.misc.Hotkey
import ru.adan.silmaril.misc.toHotkeyEvent
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.view.CreatureEffect

/**
 * Desktop-specific extension functions for ScriptingEngine.
 * These provide KeyEvent adapters and CreatureEffect methods that are not available on other platforms.
 */

/**
 * Process a Compose KeyEvent by converting it to a HotkeyEvent.
 */
fun ScriptingEngine.processHotkey(keyEvent: KeyEvent): Boolean {
    if (!Hotkey.isKeyValid(keyEvent)) return false
    return processHotkey(keyEvent.toHotkeyEvent())
}

/**
 * Check if a Compose KeyEvent is bound to a hotkey.
 */
fun ScriptingEngine.isBoundHotkeyEvent(keyEvent: KeyEvent): Boolean {
    if (!Hotkey.isKeyValid(keyEvent)) return false
    return isBoundHotkeyEvent(keyEvent.toHotkeyEvent())
}

/**
 * Get group affects with CreatureEffect icons.
 * This is desktop-only because CreatureEffect uses DrawableResource.
 */
fun ScriptingEngine.getGroupAffects(): List<Pair<String, List<CreatureEffect>>> {
    val pm = getProfileManager()
    if (pm is ProfileManager) {
        return pm.currentGroupModel.value.getGroupMates().map { groupMate ->
            groupMate.name to groupMate.affects.mapNotNull { affect -> CreatureEffect.fromAffect(affect, false) }
        }
    }
    return emptyList()
}

/**
 * Get mobs affects with CreatureEffect icons.
 * This is desktop-only because CreatureEffect uses DrawableResource.
 */
fun ScriptingEngine.getMobsAffects(): List<Pair<String, List<CreatureEffect>>> {
    val pm = getProfileManager()
    if (pm is ProfileManager) {
        return pm.currentMobsModel.value.getMobs().map { mob ->
            mob.name to mob.affects.mapNotNull { affect -> CreatureEffect.fromAffect(affect, false) }
        }
    }
    return emptyList()
}

/**
 * Add a hotkey using the desktop Hotkey class (with Compose Key).
 */
fun ScriptingEngine.addHotkeyToGroup(group: String, hotkey: Hotkey) {
    addHotkeyToGroup(group, hotkey.toHotkeyData())
}
