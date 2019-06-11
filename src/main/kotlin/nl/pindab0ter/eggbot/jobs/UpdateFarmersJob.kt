package nl.pindab0ter.eggbot.jobs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import nl.pindab0ter.eggbot.asyncMap
import nl.pindab0ter.eggbot.database.Farmer
import nl.pindab0ter.eggbot.network.AuxBrain
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.Job
import org.quartz.JobExecutionContext


class UpdateFarmersJob : Job {

    private val log = KotlinLogging.logger {}

    override fun execute(context: JobExecutionContext?) {
        val farmers = transaction { Farmer.all().toList() }
        if (farmers.isEmpty()) {
            log.info { "No farmers to update…" }
            return
        }

        runBlocking(Dispatchers.IO) {
            farmers
                .asyncMap { farmer -> farmer to AuxBrain.getFarmerBackup(farmer.inGameId).get() }
                .let { farmers ->
                    transaction {
                        farmers.forEach { (farmer, backup) -> farmer.update(backup) }
                    }
                    log.info { "Updated ${farmers.size} farmers." }
                }
        }
    }
}
