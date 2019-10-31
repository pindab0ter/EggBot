package nl.pindab0ter.eggbot.commands

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import nl.pindab0ter.eggbot.commands.categories.AdminCategory
import nl.pindab0ter.eggbot.database.Coop
import nl.pindab0ter.eggbot.database.Coops
import nl.pindab0ter.eggbot.simulation.CoopContractSimulation
import nl.pindab0ter.eggbot.simulation.CoopContractSimulationResult.*
import nl.pindab0ter.eggbot.utilities.*
import org.jetbrains.exposed.sql.transactions.transaction

object CoopsInfo : Command() {

    private val log = KotlinLogging.logger { }

    init {
        name = "coops"
        aliases = arrayOf("coopsinfo", "coops-info", "co-ops", "co-ops-info")
        arguments = "<contract id>"
        help = "Shows info on all co-ops for the specified contract."
        category = AdminCategory
        guildOnly = false
    }

    @Suppress("FoldInitializerAndIfToElvis")
    override fun execute(event: CommandEvent) {
        event.channel.sendTyping().queue()

        (checkPrerequisites(
            event,
            minArguments = 1,
            maxArguments = 1
        ) as? PrerequisitesCheckResult.Failure)?.message?.let {
            event.replyWarning(it)
            log.debug { it }
            return
        }

        val contractId = event.arguments.first()
        val coops = runBlocking(Dispatchers.IO) {
            transaction {
                Coop.find { Coops.contract eq contractId }.toList().sortedBy { it.name }
            }.asyncMap { coop ->
                CoopContractSimulation.Factory(coop.contract, coop.name)
            }
        }

        if (coops.isEmpty()) "Could not find any co-ops for contract id `$contractId`.\nIs `contract id` correct and are there registered teams?".let {
            event.replyWarning(it)
            log.debug { it }
            return
        }

        event.reply("Registered co-ops for `$contractId`:\n${coops.joinToString("\n") { result ->
            when (result) {
                is NotFound -> "`${result.coopId}`: ✗ Waiting for starter" // TODO: Tag starter and/or leader
                is Abandoned -> "`${result.coopStatus.coopId}`: ✗ Abandoned"
                is InProgress -> {
                    val progress = (result.simulation.timeRemaining / result.simulation.goalReachedMoments.last().moment!!)
                        ?.asPercentage() ?: "error"
                    when {
                        result.simulation.willFinish -> "`${result.simulation.coopId}`: ✓ Will finish ($progress)"
                        else -> "`${result.simulation.coopId}`: ✗ Won't finish ($progress)"
                    }
                }
                is Failed -> "`${result.coopStatus.coopId}`: ✗ Hasn't finished"
                is Finished -> "`${result.coopStatus.coopId}`: ✓ Finished"
            }
        }}")
    }
}
