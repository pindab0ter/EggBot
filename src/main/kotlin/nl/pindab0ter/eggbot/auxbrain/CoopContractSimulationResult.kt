package nl.pindab0ter.eggbot.auxbrain

import com.auxbrain.ei.EggInc

sealed class CoopContractSimulationResult {
    data class NotFound(val contractId: String, val coopId: String): CoopContractSimulationResult()
    data class Empty(val coopStatus: EggInc.CoopStatusResponse, val contractName: String): CoopContractSimulationResult()
    data class Finished(val coopStatus: EggInc.CoopStatusResponse, val contractName: String): CoopContractSimulationResult()
    data class InProgress(val simulation: CoopContractSimulation): CoopContractSimulationResult()
}
