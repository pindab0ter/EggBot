package nl.pindab0ter.eggbot

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandEvent
import net.dv8tion.jda.core.entities.ChannelType
import java.util.function.Consumer


object HelpConsumer : Consumer<CommandEvent> {
    override fun accept(event: CommandEvent) {
        StringBuilder("**Commands**:\n").apply {
            appendln("`<>` = required argument, `[]` = optional argument")
            fun append(commands: List<Command>) = commands.forEach { command ->
                if (!command.isHidden && (!command.isOwnerCommand || event.isOwner)) {
                    append("\n`")
                    append(EggBot.commandClient.textualPrefix)
                    append(if (EggBot.commandClient.prefix == null) " " else "")
                    append(command.name)
                    append(if (command.arguments == null) "`" else " ${command.arguments}`")
                    append(" - ").append(command.help)
                }
            }

            EggBot.commandClient.commands.let { commands ->
                if (commands.any { it.category != null }) {
                    commands.groupBy { it.category }.forEach { (category, commands) ->
                        append("\n\n")
                        append("  __${if (category != null) category.name else "No category"}__:")
                        append(commands)
                    }
                } else append(commands)
            }

            // event.reply(toString())
            event.replyInDm(toString(),
                { if (event.isFromType(ChannelType.TEXT)) event.reactSuccess() },
                { event.replyWarning("Help cannot be sent because you are blocking Direct Messages.") }
            )
        }
    }
}
