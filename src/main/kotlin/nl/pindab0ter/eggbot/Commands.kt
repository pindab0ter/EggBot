package nl.pindab0ter.eggbot

import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteErrorCode

interface Command {
    val keyWord: String
    val help: String
    fun function(event: MessageReceivedEvent)
}

object Help : Command {
    override val keyWord = "help"
    override val help = "$prefix$keyWord - Shows this menu"
    // TODO: Change to EmbedMessage
    override fun function(event: MessageReceivedEvent) = event.channel
        .sendMessage("Available commands:\n${commands.joinToString("\n") { it.help }}")
        .queue()
}

object PingPong : Command {
    override val keyWord: String = "ping"
    override val help: String = "$prefix$keyWord - \"Pong!\""
    override fun function(event: MessageReceivedEvent) = event.channel.sendMessage("Pong!").queue()
}

object Register : Command {
    override val keyWord = "register"
    override val help = "$prefix$keyWord <in-game name> - Register on this server with your in-game name"

    override fun function(event: MessageReceivedEvent) {
        val arguments = event.message.arguments

        if (arguments?.size != 1) {
            event.channel.sendMessage(help).queue()
            return
        }

        try {
            transaction {
                Farmer.new {
                    discordTag = event.author.asTag
                    inGameName = arguments[0]
                }
            }.apply {
                event.channel.sendMessage("Successfully registered, welcome!").queue()
            }
        } catch (exception: ExposedSQLException) {
            if (exception.errorCode == SQLiteErrorCode.SQLITE_CONSTRAINT.code &&
                exception.message?.contains(ColumnNames.farmerDiscordTag) == true
            ) {
                event.channel.sendMessage("You are already registered!").queue()
            } else event.channel.sendMessage("Failed to register.").queue()
        }
    }
}
