@file:OptIn(KordPreview::class)

package nl.pindab0ter.eggbot

import com.kotlindiscord.kord.extensions.ExtensibleBot
import dev.kord.common.annotation.KordPreview
import dev.kord.gateway.Intent.*
import nl.pindab0ter.eggbot.extensions.*
import nl.pindab0ter.eggbot.model.Config

lateinit var eggBot: ExtensibleBot

val guildSpecificCommands = listOf(
    ::ActivityCommand,
    ::AddCoopCommand,
    ::ContractsCommand,
    ::CoopInfoCommand,
    ::CoopsInfoCommand,
    ::EarningsBonusCommand,
    ::LeaderBoardCommand,
    ::RemoveCoopCommand,
    ::RollCallExtension,
    ::UnregisterCommand,
    ::WhoIsCommand,
)

suspend fun main() {
    eggBot = ExtensibleBot(Config.botToken) {
        intents {
            +DirectMessages
            +DirectMessageTyping
            +GuildMessages
            +GuildMessageTyping
        }

        extensions {
            configureSentry()

            // Extensions
            add(::CommandLogger)

            // Commands
            add(::RegisterCommand)
            guildSpecificCommands.forEach(::add)
        }

        hooks {
            beforeExtensionsAdded {
                connectToDatabase()
            }

            beforeStart {
                startScheduler()
            }
        }
    }

    eggBot.start()
}
