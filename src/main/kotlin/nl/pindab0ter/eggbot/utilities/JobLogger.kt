package nl.pindab0ter.eggbot.utilities

import nl.pindab0ter.eggbot.helpers.asDaysHoursAndMinutes
import org.apache.logging.log4j.kotlin.Logging
import org.joda.time.DateTime
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobListener

object JobLogger : JobListener, Logging {

    override fun jobToBeExecuted(context: JobExecutionContext?) =
        logger.info { "Executing job ${context?.jobDetail?.key?.name}…" }


    override fun jobExecutionVetoed(context: JobExecutionContext?) = Unit

    override fun getName(): String = "job_logger"

    override fun jobWasExecuted(context: JobExecutionContext?, jobException: JobExecutionException?) =
        logger.info {
            "Finished job ${context?.jobDetail?.key?.name} in ${context?.jobRunTime}ms. " +
                    "Next run: ${context?.nextFireTime?.let { DateTime(it.time).asDaysHoursAndMinutes() }}"
        }
}