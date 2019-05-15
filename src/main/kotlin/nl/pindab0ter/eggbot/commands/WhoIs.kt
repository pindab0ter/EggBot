package nl.pindab0ter.eggbot.commands

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandEvent
import mu.KotlinLogging
import nl.pindab0ter.eggbot.EggBot
import nl.pindab0ter.eggbot.arguments
import nl.pindab0ter.eggbot.database.DiscordUser
import nl.pindab0ter.eggbot.database.DiscordUsers
import nl.pindab0ter.eggbot.database.Farmer
import nl.pindab0ter.eggbot.database.Farmers
import nl.pindab0ter.eggbot.missingArguments
import org.jetbrains.exposed.sql.transactions.transaction

object WhoIs : Command() {

    private val log = KotlinLogging.logger { }

    init {
        name = "whois"
        aliases = arrayOf("who-is", "who", "whothefuckis")
        arguments = "<in-game name OR discord (nick)name>"
        help = "See which Discord user has registered with that in-game name or vice versa."
        // category = UsersCategory
        guildOnly = false
    }

    @Suppress("FoldInitializerAndIfToElvis")
    override fun execute(event: CommandEvent) {
        event.channel.sendTyping().queue()

        if (event.arguments.isEmpty()) missingArguments.let {
            event.replyWarning(it)
            log.debug { it }
            return
        }

        val name = event.arguments.joinToString(" ").replace(Regex("""^@?(\w*)(?:#\d{4})?$"""), "$1")

        transaction {
            (DiscordUser.find {
                DiscordUsers.discordTag like "${name}_____"
            }.firstOrNull() ?: EggBot.guild.getMembersByNickname(name, true).firstOrNull()?.let { discordUser ->
                DiscordUser[discordUser.user.id]
            })?.let { discordUser ->
                val discordUserName = discordUser.discordTag.dropLast(5)
                val nickname = EggBot.guild.getMemberById(discordUser.discordId)?.nickname
                    ?.let { nickname -> " ($nickname)" } ?: ""
                val farmerNames = discordUser.farmers.joinToString("`, `") { it.inGameName }

                "`@$discordUserName$nickname` is registered with: `$farmerNames`".let {
                    event.reply(it)
                    return@transaction
                }
            }

            Farmer.find { Farmers.inGameName like name }.firstOrNull()?.let { farmer ->
                val discordUserName = farmer.discordUser.discordTag.dropLast(5)
                val nickname = EggBot.guild.getMemberById(farmer.discordUser.discordId)?.nickname
                    ?.let { nickname -> " ($nickname)" } ?: ""

                "`${farmer.inGameName}` belongs to `@$discordUserName$nickname`".let {
                    event.reply(it)
                    return@transaction
                }
            }


            "No farmers or discord users found by the name of `$name`.".let {
                event.reply(it)
                log.debug { it }
                return@transaction
            }
        }
    }
}

