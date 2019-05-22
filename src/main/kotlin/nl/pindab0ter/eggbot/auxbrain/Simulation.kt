package nl.pindab0ter.eggbot.auxbrain

import com.auxbrain.ei.EggInc
import nl.pindab0ter.eggbot.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.MathContext.DECIMAL32

abstract class Simulation(val backup: EggInc.Backup) {

    internal abstract val farm: EggInc.Simulation

    //
    // Basic info
    //

    val farmerName: String = backup.name

    //
    // Research
    //

    internal val internalHatcheryFlatIncreases: List<BigDecimal>
        get() = listOf(
            BigDecimal(2 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY1.ordinal].level),
            BigDecimal(5 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY2.ordinal].level),
            BigDecimal(10 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY3.ordinal].level),
            BigDecimal(25 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY4.ordinal].level),
            BigDecimal(5 * farm.commonResearchList[CommonResearch.MACHINE_LEARNING_INCUBATORS.ordinal].level),
            BigDecimal(50 * farm.commonResearchList[CommonResearch.NEURAL_LINKING.ordinal].level)
        )

    internal val habCapacityMultipliers: List<BigDecimal>
        get() = listOf(
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.HEN_HOUSE_REMODEL.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.MICROLUX_CHICKEN_SUITES.ordinal].level),
            BigDecimal(1 + .02 * farm.commonResearchList[CommonResearch.GRAV_PLATING.ordinal].level),
            BigDecimal(1 + .02 * farm.commonResearchList[CommonResearch.WORMHOLE_DAMPENING.ordinal].level)
        )

    internal val internalHatcheryMultiplier: BigDecimal
        get() = BigDecimal(1 + .05 * backup.data.epicResearchList[EpicResearch.EPIC_INT_HATCHERIES.ordinal].level)

    internal val eggLayingMultipliers: List<BigDecimal>
        get() = listOf(
            BigDecimal(1 + .10 * farm.commonResearchList[CommonResearch.COMFORTABLE_NESTS.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.HEN_HOUSE_AC.ordinal].level),
            BigDecimal(1 + .15 * farm.commonResearchList[CommonResearch.IMPROVED_GENETICS.ordinal].level),
            BigDecimal(1 + .10 * farm.commonResearchList[CommonResearch.TIME_COMPRESSION.ordinal].level),
            BigDecimal(1 + .02 * farm.commonResearchList[CommonResearch.TIMELINE_DIVERSION.ordinal].level),
            BigDecimal(1 + .10 * farm.commonResearchList[CommonResearch.RELATIVITY_OPTIMIZATION.ordinal].level),
            BigDecimal(1 + .05 * backup.data.epicResearchList[EpicResearch.EPIC_COMFY_NESTS.ordinal].level)
        )

    internal val shippingRatePercentageIncreases: List<BigDecimal>
        get() = listOf(
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.IMPROVED_LEAFSPRINGS.ordinal].level),
            BigDecimal(1 + .10 * farm.commonResearchList[CommonResearch.LIGHTWEIGHT_BOXES.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.DRIVER_TRAINING.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.SUPER_ALLOY_FRAMES.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.QUANTUM_STORAGE.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.HOVER_UPGRADES.ordinal].level), // Assumes at least Hover Semi
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.DARK_CONTAINMENT.ordinal].level),
            BigDecimal(1 + .05 * farm.commonResearchList[CommonResearch.NEURAL_NET_REFINEMENT.ordinal].level),
            BigDecimal(1 + .05 * backup.data.epicResearchList[EpicResearch.TRANSPORTATION_LOBBYISTS.ordinal].level)
        )

    internal val internalHatcheryCalm: BigDecimal
        get() = BigDecimal(1 + 0.10 * backup.data.epicResearchList[EpicResearch.INTERNAL_HATCH_CALM.ordinal].level)

    //
    // Habitats (chicken cap)
    //

    private val habsMaxCapacityBonus: BigDecimal by lazy { habCapacityMultipliers.product() }

    // TODO: Remove unnecessary MathContext?
    val EggInc.HabLevel.maxCapacity: BigDecimal get() = capacity.multiply(habsMaxCapacityBonus, DECIMAL32)

    val habsMaxCapacity: BigDecimal by lazy { farm.habsList.sumBy { hab -> hab.maxCapacity } }

    //
    // Internal hatchery (chicken increase)
    //

    val internalHatcheryRatePerMinute: BigDecimal by lazy { (internalHatcheryFlatIncreases.sum() * internalHatcheryMultiplier) }

    // TODO: Include Internal Hatchery Sharing for full habs
    val populationIncreaseRate: BigDecimal by lazy {
        farm.habsList
            .foldIndexed(BigDecimal.ZERO) { index, acc, hab -> acc + if (hab.capacity >= farm.habPopulation[index]) BigDecimal.ONE else BigDecimal.ZERO }
            .times(internalHatcheryRatePerMinute)
    }

    //
    // Chickens
    //

    val population: BigDecimal by lazy { farm.numChickens.toBigDecimal() }

    //
    // Eggs
    //

    private val eggLayingBonus: BigDecimal by lazy { eggLayingMultipliers.product() }

    val eggsLaid by lazy { farm.eggsLaid.toBigDecimal() }

    val eggLayingBaseRatePerSecond: BigDecimal by lazy { BigDecimal.ONE.divide(BigDecimal(30), MathContext.DECIMAL64) }

    val eggLayingRatePerSecond: BigDecimal by lazy { population * eggLayingBaseRatePerSecond * eggLayingBonus }

    val eggLayingRatePerMinute: BigDecimal by lazy { population * eggLayingBaseRatePerSecond * eggLayingBonus * 60 }

    //
    // Shipping rate (max egg laying rate)
    //

    private val shippingRateBonus: BigDecimal by lazy { shippingRatePercentageIncreases.product() }

    val shippingRatePerMinute: BigDecimal by lazy {
        farm.vehiclesList.foldIndexed(BigDecimal.ZERO) { index, acc, vehicleType ->
            when (vehicleType) {
                EggInc.VehicleType.HYPERLOOP_TRAIN -> acc + vehicleType.capacity * farm.hyperloopCarsList[index]
                else -> acc + vehicleType.capacity
            }
        }.multiply(shippingRateBonus)
    }

    val currentEggLayingRatePerSecond by lazy { minOf(eggLayingRatePerSecond, shippingRatePerMinute) }

    val currentEggLayingRatePerMinute by lazy { currentEggLayingRatePerSecond * 60 }

    val currentEggLayingRatePerHour by lazy { currentEggLayingRatePerSecond * 60 * 60 }

}