package nl.pindab0ter.eggbot.view

import com.auxbrain.ei.Contract
import com.auxbrain.ei.CoopStatusResponse
import nl.pindab0ter.eggbot.EggBot.toEmote
import nl.pindab0ter.eggbot.helpers.*
import nl.pindab0ter.eggbot.helpers.BigDecimal.Companion.SIXTY
import nl.pindab0ter.eggbot.helpers.HabsStatus.BottleneckReached
import nl.pindab0ter.eggbot.helpers.HabsStatus.MaxedOut
import nl.pindab0ter.eggbot.helpers.NumberFormatter.OPTIONAL_DECIMALS
import nl.pindab0ter.eggbot.model.Config
import nl.pindab0ter.eggbot.model.Table
import nl.pindab0ter.eggbot.model.Table.AlignedColumn.Alignment.LEFT
import nl.pindab0ter.eggbot.model.Table.AlignedColumn.Alignment.RIGHT
import nl.pindab0ter.eggbot.model.simulation.CoopContractState
import nl.pindab0ter.eggbot.model.simulation.Farmer
import org.joda.time.Duration
import kotlin.random.Random


fun coopInfoResponse(
    state: CoopContractState,
    compact: Boolean = false,
): List<String> = buildString {
    val bottleneckedFarmers = state.farmers.zip(state.farmers.shortenedNames()).filter { (farmer, _) ->
        willReachBottleneckBeforeDone(farmer, state.timeRemaining, state.goals.last().moment)
    }

    appendLine("`${state.coopId}` vs. _${state.contractName}_:")

    drawGoals(state, compact)
    appendBreakpoint()
    drawBasicInfo(state, compact)
    appendBreakpoint()

    if (!compact) {
        drawMembers(state)
        if (bottleneckedFarmers.isNotEmpty()) {
            appendBreakpoint()
            drawBottleNecks(state, bottleneckedFarmers)
        }
    } else {
        drawCompactMembers(state)
        appendBreakpoint()
        drawCompactTokens(state)
        if (bottleneckedFarmers.isNotEmpty()) {
            appendBreakpoint()
            drawCompactBottleNecks(state, bottleneckedFarmers)
        }
    }
}.splitMessage(separator = BREAKPOINT)

fun coopFinishedIfBankedResponse(
    state: CoopContractState,
    compact: Boolean,
): List<String> = buildString {
    appendLine("`${state.coopId}` vs. _${state.contractName}_:")
    drawGoals(state, compact)
    appendBreakpoint()
    drawBasicInfo(state, compact)
    appendBreakpoint()

    if (!compact) {
        drawMembers(state)
        appendBreakpoint()
        drawTimeSinceLastBackup(state)
    } else {
        drawCompactMembers(state)
        appendBreakpoint()
        drawCompactTimeSinceLastBackup(state)
    }
}.splitMessage(separator = BREAKPOINT)

fun coopFinishedResponse(
    status: CoopStatusResponse,
    contract: Contract,
    compact: Boolean,
) = buildString {
    append("""
        `${status.coopId}` vs. __${contract.name}__:

        This co-op has successfully finished their contract! ${Config.emojiSuccess}

        """.trimIndent())

    appendTable {
        title = "__**🗒️ Basic info**__"
        displayHeaders = false
        topPadding = 1

        column {
            alignment = LEFT
            rightPadding = 1

            cells = buildList list@{
                add("Time remaining:")
                add("Banked eggs:")
                if (status.public) add("Access:")
            }
        }

        column {
            alignment = if (!compact) LEFT else RIGHT

            val eggsLaid = status.contributors.sumByBigDecimal { contributor ->
                contributor.contributionAmount.toBigDecimal()
            }
            val eggsLaidRate = status.contributors.sumByBigDecimal { contributor ->
                contributor.contributionRate.toBigDecimal()
            }.multiply(SIXTY)

            cells = buildList {
                add(status.timeRemaining.asDaysHoursAndMinutes(compact, compact))
                add(eggsLaid.asIllions() + if (!compact) " (${eggsLaidRate.asIllions()}/hr)" else "")
                if (status.public) add("This co-op is PUBLIC")
            }
        }
    }

    appendTable {
        title = "__${contract.egg.toEmote()} **Goals** (${contract.goals.count()}/${contract.goals.count()})__"
        displayHeaders = false
        topPadding = 1

        incrementColumn(suffix = ".")
        column {
            leftPadding = 1
            cells = contract.goals.map { goal -> goal.targetAmount.toBigDecimal().asIllions(OPTIONAL_DECIMALS) }
        }
        column {
            leftPadding = 1
            rightPadding = 1
            cells = contract.goals.map { "🏁" }
        }
        column {
            cells = contract.goals.map { "Goal reached!" }
        }
    }

    appendTable {
        val memberEmoji = when (Random.nextBoolean()) {
            true -> "👨‍🌾"
            false -> "👩‍🌾"
        }
        title = "__**${memberEmoji} Members** (${status.contributors.count()}/${contract.maxCoopSize})__"
        topPadding = 1

        incrementColumn { suffix = "." }

        column {
            header = "Name"
            leftPadding = 1
            rightPadding = 1

            val shortenedNames by lazy {
                status.contributors.map { contributor ->
                    contributor.userName.let { name ->
                        if (name.length <= 10) name
                        else "${name.substring(0 until 9)}…"
                    }
                }
            }

            cells = if (compact) shortenedNames else status.contributors.map { contributor ->
                contributor.userName
            }
        }

        column {
            header = "Banked"
            alignment = RIGHT
            cells = status.contributors.map { contributor ->
                contributor.contributionAmount.toBigDecimal().asIllions()
            }
        }

        divider()

        column {
            header = "/hr"
            alignment = LEFT
            cells = status.contributors.map { contributor ->
                contributor.contributionRate.toBigDecimal().multiply(SIXTY).asIllions()
            }
        }
    }
}.splitMessage(separator = BREAKPOINT)

private fun List<Farmer>.shortenedNames(): List<String> = map { farmer ->
    farmer.name.let { name ->
        if (name.length <= 10) name
        else "${name.substring(0 until 9)}…"
    }
}

private fun StringBuilder.drawGoals(
    state: CoopContractState,
    compact: Boolean,
): StringBuilder = appendTable {
    title = "__${state.egg.toEmote()} **Goals** (${state.goalsReached}/${state.goals.count()})__"
    displayHeaders = false
    topPadding = 1

    incrementColumn(suffix = ".")
    column {
        leftPadding = 1
        cells = state.goals.map { (target, _) -> target.asIllions(OPTIONAL_DECIMALS) }
    }
    column {
        leftPadding = 1
        rightPadding = 1
        cells = state.goals.map { (_, moment) ->
            when {
                moment == null || moment > state.timeRemaining -> "🔴"
                moment == Duration.ZERO -> "🏁"
                else -> "🟢"
            }
        }
    }
    column {
        cells = state.goals.map { (_, moment) ->
            when (moment) {
                null -> "More than a year"
                Duration.ZERO -> "Goal reached!"
                else -> moment.asDaysHoursAndMinutes(compact)
            }
        }
    }
}

private fun StringBuilder.drawBasicInfo(
    state: CoopContractState,
    compact: Boolean,
): StringBuilder = appendTable {
    title = "__**🗒️ Basic info**__"
    displayHeaders = false
    topPadding = 1

    column {
        alignment = LEFT
        rightPadding = 1

        cells = buildList list@{
            if (!state.finishedIfBanked) {
                add("Time remaining:")
                add("Eggspected:")
            }
            add("Current eggs:")
            add("Banked eggs:")
            add("Unbanked eggs:")
            add("Current chickens:")
            add("Tokens available:")
            add("Tokens spent:")
            if (state.public) add("Access:")
        }
    }

    column {
        alignment = if (!compact) LEFT else RIGHT

        cells = buildList {
            if (!state.finishedIfBanked) {
                add(state.timeRemaining.asDaysHoursAndMinutes(compact, compact))
                add(state.timeUpEggsLaid.asIllions())
            }
            add(state.currentEggsLaid.asIllions() + if (!compact)
                " (${state.currentEggsPerMinute.multiply(SIXTY).asIllions()}/hr)" else "")
            add(state.reportedEggsLaid.asIllions())
            add(state.unreportedEggsLaid.asIllions())
            add(state.currentPopulation.asIllions() + if (!compact)
                " (${state.currentPopulationIncreasePerMinute.multiply(SIXTY).asIllions()}/hr)" else "")
            add(state.tokensAvailable.toString())
            add(state.tokensSpent.toString())
            if (state.public) add("This co-op is PUBLIC")
        }
    }
}

private fun StringBuilder.drawMembers(
    state: CoopContractState,
): StringBuilder = appendTable {
    val memberEmoji = when (Random.nextBoolean()) {
        true -> "👨‍🌾"
        false -> "👩‍🌾"
    }
    title = "__**${memberEmoji} Members** (${state.farmers.count()}/${state.maxCoopSize})__"
    topPadding = 1

    incrementColumn { suffix = "." }

    column {
        header = "Name"
        leftPadding = 1
        rightPadding = 2
        cells = state.farmers.map { farmer -> "${farmer.name}${if (farmer.isSleeping) " zZ" else ""}" }
    }

    column {
        header = "Current"
        alignment = RIGHT
        cells = state.farmers.map { farmer -> farmer.currentEggsLaid.asIllions() }
    }

    divider()

    column {
        header = "Banked"

        alignment = RIGHT
        leftPadding = 1

        cells = state.farmers.map { farmer -> farmer.reportedEggsLaid.asIllions() }
    }

    divider()

    overtakersColumn(state)

    column {
        header = "/hr"
        rightPadding = 2
        cells = state.farmers.map { farmer -> farmer.currentEggsPerMinute.multiply(SIXTY).asIllions() }
    }

    column {
        header = "Chickens"
        alignment = RIGHT
        cells = state.farmers.map { farmer -> farmer.currentChickens.asIllions() }
    }

    divider()

    column {
        header = "/hr"
        rightPadding = 2
        cells = state.farmers.map { farmer ->
            farmer.currentChickenIncreasePerMinute.multiply(SIXTY).asIllions()
        }
    }

    column {
        header = "Tkns"
        alignment = RIGHT
        cells = state.farmers.map { farmer ->
            if (farmer.constants.tokensAvailable > 0) "${farmer.constants.tokensAvailable}" else ""
        }
    }

    divider()

    column {
        header = "Spent"
        cells = state.farmers.map { farmer ->
            if (farmer.constants.tokensSpent > 0) "${farmer.constants.tokensSpent}" else ""
        }
    }
}

private fun StringBuilder.drawCompactMembers(
    state: CoopContractState,
): StringBuilder = appendTable {
    val memberEmoji = when (Random.nextBoolean()) {
        true -> "👨‍🌾"
        false -> "👩‍🌾"
    }
    title = "__**${memberEmoji} Members** (${state.farmers.count()}/${state.maxCoopSize})__"
    topPadding = 1

    column {
        header = "Name"
        rightPadding = 1
        cells = state.farmers.zip(state.farmers.shortenedNames()).map { (farmer, name) ->
            "$name${if (farmer.isSleeping) " zZ" else ""}"
        }
    }

    overtakersColumn(state) {
        rightPadding = 2
    }

    column {
        header = "Current"
        alignment = RIGHT
        cells = state.farmers.map { farmer -> farmer.currentEggsLaid.asIllions() }
    }

    divider()

    column {
        header = "Banked"

        alignment = LEFT

        cells = state.farmers.map { farmer -> farmer.reportedEggsLaid.asIllions() }
    }
}

private fun Table.overtakersColumn(state: CoopContractState, init: Table.EmojiColumn.() -> Unit = {}) {
    val overtakers: List<String> = state.farmers.map { farmer ->
        when {
            state.farmers.any { other ->
                farmer.currentEggsLaid < other.currentEggsLaid && farmer.timeUpEggsLaid > other.timeUpEggsLaid
            } -> "⬆️"
            state.farmers.any { other ->
                farmer.currentEggsLaid > other.currentEggsLaid && farmer.timeUpEggsLaid < other.timeUpEggsLaid
            } -> "⬇️"
            else -> "➖"
        }
    }

    if (overtakers.any { it != "➖" }) emojiColumn {
        header = if (Random.nextBoolean()) "🏃‍♂️" else "🏃‍♀️"
        cells = overtakers
        init()
    }
}

private fun StringBuilder.drawBottleNecks(
    state: CoopContractState,
    bottleneckedFarmers: List<Pair<Farmer, String>>,
): StringBuilder = appendTable {
    title = "__**⚠ Bottlenecks**__"
    topPadding = 1

    column {
        header = "Name"
        rightPadding = 2
        cells = bottleneckedFarmers.map { (farmer, _) -> farmer.name }
    }

    column {
        header = "Habs"
        leftPadding = 1
        alignment = RIGHT
        cells = bottleneckedFarmers.map { (farmer, _) ->
            when (farmer.runningState.habsStatus) {
                is BottleneckReached -> {
                    val moment = farmer.runningState.habsStatus.moment
                    when {
                        moment == Duration.ZERO -> "Full!"
                        moment < state.timeRemaining && moment < state.timeTillFinalGoal ?: ONE_YEAR ->
                            moment.asDaysHoursAndMinutes(compact = true, spacing = true)
                        else -> ""
                    }
                }
                is MaxedOut -> when (farmer.runningState.habsStatus.moment) {
                    Duration.ZERO -> "Maxed!"
                    else -> farmer.runningState.habsStatus.moment.asDaysHoursAndMinutes(compact = true, spacing = true)
                }
                else -> ""
            }
        }
    }

    emojiColumn {
        header = "🏘️"
        leftPadding = 1
        cells = bottleneckedFarmers.map { (farmer, _) ->
            when (farmer.runningState.habsStatus) {
                is MaxedOut -> "🟢"
                is BottleneckReached -> {
                    val moment = farmer.runningState.habsStatus.moment
                    when {
                        moment == Duration.ZERO -> "🛑"
                        moment < state.timeRemaining && moment < state.timeTillFinalGoal ?: ONE_YEAR -> "⚠️"
                        else -> "➖"
                    }
                }
                else -> "➖"
            }
        }
    }

    divider()

    column {
        header = "Transport"
        leftPadding = 1
        alignment = RIGHT
        cells = bottleneckedFarmers.map { (farmer, _) ->
            val moment = farmer.runningState.transportBottleneck
            when {
                moment == null -> ""
                moment == Duration.ZERO -> "Full!"
                moment < state.timeRemaining && moment < state.timeTillFinalGoal ?: ONE_YEAR ->
                    moment.asDaysHoursAndMinutes(compact = true, spacing = true)
                else -> ""
            }
        }
    }

    emojiColumn {
        header = "🚛"
        leftPadding = 1
        cells = bottleneckedFarmers.map { (farmer, _) ->
            val moment = farmer.runningState.transportBottleneck
            when {
                moment == null -> "➖"
                moment == Duration.ZERO -> "🛑"
                moment < state.timeRemaining && moment < state.timeTillFinalGoal ?: ONE_YEAR -> "⚠️"
                else -> "➖"
            }
        }
    }

    divider()

    column {
        header = "Silos"
        leftPadding = 1
        alignment = RIGHT
        cells = bottleneckedFarmers.map { (farmer, _) ->
            when {
                farmer.awayTimeRemaining <= Duration.ZERO -> "Empty!"
                farmer.awayTimeRemaining < Duration.standardHours(12L) ->
                    farmer.awayTimeRemaining.asDaysHoursAndMinutes(compact = true, spacing = true)
                else -> ""
            }
        }
    }

    emojiColumn {
        header = "⌛"
        leftPadding = 1
        cells = bottleneckedFarmers.map { (farmer, _) ->
            when {
                farmer.awayTimeRemaining <= Duration.ZERO ->
                    "🛑"
                farmer.awayTimeRemaining < Duration.standardHours(12L)
                        && farmer.awayTimeRemaining < state.timeRemaining
                        && farmer.awayTimeRemaining < state.timeTillFinalGoal ?: ONE_YEAR ->
                    "⚠️"
                else -> "➖"
            }
        }
    }

    divider(intersection = '╡')
}

private fun StringBuilder.drawCompactBottleNecks(
    state: CoopContractState,
    bottleneckedFarmers: List<Pair<Farmer, String>>,
): StringBuilder = appendTable {
    title = "__**⚠ Bottlenecks**__"
    topPadding = 1

    column {
        header = "Name"
        cells = bottleneckedFarmers.map { (_, shortenedName) -> shortenedName }
    }

    emojiColumn {
        header = "🏘️"
        leftPadding = 1
        cells = bottleneckedFarmers.map { (farmer, _) ->
            when (farmer.runningState.habsStatus) {
                is MaxedOut -> "🟢"
                is BottleneckReached -> {
                    val moment = farmer.runningState.habsStatus.moment
                    when {
                        moment == Duration.ZERO -> "🛑"
                        moment < state.timeRemaining && moment < state.timeTillFinalGoal ?: ONE_YEAR -> "⚠️"
                        else -> "➖"
                    }
                }
                else -> "➖"
            }
        }
    }

    divider()

    emojiColumn {
        header = "🚛"
        cells = bottleneckedFarmers.map { (farmer, _) ->
            val moment = farmer.runningState.transportBottleneck ?: Duration.ZERO
            when {
                moment == Duration.ZERO -> "🛑"
                moment < state.timeRemaining && moment < state.timeTillFinalGoal ?: ONE_YEAR -> "⚠️"
                else -> "➖"
            }
        }
    }

    divider()

    emojiColumn {
        header = "⌛"
        cells = bottleneckedFarmers.map { (farmer, _) ->
            when {
                farmer.awayTimeRemaining == Duration.ZERO -> "🛑"
                farmer.awayTimeRemaining < Duration.standardHours(12L)
                        && farmer.awayTimeRemaining < state.timeRemaining
                        && farmer.awayTimeRemaining < state.timeTillFinalGoal ?: ONE_YEAR ->
                    "⚠️"
                else -> "➖"
            }
        }
    }
}

private fun StringBuilder.drawCompactTokens(
    state: CoopContractState,
): StringBuilder = appendTable {
    title = "__**🎫 Tokens**__"
    topPadding = 1

    column {
        header = "Name"
        rightPadding = 2
        cells = state.farmers.shortenedNames()
    }
    column {
        header = "Tokens"
        alignment = RIGHT
        cells = state.farmers.map { farmer -> "${farmer.constants.tokensAvailable}" }
    }
    divider()
    column {
        header = "Spent"
        cells = state.farmers.map { farmer -> "${farmer.constants.tokensSpent}" }
    }
}

private fun StringBuilder.drawTimeSinceLastBackup(
    state: CoopContractState,
): StringBuilder = appendTable {
    title = "__**🎉 Bank now to finish!**__"
    topPadding = 1

    val farmersSortedByUnreportedEggsLaid = state.farmers.sortedByDescending { farmer -> farmer.unreportedEggsLaid }

    column {
        header = "Name"
        cells = farmersSortedByUnreportedEggsLaid.map { farmer ->
            farmer.name
        }
    }

    column {
        header = "Last update"
        alignment = RIGHT
        leftPadding = 2
        cells = farmersSortedByUnreportedEggsLaid.map { farmer ->
            "${farmer.timeSinceBackup.asDaysHoursAndMinutes(compact = true, spacing = true)} ago"
        }
    }

    divider()

    column {
        header = "Unbanked"
        alignment = RIGHT
        leftPadding = 1
        cells = farmersSortedByUnreportedEggsLaid.map { farmer ->
            farmer.unreportedEggsLaid.asIllions()
        }
    }

    divider()
}

private fun StringBuilder.drawCompactTimeSinceLastBackup(
    state: CoopContractState,
): StringBuilder = appendTable {
    title = "__**🎉 Bank now!**__"
    topPadding = 1

    val sortedFarmers = state.farmers.sortedByDescending { farmer -> farmer.timeSinceBackup }

    column {
        header = "Name"
        rightPadding = 1
        cells = sortedFarmers.shortenedNames()
    }

    column {
        header = "Last update"
        leftPadding = 1
        alignment = RIGHT
        cells = sortedFarmers.map { farmer ->
            "${farmer.timeSinceBackup.asDaysHoursAndMinutes(true, spacing = true)} ago"
        }
    }
}
