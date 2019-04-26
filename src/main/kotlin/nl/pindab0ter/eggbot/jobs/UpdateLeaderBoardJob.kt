package nl.pindab0ter.eggbot.jobs

import mu.KotlinLogging
import nl.pindab0ter.eggbot.Config
import nl.pindab0ter.eggbot.EggBot
import nl.pindab0ter.eggbot.database.Farmer
import nl.pindab0ter.eggbot.leaderBoard
import org.jetbrains.exposed.sql.transactions.transaction
import org.quartz.Job
import org.quartz.JobExecutionContext

class UpdateLeaderBoardJob : Job {

    private val log = KotlinLogging.logger {}

    override fun execute(context: JobExecutionContext?) {
        val farmers = transaction {
            Farmer.all().toList().sortedByDescending { it.earningsBonus }
        }

        if (farmers.isEmpty()) {
            log.info { "There are no registered farmers" }
            return
        }

        EggBot.jdaClient.getTextChannelById(Config.leaderBoardChannel).apply {
            history.retrievePast(5).complete().let { messages ->
                log.info { "Purging ${messages.count()} leader board messages…" }
                purgeMessages(messages)
            }

            log.info { "Sending updated leader board…" }
            leaderBoard(farmers).forEach { message ->
                sendMessage(message).queue()
            }
        }
    }
}