package nl.pindab0ter.eggbot.extensions

import com.auxbrain.ei.Contract
import com.kotlindiscord.kord.extensions.checks.hasRole
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission.ManageChannels
import dev.kord.common.entity.Permission.ManageRoles
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.createRole
import dev.kord.core.behavior.createTextChannel
import dev.kord.rest.request.RestRequestException
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import nl.pindab0ter.eggbot.DEFAULT_ROLE_COLOR
import nl.pindab0ter.eggbot.config
import nl.pindab0ter.eggbot.databases
import nl.pindab0ter.eggbot.extensions.RollCallExtension.DeletionStatus.Type.CHANNEL
import nl.pindab0ter.eggbot.extensions.RollCallExtension.DeletionStatus.Type.ROLE
import nl.pindab0ter.eggbot.helpers.*
import nl.pindab0ter.eggbot.model.createRollCall
import nl.pindab0ter.eggbot.model.database.Coop
import nl.pindab0ter.eggbot.model.database.Coops
import nl.pindab0ter.eggbot.view.rollCallResponse
import org.jetbrains.exposed.sql.transactions.transaction

class RollCallExtension : Extension() {
    val logger = KotlinLogging.logger { }
    override val name: String = javaClass.simpleName

    private data class DeletionStatus(
        val type: Type,
        val deleted: Boolean,
    ) {
        enum class Type {
            CHANNEL, ROLE
        }
    }

    override suspend fun setup() {
        for (server in config.servers) publicSlashCommand {
            name = "roll-call"
            description = "Manage roll calls"
            locking = true
            guild(server.snowflake)

            check {
                hasRole(server.role.admin)
                passIf(event.interaction.user.id == config.botOwner)
            }

            class CreateRollCallArguments : Arguments() {
                val contract: Contract by contract()
                val basename: String by string {
                    name = "name"
                    description = "The base for the co-op names"
                    validate {
                        failIf("Co-op names cannot contain spaces") { value.contains(' ') }
                    }
                }
                val createRoles: Boolean by createRole()
                val createChannels: Boolean by createChannel()
            }

            class ClearRollCallArguments : Arguments() {
                val contract: Contract by contract()
            }

            publicSubCommand(::CreateRollCallArguments) {
                name = "create"
                description = "Create co-ops for a contract"
                requiredPerms += listOf(
                    ManageRoles,
                    ManageChannels,
                )

                action {
                    val coops = transaction(databases[server.name]) {
                        createRollCall(arguments.basename, arguments.contract.maxCoopSize)
                            // First create all co-ops
                            .map { (name, farmers) ->
                                Coop.new {
                                    this.contractId = arguments.contract.id
                                    this.name = name
                                }.also { it.farmers = farmers }
                            }
                            // Then create roles and channels for all the successfully created co-ops
                            .onEach { coop ->
                                // TODO: Progress bar?

                                runBlocking {
                                    // Create and assign roles
                                    if (arguments.createRoles) {
                                        val role = guild?.createRole {
                                            name = coop.name
                                            mentionable = true
                                            color = DEFAULT_ROLE_COLOR
                                        }

                                        if (role != null) {
                                            coop.roleId = role.id
                                            coop.farmers.forEach { farmer ->
                                                guild?.getMemberOrNull(farmer.discordUser.snowflake)
                                                    ?.addRole(role.id, "Roll call for ${arguments.contract.name}")
                                            }
                                        } else {
                                            this@publicSubCommand.logger.error { "Failed to create role for co-op ${coop.name}" }
                                        }
                                    }

                                    // Create and assign channel
                                    if (arguments.createChannels) {
                                        val channel = guild?.createTextChannel(coop.name) {
                                            parentId = server.channel.coopsGroup
                                            reason = "Roll call for ${arguments.contract.name}"
                                        }
                                        coop.channelId = channel?.id
                                    }
                                }
                            }
                    }

                    guild?.rollCallResponse(arguments.contract, coops)?.let { multipartRespond(it) }
                }
            }

            publicSubCommand(::ClearRollCallArguments) {
                name = "clear"
                description = "Remove all co-ops for a contract"
                requiredPerms += listOf(
                    ManageRoles,
                    ManageChannels,
                )

                action {
                    val coops = transaction(databases[server.name]) {
                        Coop.find { Coops.contractId eq arguments.contract.id }.toList()
                    }

                    if (coops.isEmpty()) {
                        respond { content = "No co-ops found for _${arguments.contract.name}_." }
                        return@action
                    }

                    val statuses = coops
                        .map<Coop, Pair<Coop, MutableSet<DeletionStatus>>> { coop: Coop -> coop to mutableSetOf() }
                        .map { (coop: Coop, statuses: MutableSet<DeletionStatus>) ->
                            val coopName = coop.name

                            try {
                                guild?.getChannelOrNull(coop.channelId)?.delete("Roll Call for ${arguments.contract.name} cleared by ${user.asUser().username}")
                                statuses.add(DeletionStatus(CHANNEL, true))
                            } catch (exception: RestRequestException) {
                                statuses.add(DeletionStatus(CHANNEL, false))
                                this@publicSubCommand.logger.warn { "Failed to delete channel for co-op $coopName: ${exception.localizedMessage}" }
                            }

                            try {
                                guild?.getRoleOrNull(coop.roleId)?.delete("Roll Call for ${arguments.contract.name} cleared by ${user.asUser().username}")
                                statuses.add(DeletionStatus(ROLE, true))
                            } catch (exception: RestRequestException) {
                                statuses.add(DeletionStatus(ROLE, false))
                                this@publicSubCommand.logger.warn { "Failed to delete role for co-op $coopName: ${exception.localizedMessage}" }
                            }

                            transaction(databases[server.name]) {coop.delete() }

                            coopName to statuses.toSet()
                        }

                    respond {
                        content = buildString {
                            val successfullyDeletedChannels = statuses.count { (_, statuses: Set<DeletionStatus>) ->
                                statuses.any { deletionStatus: DeletionStatus ->
                                    deletionStatus.type == CHANNEL && deletionStatus.deleted
                                }
                            }
                            val successfullyDeletedRoles = statuses.count { (_, statuses: Set<DeletionStatus>) ->
                                statuses.any { deletionStatus: DeletionStatus ->
                                    deletionStatus.type == ROLE && deletionStatus.deleted
                                }
                            }

                            appendLine("Cleared the roll-call for __${arguments.contract.name}__:")
                            appendLine("Successfully deleted $successfullyDeletedChannels channels and $successfullyDeletedRoles roles.")

                            statuses
                                .map { (coopName, statuses) ->
                                    coopName to statuses
                                        .filterNot(DeletionStatus::deleted)
                                        .map { deletionStatus -> deletionStatus.type }
                                        .sorted()
                                }
                                .filter { (_, statuses) -> statuses.isNotEmpty() }
                                .sortedWith(compareBy { it.first })
                                .let { failedToDelete ->
                                    if (failedToDelete.isNotEmpty()) appendLine("Failed to delete:")
                                    failedToDelete.forEach { (coopName, types) ->
                                        append("For `$coopName`: ")
                                        when (types.size) {
                                            1 -> append(types.first().name.lowercase())
                                            else -> types.joinToString(" and ") { type -> type.name.lowercase() }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}