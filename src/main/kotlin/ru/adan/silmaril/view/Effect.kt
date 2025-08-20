package ru.adan.silmaril.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.*
import ru.adan.silmaril.mud_messages.Affect

@Composable
fun Effect(affect: Affect) {
    Image(
        painter = painterResource(when (affect.name) {
//            "голод" -> Res.drawable.hunger
//            "жажда" -> Res.drawable.thirst
//            "полет" -> Res.drawable.hermes
//            "ускорение" -> Res.drawable.accelerate
            // blesses
//            "благословение" -> Res.drawable.bless
//            "точность" -> Res.drawable.bless
//            "слепота" -> Res.drawable.blind
//            "проклятие" -> Res.drawable.curse
            // cleric heals
//            "легкое заживление" -> Res.drawable.heal_cleric
//            "серьезное заживление" -> Res.drawable.heal_cleric
//            "критическое заживление" -> Res.drawable.heal_cleric
//            "заживление" -> Res.drawable.heal_cleric
            // druid heals
//            "легкое обновление" -> Res.drawable.heal_druid
//            "серьезное обновление" -> Res.drawable.heal_druid
//            "критическое обновление" -> Res.drawable.heal_druid
            // holds
//            "придержать персону" -> Res.drawable.hold_person
//            "придержать любого" -> Res.drawable.hold_person
//            "невидимость" -> Res.drawable.invisibility
//            "каменное проклятие" -> Res.drawable.petrification
            // poisons
//            "яд" -> Res.drawable.poison
//            "ядовитый выстрел" -> Res.drawable.poison
//            "защита" -> Res.drawable.shield1
//            "замедление" -> Res.drawable.slow
            
//            "голод" -> Res.drawable.hunger
//            "жажда" -> Res.drawable.thirst
//            "полет" -> Res.drawable.hermes
            else -> Res.drawable.shield2
        }),
        modifier = Modifier.width(30.dp).padding(end = 6.dp),
        contentDescription = "Effect",
    )
}