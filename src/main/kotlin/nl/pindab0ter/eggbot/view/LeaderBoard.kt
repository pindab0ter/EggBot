package nl.pindab0ter.eggbot.view

import com.auxbrain.ei.Backup
import nl.pindab0ter.eggbot.helpers.*
import nl.pindab0ter.eggbot.helpers.DisplayMode.*
import nl.pindab0ter.eggbot.helpers.Typography.zwsp
import nl.pindab0ter.eggbot.model.Config
import nl.pindab0ter.eggbot.model.LeaderBoard
import nl.pindab0ter.eggbot.model.LeaderBoard.*
import nl.pindab0ter.eggbot.model.Table

suspend fun leaderboardResponse(
    farmers: List<Backup>,
    leaderBoard: LeaderBoard,
    top: Int? = null,
    displayMode: DisplayMode = REGULAR,
): List<String> = table {
    val compact = displayMode == COMPACT

    val sortedFarmers = when (leaderBoard) {
        EARNINGS_BONUS -> farmers.sortedByDescending { farmer -> farmer.game?.earningsBonus }
        SOUL_EGGS -> farmers.sortedByDescending { farmer -> farmer.game?.soulEggs }
        PROPHECY_EGGS -> farmers.sortedByDescending { farmer -> farmer.game?.prophecyEggs }
        PRESTIGES -> farmers.sortedByDescending { farmer -> farmer.stats?.prestigeCount }
        DRONE_TAKEDOWNS -> farmers.sortedByDescending { farmer -> farmer.stats?.droneTakedowns }
        ELITE_DRONE_TAKEDOWNS -> farmers.sortedByDescending { farmer -> farmer.stats?.droneTakedownsElite }
    }.let { sortedFarmers -> if (top != null) sortedFarmers.take(top) else sortedFarmers }

    val shortenedNames = sortedFarmers.map { farmer ->
        farmer.userName.let { name ->
            if (name.length <= 10) name
            else "${name.substring(0 until 9)}…"
        }
    }

    val boardTitle = when (leaderBoard) {
        EARNINGS_BONUS -> "💵 Earnings Bonus"
        SOUL_EGGS -> "${emoteMention(Config.emoteSoulEgg) ?: "🥚"} Soul Eggs"
        PROPHECY_EGGS -> "${emoteMention(Config.emoteProphecyEgg) ?: "🥚"} Prophecy Eggs"
        PRESTIGES -> "🥨 Prestiges"
        DRONE_TAKEDOWNS -> "✈🚫 Drone Takedowns"
        ELITE_DRONE_TAKEDOWNS -> "🎖✈🚫 Elite Drone Takedowns"
    }

    title = "__**$boardTitle${if (!compact) " Leader Board" else ""}**__"
    displayHeaders = true
    if (compact) incrementColumn() else incrementColumn(":")
    column {
        header = "Name"
        leftPadding = 1
        rightPadding = if (compact) 1 else 2
        cells = if (compact) shortenedNames else sortedFarmers.map { farmer -> farmer.userName }
    }

    column {
        header = when (leaderBoard) {
            EARNINGS_BONUS -> "Earnings Bonus" + if (compact) "" else "  " // Added spacing for percent suffix
            SOUL_EGGS -> "Soul Eggs"
            PROPHECY_EGGS -> "Prophecy Eggs"
            PRESTIGES -> "Prestiges"
            DRONE_TAKEDOWNS -> "Drone Takedowns"
            ELITE_DRONE_TAKEDOWNS -> "Elite Drone Takedowns"
        }

        alignment = Table.AlignedColumn.Alignment.RIGHT

        cells = when (leaderBoard) {
            EARNINGS_BONUS -> sortedFarmers.map { farmer ->
                when (displayMode) {
                    EXTENDED -> "${farmer.game?.earningsBonus?.formatInteger()}$zwsp%"
                    else -> "${farmer.game?.earningsBonus?.formatIllions(shortened = compact)}${if (compact) "" else "$zwsp%"}"
                }
            }
            SOUL_EGGS -> sortedFarmers.map { farmer ->
                when (displayMode) {
                    EXTENDED -> farmer.game?.soulEggs?.toBigDecimal()?.formatInteger()
                    else -> farmer.game?.soulEggs?.toBigDecimal()?.formatIllions(shortened = compact)
                } ?: ""
            }
            PROPHECY_EGGS -> sortedFarmers.map { farmer -> farmer.game?.prophecyEggs?.formatInteger() ?: "" }
            PRESTIGES -> sortedFarmers.map { farmer -> farmer.stats?.prestigeCount?.formatInteger() ?: "" }
            DRONE_TAKEDOWNS -> sortedFarmers.map { farmer -> farmer.stats?.droneTakedowns?.formatInteger() ?: "" }
            ELITE_DRONE_TAKEDOWNS -> sortedFarmers.map { farmer -> farmer.stats?.droneTakedownsElite?.formatInteger() ?: "" }
        }
    }

    if (leaderBoard == EARNINGS_BONUS) column {
        header = if (compact) "Role" else "Farmer Role"
        leftPadding = if (compact) 1 else 2
        cells = sortedFarmers.map { farmer -> farmer.game?.earningsBonus?.formatRank(shortened = compact) ?: "" }
    }
}

