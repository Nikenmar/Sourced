package cmrstudio.sourced

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MovementMode {
  /** The "Half-Life" preset: five commands per tick, edge friction on. */
  @SerialName("hl1") HL1,

  /** The "Half-Life 2" preset: additive jump and the forward boost behind accelerated back hopping. */
  @SerialName("hl2") HL2,

  /**
   * The "HL1 Movement" preset: three commands per tick, an uncapped hop and strong air control.
   * Splitting each tick into a fixed number of commands spreads the air steering in small steps
   * while staying independent of frame rate.
   *
   * The default, and the one the mod is tuned around - see `DEFAULT_MOVEMENT_MODE`.
   */
  @SerialName("hl1mov") HL1MOV,

  /** Quake-style movement with soft and hard caps. */
  @SerialName("quake") QUAKE;

  val isHalfLife: Boolean get() = this == HL1 || this == HL2 || this == HL1MOV

  /** Language-file key for the name shown in the config screen and the mode-switch message. */
  val translationKey: String
    get() = when (this) {
      HL1 -> "sourced.mode.hl1"
      HL2 -> "sourced.mode.hl2"
      HL1MOV -> "sourced.mode.hl1mov"
      QUAKE -> "sourced.mode.quake"
    }

  fun next(): MovementMode = entries[(ordinal + 1) % entries.size]
}
