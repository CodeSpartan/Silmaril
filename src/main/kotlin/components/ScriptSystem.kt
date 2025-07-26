package components

class ScriptSystem() {
    val rawCommands : MutableMap<CommandName, List<Command>> = mutableMapOf()

    //
    fun addCommand(cmdString : String) {
        val (rawCommandName, rawCommandValue) = cmdString.split('=', limit = 2)
        rawCommandValue.split(' ').forEach { cmd ->
            if (cmd.first() == '$') {
                Command.AliasCommand(CommandName(cmd))
            } else {
                Command.RawCommand(cmd)
            }
        }
    }
}

sealed class Command {
    data class RawCommand(val command: String)
    data class AliasCommand(val command: CommandName)
}

data class CommandName(val command: String)
