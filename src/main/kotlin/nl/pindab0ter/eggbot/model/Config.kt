package nl.pindab0ter.eggbot.model

import com.auxbrain.ei.Egg
import com.auxbrain.ei.Egg.*
import dev.kord.common.entity.Snowflake
import mu.KotlinLogging
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.*
import kotlin.system.exitProcess

object Config {
    private const val FILE_NAME = "eggbot.properties"
    private val logger = KotlinLogging.logger { }

    val botToken: String
    val prefix: String
    val helpWord: String
    val emojiSuccess: String
    val emojiWarning: String
    val emojiError: String

    // val activity: Activity?
    private val statusType: String
    private val statusText: String?
    val clientVersion: Int
    val devMode: Boolean

    // AuxBrain IDs
    val userId: String
    val deviceId: String

    // Discord IDs
    val botOwner: Snowflake
    val guild: Snowflake
    val adminRole: Snowflake

    val botCommandsChannel: Snowflake
    val earningsBonusLeaderBoardChannel: Snowflake
    val soulEggsLeaderBoardChannel: Snowflake
    val prestigesLeaderBoardChannel: Snowflake
    val dronesLeaderBoardChannel: Snowflake
    val eliteDronesLeaderBoardChannel: Snowflake

    val eggsToEmotes: Map<Egg, Snowflake?>
    val emoteGoldenEgg: Snowflake?
    val emoteSoulEgg: Snowflake?
    val emoteProphecyEgg: Snowflake?

    // Sentry
    val sentryDsn: String?

    init {
        Properties().apply {
            try {
                load(FileInputStream(FILE_NAME))
            } catch (exception: FileNotFoundException) {
                logger.error { "Could not find $FILE_NAME. Please make a copy of $FILE_NAME.example" }
                exitProcess(1)
            }

            botToken = getRequired("bot_token")
            prefix = getOptional("prefix", "!")
            helpWord = getOptional("help_word", "help")
            emojiSuccess = getOptional("emoji.success", "👍")
            emojiWarning = getOptional("emoji.warning", "⚠️")
            emojiError = getOptional("emoji.error", "🚫")
            statusType = getOptional("status_type", "DEFAULT")
            statusText = getOptional("status_text")
            // activity = if (statusText != null) Activity.of(
            //     when (statusType) {
            //         "STREAMING" -> Activity.ActivityType.STREAMING
            //         "LISTENING" -> Activity.ActivityType.LISTENING
            //         "WATCHING" -> Activity.ActivityType.WATCHING
            //         else -> Activity.ActivityType.DEFAULT
            //     }, statusText
            // ) else null
            clientVersion = getOptional("client_version", "0").toInt()
            devMode = getOptional("dev_mode", "false") == "true"

            userId = getRequired("user_id")
            deviceId = getRequired("device_id")

            botOwner = getOptional("bot_owner_id", "0").let(::Snowflake)
            guild = getRequired("guild_id").let(::Snowflake)
            adminRole = getRequired("role.admin_id").let(::Snowflake)

            botCommandsChannel = getRequired("channel.bot_commands").let(::Snowflake)
            earningsBonusLeaderBoardChannel = getRequired("channel.leader_board.earnings_bonus").let(::Snowflake)
            soulEggsLeaderBoardChannel = getRequired("channel.leader_board.soul_eggs").let(::Snowflake)
            prestigesLeaderBoardChannel = getRequired("channel.leader_board.prestiges").let(::Snowflake)
            dronesLeaderBoardChannel = getRequired("channel.leader_board.drone_takedowns").let(::Snowflake)
            eliteDronesLeaderBoardChannel = getRequired("channel.leader_board.elite_drone_takedowns").let(::Snowflake)

            // @formatter:off
            eggsToEmotes = mapOf (
                UNKNOWN_EGG    to getOptional("emote.default"),
                EDIBLE         to getOptional("emote.edible"),
                SUPERFOOD      to getOptional("emote.superfood"),
                MEDICAL        to getOptional("emote.medical"),
                ROCKET_FUEL    to getOptional("emote.rocket_fuel"),
                SUPER_MATERIAL to getOptional("emote.super_material"),
                FUSION         to getOptional("emote.fusion"),
                QUANTUM        to getOptional("emote.quantum"),
                IMMORTALITY    to getOptional("emote.immortality"),
                TACHYON        to getOptional("emote.tachyon"),
                GRAVITON       to getOptional("emote.graviton"),
                DILITHIUM      to getOptional("emote.dilithium"),
                PRODIGY        to getOptional("emote.prodigy"),
                TERRAFORM      to getOptional("emote.terraform"),
                ANTIMATTER     to getOptional("emote.antimatter"),
                DARK_MATTER    to getOptional("emote.dark_matter"),
                AI             to getOptional("emote.ai"),
                NEBULA         to getOptional("emote.nebula"),
                UNIVERSE       to getOptional("emote.universe"),
                ENLIGHTENMENT  to getOptional("emote.enlightenment"),
                CHOCOLATE      to getOptional("emote.chocolate"),
                EASTER         to getOptional("emote.easter"),
                WATER_BALLOON  to getOptional("emote.water_balloon"),
                FIREWORK       to getOptional("emote.firework"),
                PUMPKIN        to getOptional("emote.pumpkin")
            ).mapValues {  it.value?.let(::Snowflake) }
            // @formatter:on

            emoteGoldenEgg = getOptional("emote.gold")?.let(::Snowflake)
            emoteSoulEgg = getOptional("emote.soul")?.let(::Snowflake)
            emoteProphecyEgg = getOptional("emote.prophecy")?.let(::Snowflake)

            sentryDsn = getOptional("sentry.dsn")

            logger.info("Config loaded")
        }
    }

    private fun Properties.getRequired(key: String): String = getProperty(key).let {
        return when {
            !it.isNullOrBlank() -> it
            else -> throw PropertyNotFoundException("Could not load \"$key\" from \"$FILE_NAME\".")
        }
    }

    private fun Properties.getOptional(key: String): String? = getProperty(key).let {
        return when {
            !it.isNullOrBlank() -> it
            else -> null
        }
    }

    private fun Properties.getOptional(key: String, default: String): String = getProperty(key).let {
        return when {
            !it.isNullOrBlank() -> it
            else -> default
        }
    }

    class PropertyNotFoundException(override val message: String?) : Exception(message)
}
