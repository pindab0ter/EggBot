package nl.pindab0ter.eggbot.utilities

import com.jagrosh.jdautilities.command.Command
import com.jagrosh.jdautilities.command.CommandEvent
import kotlinx.coroutines.*
import mu.KotlinLogging
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import nl.pindab0ter.eggbot.jda.commandClient
import kotlin.coroutines.CoroutineContext

fun String.splitMessage(
    prefix: String = "",
    postfix: String = "",
    separator: Char = '\n'
): List<String> = split(separator)
    .also { lines -> require(lines.none { it.length >= 2000 }) { "Any block cannot be larger than 2000 characters." } }
    .fold(listOf("")) { acc, section ->
        if ("${acc.last()}$section$postfix$separator".length < 2000) acc.replaceLast { "$it$section$separator" }
        else acc.replaceLast { "$it$postfix" }.plus("$prefix$section$separator")
    }
    .replaceLast { "$it$postfix" }

fun CommandEvent.replyInDms(messages: List<String>) {
    var successful: Boolean? = null
    messages.forEachIndexed { i, message ->
        replyInDm(message, {
            successful = (successful ?: true) && true
            if (i == messages.size - 1 && isFromType(ChannelType.TEXT)) reactSuccess()
        }, {
            if (successful == null) replyWarning("Help cannot be sent because you are blocking Direct Messages.")
            successful = false
        })
    }
}

val Command.missingArguments get() = "Missing argument(s). Use `${commandClient.textualPrefix}${this.name} ${this.arguments}` without the brackets."
val Command.tooManyArguments get() = "Too many arguments. Use `${commandClient.textualPrefix}${this.name} ${this.arguments}` without the brackets."

class ProgressBarUpdater(
    private val goal: Int,
    private val message: Message,
    private val updateAfterGoalReached: Boolean = true
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Default
    private var running: Boolean = true
    private var value: Int = 0
    private var dirty: Boolean = true
    private var job: Job

    init {
        job = loop()
    }

    private fun loop() = GlobalScope.launch {
        while (running) {
            when {
                value >= goal -> running = false
                dirty -> {
                    message.editMessage(drawProgressBar(value, goal)).queue()
                    dirty = false
                }
                else -> delay(1000)
            }
        }
        message.editMessage(drawProgressBar(goal, goal)).queue()
    }

    fun update(value: Int) {
        if (value >= goal && !updateAfterGoalReached) job.cancel()
        else {
            this.value = value
            dirty = true
        }
    }
}
