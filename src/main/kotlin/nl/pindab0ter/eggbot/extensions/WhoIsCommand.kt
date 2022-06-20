package nl.pindab0ter.eggbot.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.suggestStringMap
import dev.kord.common.annotation.KordPreview
import dev.kord.core.entity.User
import nl.pindab0ter.eggbot.config
import nl.pindab0ter.eggbot.converters.optionalFarmer
import nl.pindab0ter.eggbot.databases
import nl.pindab0ter.eggbot.helpers.getMemberOrNull
import nl.pindab0ter.eggbot.helpers.toListing
import nl.pindab0ter.eggbot.model.database.DiscordUsers
import nl.pindab0ter.eggbot.model.database.Farmer
import nl.pindab0ter.eggbot.model.database.Farmers
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

@KordPreview
class WhoIsCommand : Extension() {
    override val name: String = javaClass.simpleName

    override suspend fun setup() = config.servers.forEach { server ->
        class WhoIsThisArguments : Arguments() {
            val discordUser: User? by optionalUser {
                name = "member"
                description = "Find out which farmers this member has registered."
            }
            val farmer: Farmer? by optionalFarmer {
                name = "farmer"
                description = "Find out which member has registered this farmer."
                database = databases[server.name]

                autoComplete {
                    val farmerInput: String = command.options["farmer"]?.value as String

                    val farmers = transaction(databases[server.name]) {
                        val query = Farmers
                            .innerJoin(DiscordUsers)
                            .select { Farmers.inGameName like "%$farmerInput%" }
                            .orderBy(Farmers.inGameName to ASC)
                            .limit(25)

                        Farmer.wrapRows(query).associate { farmer ->
                            farmer.inGameName!! to farmer.eggIncId
                        }
                    }

                    suggestStringMap(farmers)
                }
            }
        }

        publicSlashCommand(::WhoIsThisArguments) {
            name = "whoisthis"
            description = "Find out who is who."
            guild(server.snowflake)

            action {
                when {
                    arguments.discordUser != null && arguments.farmer != null -> {
                        respond { content = "You can't specify both a member and a farmer." }
                    }

                    arguments.discordUser != null -> {
                        val farmers = transaction(databases[server.name]) {
                            Farmer.find { Farmers.discordUserId eq arguments.discordUser?.id.toString() }.toList()
                        }

                        respond {
                            content =
                                if (farmers.isNotEmpty()) "${arguments.discordUser?.mention} has: ${farmers.toListing()}"
                                else "${arguments.discordUser?.mention} has not registered any farmers."
                        }
                    }

                    arguments.farmer != null -> {
                        val discordUser = arguments.farmer?.discordId?.let { guild.getMemberOrNull(it) }

                        respond {
                            content =
                                if (discordUser != null) "${arguments.farmer?.inGameName} is registered by ${discordUser.mention}"
                                else "**Error:** Failed to find member for ${arguments.farmer?.inGameName}."
                        }
                    }
                    else -> {
                        respond { content = "Please specify a member or a farmer." }
                    }
                }
            }
        }
    }
}
