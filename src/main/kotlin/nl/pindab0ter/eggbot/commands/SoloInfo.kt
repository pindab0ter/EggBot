package nl.pindab0ter.eggbot.commands

import com.jagrosh.jdautilities.command.CommandEvent
import com.martiansoftware.jsap.JSAPResult
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.ChannelType
import nl.pindab0ter.eggbot.Config
import nl.pindab0ter.eggbot.EggBot.botCommandsChannel
import nl.pindab0ter.eggbot.EggBot.eggsToEmotes
import nl.pindab0ter.eggbot.commands.categories.ContractsCategory
import nl.pindab0ter.eggbot.database.DiscordUser
import nl.pindab0ter.eggbot.database.Farmer
import nl.pindab0ter.eggbot.jda.EggBotCommand
import nl.pindab0ter.eggbot.network.AuxBrain
import nl.pindab0ter.eggbot.simulation.ContractSimulation
import nl.pindab0ter.eggbot.utilities.*
import nl.pindab0ter.eggbot.utilities.NumberFormatter.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.Duration

object SoloInfo : EggBotCommand() {

    private val log = KotlinLogging.logger { }

    init {
        category = ContractsCategory
        name = "solo"
        help = "Shows the progress of a contract you're not in a co-op for."
        parameters = listOf(
            contractIdOption,
            compactSwitch
        )
        sendTyping = true
        init()
    }

    @Suppress("FoldInitializerAndIfToElvis")
    override fun execute(event: CommandEvent, parameters: JSAPResult) {
        val farmers = transaction { DiscordUser.findById(event.author.id)?.farmers?.toList()!! }
        val contractId: String = parameters.getString(CONTRACT_ID)
        val compact: Boolean = parameters.getBoolean(COMPACT)

        for (farmer: Farmer in farmers) AuxBrain.getFarmerBackup(farmer.inGameId)?.let { backup ->
            val localContract = if (backup.game != null) backup.contracts?.contracts?.find {
                it.contract?.id == contractId
            } else "No data found for `${farmer.inGameName}`.".let {
                log.warn { it }
                event.reply(it)
                return
            }

            if (localContract == null)
                "No contract found with ID `$contractId` for `${farmer.inGameName}`. Try using `${event.client.textualPrefix}${ContractIDs.name}`".let {
                    log.debug { it }
                    event.reply(it)
                    return
                }
            if (localContract.contract?.coopAllowed == true && localContract.coopId.isNotBlank())
                "The contract with ID `$contractId` is not a solo contract.".let {
                    log.debug { it }
                    event.reply(it)
                    return
                }
            val simulation = ContractSimulation(backup, contractId)
                ?: "You haven't started this contract yet.".let {
                    log.debug { it }
                    event.reply(it)
                    return
                }

            simulation.run()

            message(simulation, compact).let { message ->
                if (event.channel == botCommandsChannel) {
                    event.reply(message)
                } else {
                    event.replyInDm(message)
                    if (event.isFromType(ChannelType.TEXT)) event.reactSuccess()
                }
            }
        }
    }

    fun message(
        simulation: ContractSimulation,
        compact: Boolean = false
    ): String = StringBuilder().apply stringBuilder@{
        val eggEmote = eggsToEmotes[simulation.egg]?.asMention ?: "🥚"

        appendLine("`${simulation.farmerName}` vs. _${simulation.contractName}_:")
        appendLine()

        if (simulation.finished) {
            appendLine("**You have successfully finished this contract! ${Config.emojiSuccess}**")
            return@stringBuilder
        }

        // region Goals

        appendLine("__$eggEmote **Goals** (${simulation.goalsReached}/${simulation.goals.count()}):__ ```")
        simulation.goalReachedMoments.forEachIndexed { index, (goal, moment) ->
            append("${index + 1}. ")
            appendPaddingCharacters(
                goal.asIllions(OPTIONAL_DECIMALS),
                simulation.goalReachedMoments.map { it.target.asIllions(OPTIONAL_DECIMALS) }
            )
            append(goal.asIllions(OPTIONAL_DECIMALS))
            append(
                when {
                    moment == null || moment > simulation.timeRemaining -> " 🔴 "
                    moment == Duration.ZERO -> " 🏁 "
                    else -> " 🟢 "
                }
            )
            when (moment) {
                null -> append("More than a year")
                Duration.ZERO -> append("Goal reached!")
                else -> append(moment.asDaysHoursAndMinutes(compact))
            }
            if (index + 1 < simulation.goals.count()) appendLine()
        }
        appendLine("```")

        // endregion Goals

        // region Basic info and totals

        appendLine("__🗒️ **Basic info**__ ```")
        simulation.apply {
            this@stringBuilder.appendLine("Eggspected:       ${eggspected.asIllions()}")
            this@stringBuilder.appendLine("Time remaining:   ${timeRemaining.asDaysHoursAndMinutes(compact)}")
            append("Current chickens: ${currentPopulation.asIllions()} ")
            if (!compact) append("(${populationIncreasePerHour.asIllions()}/hr)")
            this@stringBuilder.appendLine()
            append("Current eggs:     ${currentEggs.asIllions()} ")
            if (!compact) append("(${(eggsPerChickenPerMinute * currentPopulation * 60).asIllions()}/hr) ")
            this@stringBuilder.appendLine()
            this@stringBuilder.appendLine("Last update:      ${timeSinceLastUpdate.asDaysHoursAndMinutes(compact)} ago")
            this@stringBuilder.appendLine("```")
        }

        // endregion Basic info and totals

        // region Bottlenecks

        simulation.apply {
            if (habBottleneckReached != null || transportBottleneckReached != null) {
                this@stringBuilder.appendLine("__**⚠ Bottlenecks**__ ```")
                habBottleneckReached?.let {
                    if (it == Duration.ZERO) append("🏠Full! ")
                    else append("🏠${it.asDaysHoursAndMinutes(true)} ")
                }
                transportBottleneckReached?.let {
                    if (it == Duration.ZERO) append("🚛Full! ")
                    else append("🚛${it.asDaysHoursAndMinutes(true)} ")
                }
                this@stringBuilder.appendLine("```")
            }
        }

        // endregion Bottlenecks

    }.toString()
}
