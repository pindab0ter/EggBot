package nl.pindab0ter.eggbot.simulation

import com.auxbrain.ei.EggInc
import com.auxbrain.ei.EggInc.HabLevel.NO_HAB
import nl.pindab0ter.eggbot.utilities.*
import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO
import java.math.MathContext.DECIMAL64

abstract class Simulation(val backup: EggInc.Backup) {

    internal abstract val farm: EggInc.Backup.Simulation

    // region Basic info

    val farmerName: String = backup.userName

    // endregion

    // region Research

    private val internalHatcheryFlatIncreases: List<BigDecimal>
        get() = listOf(
            BigDecimal(2 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY1.ordinal].level),
            BigDecimal(5 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY2.ordinal].level),
            BigDecimal(10 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY3.ordinal].level),
            BigDecimal(25 * farm.commonResearchList[CommonResearch.INTERNAL_HATCHERY4.ordinal].level),
            BigDecimal(5 * farm.commonResearchList[CommonResearch.MACHINE_LEARNING_INCUBATORS.ordinal].level),
            BigDecimal(50 * farm.commonResearchList[CommonResearch.NEURAL_LINKING.ordinal].level)
        )

    private val habCapacityMultipliers: List<BigDecimal>
        get() = listOf(
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.HEN_HOUSE_REMODEL.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.MICROLUX_CHICKEN_SUITES.ordinal].level,
            ONE + BigDecimal(".02") * farm.commonResearchList[CommonResearch.GRAV_PLATING.ordinal].level,
            ONE + BigDecimal(".02") * farm.commonResearchList[CommonResearch.WORMHOLE_DAMPENING.ordinal].level
        )

    private val internalHatcheryMultiplier: BigDecimal
        get() = ONE + BigDecimal(".05") * backup.game.epicResearchList[EpicResearch.EPIC_INT_HATCHERIES.ordinal].level

    private val eggLayingMultipliers: List<BigDecimal>
        get() = listOf(
            ONE + BigDecimal(".10") * farm.commonResearchList[CommonResearch.COMFORTABLE_NESTS.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.HEN_HOUSE_AC.ordinal].level,
            ONE + BigDecimal(".15") * farm.commonResearchList[CommonResearch.IMPROVED_GENETICS.ordinal].level,
            ONE + BigDecimal(".10") * farm.commonResearchList[CommonResearch.TIME_COMPRESSION.ordinal].level,
            ONE + BigDecimal(".02") * farm.commonResearchList[CommonResearch.TIMELINE_DIVERSION.ordinal].level,
            ONE + BigDecimal(".10") * farm.commonResearchList[CommonResearch.RELATIVITY_OPTIMIZATION.ordinal].level,
            ONE + BigDecimal(".05") * backup.game.epicResearchList[EpicResearch.EPIC_COMFY_NESTS.ordinal].level
        )

    private val shippingRatePercentageIncreases: List<BigDecimal>
        get() = listOf(
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.IMPROVED_LEAFSPRINGS.ordinal].level,
            ONE + BigDecimal(".10") * farm.commonResearchList[CommonResearch.LIGHTWEIGHT_BOXES.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.DRIVER_TRAINING.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.SUPER_ALLOY_FRAMES.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.QUANTUM_STORAGE.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.HOVER_UPGRADES.ordinal].level, // Assumes at least Hover Semi
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.DARK_CONTAINMENT.ordinal].level,
            ONE + BigDecimal(".05") * farm.commonResearchList[CommonResearch.NEURAL_NET_REFINEMENT.ordinal].level,
            ONE + BigDecimal(".05") * backup.game.epicResearchList[EpicResearch.TRANSPORTATION_LOBBYISTS.ordinal].level
        )

    private val internalHatcheryCalm: BigDecimal
        get() = ONE + BigDecimal(".10") * backup.game.epicResearchList[EpicResearch.INTERNAL_HATCH_CALM.ordinal].level

    // endregion

    // region Habitats (chicken cap)

    private val habsMaxCapacityBonus: BigDecimal by lazy { habCapacityMultipliers.product() }

    private val EggInc.HabLevel.maxCapacity: BigDecimal get() = capacity.multiply(habsMaxCapacityBonus)

    val habsMaxCapacity: BigDecimal by lazy { farm.habsList.sumBy { hab -> hab.maxCapacity } }

    // endregion

    // region Internal hatchery (chicken increase)

    private val internalHatcheryRatePerMinute: BigDecimal by lazy {
        (internalHatcheryFlatIncreases.sum() * internalHatcheryMultiplier)
    }

    private val internalHatcheriesAreActive: Boolean by lazy {
        internalHatcheryRatePerMinute > ZERO
    }

    // TODO: Include Internal Hatchery Sharing for full habs
    val populationIncreasePerMinute: BigDecimal by lazy {
        farm.habsList
            .foldIndexed(ZERO) { index, acc, hab ->
                acc + if (hab.maxCapacity < farm.habPopulation[index] || hab == NO_HAB) ZERO else ONE
            }
            .times(internalHatcheryRatePerMinute)
            .times(internalHatcheryCalm)
    }

    val populationIncreasePerSecond: BigDecimal by lazy {
        populationIncreasePerMinute / BigDecimal(60)
    }

    val populationIncreasePerHour: BigDecimal by lazy {
        populationIncreasePerMinute * BigDecimal(60)
    }

    // endregion

    // region Chickens

    abstract val population: BigDecimal

    // endregion

    // region Egg laying rates

    private val eggLayingBonus: BigDecimal by lazy { eggLayingMultipliers.product() }

    abstract val eggsLaid: BigDecimal

    private val eggLayingBaseRate: BigDecimal by lazy { ONE / BigDecimal(30) }

    val eggsLaidPerChickenPerSecond: BigDecimal by lazy { (eggLayingBaseRate * eggLayingBonus).round(4) }

    val eggsLaidPerChickenPerMinute: BigDecimal by lazy { eggsLaidPerChickenPerSecond * 60 }

    val eggsLaidPerSecond: BigDecimal by lazy { eggsLaidPerChickenPerSecond * population }

    val eggsLaidPerMinute: BigDecimal by lazy { eggsLaidPerSecond * 60 }

    val eggsLaidPerHour: BigDecimal by lazy { eggsLaidPerSecond * 60 * 60 }

    // endregion

    // region Shipping rate (max egg laying rate)

    private val shippingRateBonus: BigDecimal by lazy { shippingRatePercentageIncreases.product() }

    val shippingRatePerMinute: BigDecimal by lazy {
        farm.vehiclesList.foldIndexed(ZERO) { index, acc, vehicleType ->
            when (vehicleType) {
                EggInc.VehicleType.HYPERLOOP_TRAIN -> acc + vehicleType.capacity * farm.hyperloopCarsList[index]
                else -> acc + vehicleType.capacity
            }
        }.multiply(shippingRateBonus)
    }

    val shippingRatePerSecond: BigDecimal by lazy {
        shippingRatePerMinute.divide(BigDecimal(60), DECIMAL64)
    }

    // endregion
}