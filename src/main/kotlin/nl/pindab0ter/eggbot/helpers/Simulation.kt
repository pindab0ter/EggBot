package nl.pindab0ter.eggbot.helpers

import nl.pindab0ter.eggbot.model.simulation.new.Constants
import nl.pindab0ter.eggbot.model.simulation.new.FarmState
import nl.pindab0ter.eggbot.model.simulation.new.Farmer
import nl.pindab0ter.eggbot.model.simulation.new.Hab
import org.joda.time.Duration
import java.math.BigDecimal

/** Base egg laying rate per chicken per minute.
 *
 * A chicken lays 1/30 of an egg per second, so 2 per minute */
private val EGG_LAYING_BASE_RATE = BigDecimal(2)

tailrec fun catchUp(
    state: FarmState,
    constants: Constants,
    catchupTimeLeft: Duration,
): FarmState = when {
    catchupTimeLeft <= Duration.ZERO -> state
    else -> catchUp(
        state = advanceOneMinute(state, constants),
        constants = constants,
        catchupTimeLeft = catchupTimeLeft - ONE_MINUTE
    )
}

// TODO: Add boosts
fun advanceOneMinute(state: FarmState, constants: Constants, elapsed: Duration = Duration.ZERO): FarmState = state.copy(
    eggsLaid = state.eggsLaid + eggIncrease(state.habs, constants),
    habs = state.habs.map { hab ->
        hab.copy(population = minOf(hab.population + chickenIncrease(state.habs, constants), hab.capacity))
    },
    habBottleneck = state.habBottleneck ?: habBottleneck(state.habs, elapsed),
    transportBottleneck = state.transportBottleneck ?: transportBottleneck(state.habs, constants, elapsed)
)

fun transportBottleneck(habs: List<Hab>, constants: Constants, elapsed: Duration): Duration? {
    return when {
        eggIncrease(habs, constants) >= constants.transportRate -> elapsed
        else -> null
    }
}

fun habBottleneck(habs: List<Hab>, elapsed: Duration): Duration? {
    return when {
        habs.all { (population, capacity) -> population == capacity } -> elapsed
        else -> null
    }
}

fun chickenIncrease(habs: List<Hab>, constants: Constants): BigDecimal =
    constants.internalHatcheryRate.multiply(
        BigDecimal.ONE + habs.fullCount().multiply(constants.internalHatcherySharing)
    )

fun List<Hab>.fullCount(): BigDecimal {
    return sumByBigDecimal { (population, capacity) ->
        if (population >= capacity) BigDecimal.ONE
        else BigDecimal.ZERO
    }
}

fun eggIncrease(habs: List<Hab>, constants: Constants): BigDecimal = minOf(
    habs.sumByBigDecimal(Hab::population).multiply(EGG_LAYING_BASE_RATE).multiply(constants.eggLayingBonus),
    constants.transportRate
)

fun willReachBottlenecks(farmer: Farmer, finalGoalReachedAt: Duration?): Boolean {
    val bottlenecks = listOf(farmer.finalState.habBottleneck, farmer.finalState.transportBottleneck)
    val reachesBottlenecks = farmer.finalState.habBottleneck != null
            || farmer.finalState.transportBottleneck != null
            || farmer.awayTimeRemaining < Duration.standardHours(12L)
    return when {
        reachesBottlenecks && finalGoalReachedAt == null -> true
        reachesBottlenecks && bottlenecks.any { it?.isShorterThan(finalGoalReachedAt) == true } -> true
        else -> false
    }
}