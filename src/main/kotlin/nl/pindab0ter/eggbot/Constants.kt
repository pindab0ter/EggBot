package nl.pindab0ter.eggbot

import dev.kord.common.Color
import java.math.BigDecimal

const val NO_ALIAS = "[no alias]"
const val COOP_FILL_PERCENTAGE = 0.8

val DEFAULT_ROLE_COLOR = Color(15, 212, 57) // #0FD439

// Research max levels and multipliers
val BASE_SOUL_EGG_RESEARCH_BONUS = BigDecimal("0.1")
val SOUL_EGG_RESEARCH_BONUS_PER_LEVEL = BigDecimal("0.01")
val MAX_SOUL_EGG_RESEARCH_LEVEL = BigDecimal("140")
val BASE_PROPHECY_EGG_RESEARCH_BONUS = BigDecimal("0.05")
val PROPHECY_EGG_RESEARCH_BONUS_PER_LEVEL = BigDecimal("0.01")
val MAX_PROPHECY_EGG_RESEARCH_LEVEL = BigDecimal("5")
