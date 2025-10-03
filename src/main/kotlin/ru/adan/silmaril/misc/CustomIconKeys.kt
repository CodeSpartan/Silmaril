package ru.adan.silmaril.misc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

/** Room Icons */

object CustomIconKeys {
    val Quester: IconKey = PathIconKey(path = "/icons/quester.svg", iconClass = CustomIconKeys::class.java)
    val NoMagic: IconKey = PathIconKey(path = "/icons/nomagic.svg", iconClass = CustomIconKeys::class.java)
    val Boss: IconKey = PathIconKey(path = "/icons/boss.svg", iconClass = CustomIconKeys::class.java)
    val Danger: IconKey = PathIconKey(path = "/icons/danger.svg", iconClass = CustomIconKeys::class.java)
    val DeathTrap: IconKey = PathIconKey(path = "/icons/deathtrap.svg", iconClass = CustomIconKeys::class.java)
    val Item: IconKey = PathIconKey(path = "/icons/item.svg", iconClass = CustomIconKeys::class.java)
    val Trigger: IconKey = PathIconKey(path = "/icons/trigger.svg", iconClass = CustomIconKeys::class.java)
    val Misc: IconKey = PathIconKey(path = "/icons/misc.svg", iconClass = CustomIconKeys::class.java)
}

@Composable
fun rememberIconPainter(key: IconKey, isNewUi: Boolean = true): Painter {
    val path = remember(key, isNewUi) { key.path(isNewUi) }
    val provider = rememberResourcePainterProvider(path, key.iconClass)
    val painter by provider.getPainter()
    return painter
}

@Composable
fun rememberIconPainterCache(
    keys: Collection<IconKey>,
    isNewUi: Boolean = true
): Map<IconKey, Painter> {
    // Make iteration order stable so composition slots don’t shuffle
    val sorted = remember(keys) { keys.toSet().sortedBy { it.toString() } }

    return buildMap {
        for (k in sorted) {
            androidx.compose.runtime.key(k) {
                put(k, rememberIconPainter(k, isNewUi))
            }
        }
    }
}