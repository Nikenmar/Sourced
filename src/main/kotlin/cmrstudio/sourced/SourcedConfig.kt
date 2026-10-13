package cmrstudio.sourced

import cmrstudio.sourced.movecore.Preset
import cmrstudio.sourced.movecore.Rules
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

// Unknown keys are skipped, so a config written by an older version loads instead of failing:
// settings that were renamed or removed simply fall back to their defaults.
private val json = Json {
  prettyPrint = true
  ignoreUnknownKeys = true
}

@Serializable
class SourcedConfig(@Transient var path: Path? = null) {
  // @formatter:off
  /* General */
  // Movement
  var movementMode               : MovementMode = DEFAULT_MOVEMENT_MODE
  var quakeMovementEnabled       : Boolean = DEFAULT_QUAKE_MOVEMENT_ENABLED
  var      trimpingEnabled       : Boolean = DEFAULT_TRIMPING_ENABLED
  var      sharkingEnabled       : Boolean = DEFAULT_SHARKING_ENABLED

  // Miscellaneous
  var uncappedBunnyhop           : Boolean = DEFAULT_UNCAPPED_BUNNYHOP
  var noJumpCooldown             : Boolean = DEFAULT_NO_JUMP_COOLDOWN
  var jumpParticles              : Int     = DEFAULT_JUMP_PARTICLES

  // Speed indicator
  var speedDeltaIndicatorEnabled : Boolean = DEFAULT_SPEED_DELTA_INDICATOR_ENABLED
  var speedDiffIndicatorEnabled  : Boolean = DEFAULT_SPEED_DIFF_INDICATOR_ENABLED
  var speedDeltaThreshold        : Double  = DEFAULT_SPEED_DELTA_THRESHOLD
  var speedGainColor             : Int     = SPEED_GAIN_COLOR      . rgb
  var speedLossColor             : Int     = SPEED_LOSS_COLOR      . rgb
  var speedUnchangedColor        : Int     = SPEED_UNCHANGED_COLOR . rgb

  /* Movement constants */
  // Bunnyhop
  var softCapThreshold       : Double = DEFAULT_SOFT_CAP_THRESHOLD
  var hardCapThreshold       : Double = DEFAULT_HARD_CAP_THRESHOLD
  var acceleration           : Double = DEFAULT_ACCELERATION
  var airAcceleration        : Double = DEFAULT_AIR_ACCELERATION
  var maxAAccPerTick         : Double = DEFAULT_MAX_AACEL_PER_TICK
  var softCapDegen           : Double = DEFAULT_SOFT_CAP_DEGEN

  // Trimping
  var trimpMultiplier        : Double = DEFAULT_TRIMP_MULTIPLIER

  // Sharking
  var sharkingFriction       : Double = DEFAULT_SHARKING_FRICTION
  var sharkingSurfaceTension : Double = DEFAULT_SHARKING_SURFACE_TENSION

  /* Half-Life presets - settings shared by all three */
  var hlAutoHop              : Boolean = RULES_DEFAULTS.autoHop
  var hlForwardBoostEnabled  : Boolean = RULES_DEFAULTS.forwardBoostEnabled
  var hlBoostCeiling         : Double  = RULES_DEFAULTS.boostCeiling
  var hlSoftCapThreshold     : Double  = RULES_DEFAULTS.softCapThreshold
  var hlSoftCapKeep          : Double  = RULES_DEFAULTS.softCapKeep
  var hlBlockRamps           : Boolean = DEFAULT_HL_BLOCK_RAMPS
  var hlBlockRampLift        : Double  = DEFAULT_HL_BLOCK_RAMP_LIFT
  var hlUseVanillaSpeed      : Boolean = DEFAULT_HL_USE_VANILLA_SPEED
  var hlVanillaSpeedMult     : Double  = DEFAULT_HL_VANILLA_SPEED_MULT
  var hlGainFalloff          : Boolean = DEFAULT_HL_GAIN_FALLOFF
  var hlGainFalloffCurve     : SpeedGainCurve = DEFAULT_HL_GAIN_FALLOFF_CURVE
  var hlGainFalloffStart     : Double  = DEFAULT_HL_GAIN_FALLOFF_START
  var hlGainFalloffScale     : Double  = DEFAULT_HL_GAIN_FALLOFF_SCALE
  var hlStepHeight           : Double  = DEFAULT_HL_STEP_HEIGHT

  /* Half-Life preset - units are 1 inch, seconds */
  var hl1Gravity             : Double  = HL1_DEFAULTS.gravity
  var hl1FullSpeed           : Double  = HL1_DEFAULTS.fullSpeed
  var hl1InputScale          : Double  = HL1_DEFAULTS.inputScale
  var hl1GroundAcceleration  : Double  = HL1_DEFAULTS.groundAcceleration
  var hl1AirAcceleration     : Double  = HL1_DEFAULTS.airAcceleration
  var hl1AirSpeedCap         : Double  = HL1_DEFAULTS.airSpeedCap
  var hl1Friction            : Double  = HL1_DEFAULTS.friction
  var hl1EdgeFriction        : Double  = HL1_DEFAULTS.edgeFriction
  var hl1StopSpeed           : Double  = HL1_DEFAULTS.stopSpeed
  var hl1IdleFriction        : Double  = HL1_DEFAULTS.idleFriction
  var hl1MaxVelocity         : Double  = HL1_DEFAULTS.maxVelocity
  var hl1JumpHeight          : Double  = HL1_DEFAULTS.jumpHeight
  var hl1CommandsPerTick     : Int     = HL1_DEFAULTS.commandsPerTick
  var hl1HopClip             : Boolean = HL1_DEFAULTS.hopClip
  var hl1ForwardBoost        : Boolean = HL1_DEFAULTS.forwardBoost
  var hl1AdditiveJump        : Boolean = HL1_DEFAULTS.additiveJump
  var hl1SoftSpeedCap        : Boolean = HL1_DEFAULTS.softSpeedCap

  /* HL1 Movement preset */
  var hlmovGravity           : Double  = HLMOV_DEFAULTS.gravity
  var hlmovFullSpeed         : Double  = HLMOV_DEFAULTS.fullSpeed
  var hlmovInputScale        : Double  = HLMOV_DEFAULTS.inputScale
  var hlmovGroundAcceleration: Double  = HLMOV_DEFAULTS.groundAcceleration
  var hlmovAirAcceleration   : Double  = HLMOV_DEFAULTS.airAcceleration
  var hlmovAirSpeedCap       : Double  = HLMOV_DEFAULTS.airSpeedCap
  var hlmovFriction          : Double  = HLMOV_DEFAULTS.friction
  var hlmovEdgeFriction      : Double  = HLMOV_DEFAULTS.edgeFriction
  var hlmovStopSpeed         : Double  = HLMOV_DEFAULTS.stopSpeed
  var hlmovIdleFriction      : Double  = HLMOV_DEFAULTS.idleFriction
  var hlmovMaxVelocity       : Double  = HLMOV_DEFAULTS.maxVelocity
  var hlmovJumpHeight        : Double  = HLMOV_DEFAULTS.jumpHeight
  var hlmovCommandsPerTick   : Int     = HLMOV_DEFAULTS.commandsPerTick
  var hlmovHopClip           : Boolean = HLMOV_DEFAULTS.hopClip
  var hlmovForwardBoost      : Boolean = HLMOV_DEFAULTS.forwardBoost
  var hlmovAdditiveJump      : Boolean = HLMOV_DEFAULTS.additiveJump
  var hlmovSoftSpeedCap      : Boolean = HLMOV_DEFAULTS.softSpeedCap

  /* Half-Life 2 preset */
  var hl2Gravity             : Double  = HL2_DEFAULTS.gravity
  var hl2FullSpeed           : Double  = HL2_DEFAULTS.fullSpeed
  var hl2InputScale          : Double  = HL2_DEFAULTS.inputScale
  var hl2GroundAcceleration  : Double  = HL2_DEFAULTS.groundAcceleration
  var hl2AirAcceleration     : Double  = HL2_DEFAULTS.airAcceleration
  var hl2AirSpeedCap         : Double  = HL2_DEFAULTS.airSpeedCap
  var hl2Friction            : Double  = HL2_DEFAULTS.friction
  var hl2EdgeFriction        : Double  = HL2_DEFAULTS.edgeFriction
  var hl2StopSpeed           : Double  = HL2_DEFAULTS.stopSpeed
  var hl2IdleFriction        : Double  = HL2_DEFAULTS.idleFriction
  var hl2MaxVelocity         : Double  = HL2_DEFAULTS.maxVelocity
  var hl2JumpHeight          : Double  = HL2_DEFAULTS.jumpHeight
  var hl2CommandsPerTick     : Int     = HL2_DEFAULTS.commandsPerTick
  var hl2HopClip             : Boolean = HL2_DEFAULTS.hopClip
  var hl2ForwardBoost        : Boolean = HL2_DEFAULTS.forwardBoost
  var hl2AdditiveJump        : Boolean = HL2_DEFAULTS.additiveJump
  var hl2SoftSpeedCap        : Boolean = HL2_DEFAULTS.softSpeedCap
  // @formatter:on

  /** The shared switches as the movement core takes them. Built fresh, so edits apply at once. */
  fun rules(): Rules = Rules(hlAutoHop, hlForwardBoostEnabled, hlBoostCeiling, hlSoftCapThreshold, hlSoftCapKeep)

  /** One Half-Life preset as the movement core takes it. Built fresh, so edits apply at once. */
  fun preset(mode: MovementMode): Preset = when (mode) {
    MovementMode.HL2 -> Preset(
      hl2Gravity, hl2FullSpeed, hl2InputScale, hl2GroundAcceleration, hl2AirAcceleration, hl2AirSpeedCap,
      hl2Friction, hl2EdgeFriction, hl2StopSpeed, hl2IdleFriction, hl2MaxVelocity, hl2JumpHeight,
      hl2CommandsPerTick, hl2HopClip, hl2ForwardBoost, hl2AdditiveJump, hl2SoftSpeedCap,
    )
    MovementMode.HL1MOV -> Preset(
      hlmovGravity, hlmovFullSpeed, hlmovInputScale, hlmovGroundAcceleration, hlmovAirAcceleration, hlmovAirSpeedCap,
      hlmovFriction, hlmovEdgeFriction, hlmovStopSpeed, hlmovIdleFriction, hlmovMaxVelocity, hlmovJumpHeight,
      hlmovCommandsPerTick, hlmovHopClip, hlmovForwardBoost, hlmovAdditiveJump, hlmovSoftSpeedCap,
    )
    else -> Preset(
      hl1Gravity, hl1FullSpeed, hl1InputScale, hl1GroundAcceleration, hl1AirAcceleration, hl1AirSpeedCap,
      hl1Friction, hl1EdgeFriction, hl1StopSpeed, hl1IdleFriction, hl1MaxVelocity, hl1JumpHeight,
      hl1CommandsPerTick, hl1HopClip, hl1ForwardBoost, hl1AdditiveJump, hl1SoftSpeedCap,
    )
  }

  fun save() {
    path!!.writeText(json.encodeToString(this))
  }

  companion object {
    // @formatter:off
    /* General */
    // Movement
    // HL1 Movement is the preset this mod is tuned around and the one to bunnyhop in: three
    // commands per tick, and the widest per-tick air-steering budget of the four. The remaining
    // presets are kept for comparison, not as the intended experience.
            val DEFAULT_MOVEMENT_MODE               = MovementMode.HL1MOV
    const val DEFAULT_QUAKE_MOVEMENT_ENABLED        = true
    const val DEFAULT_TRIMPING_ENABLED              = true
    const val DEFAULT_SHARKING_ENABLED              = true

    // Miscellaneous
    const val DEFAULT_UNCAPPED_BUNNYHOP             = true
    const val DEFAULT_NO_JUMP_COOLDOWN              = true
    const val DEFAULT_JUMP_PARTICLES                = 4

    // Speed indicator
    const val DEFAULT_SPEED_DELTA_INDICATOR_ENABLED = false
    const val DEFAULT_SPEED_DIFF_INDICATOR_ENABLED  = true
    const val DEFAULT_SPEED_DELTA_THRESHOLD         = 6.0
  /* s */ val SPEED_GAIN_COLOR                      = Color(0xFF_00FF00.toInt())
  /* a */ val SPEED_LOSS_COLOR                      = Color(0xFF_FF0000.toInt())
  /* d */ val SPEED_UNCHANGED_COLOR                 = Color(0xFF_FFFFFF.toInt())
  // Apparently no const colors :(

    /* Movement constants */
    // Bunnyhop
    const val DEFAULT_SOFT_CAP_THRESHOLD       =   1.200
    const val DEFAULT_HARD_CAP_THRESHOLD       =   1.700
    const val DEFAULT_ACCELERATION             =  10.000
    const val DEFAULT_AIR_ACCELERATION         =  10.000
    const val DEFAULT_MAX_AACEL_PER_TICK       =   0.050
    const val DEFAULT_SOFT_CAP_DEGEN           =   0.800

    // Trimping
    const val DEFAULT_TRIMP_MULTIPLIER         =   1.800

    // Sharking
    const val DEFAULT_SHARKING_FRICTION        =   0.995
    const val DEFAULT_SHARKING_SURFACE_TENSION =   0.800

    /* Half-Life presets */
    // The movement core owns the preset values and the shared rules.
    val HL1_DEFAULTS: Preset   = Preset.halfLife()
    val HL2_DEFAULTS: Preset   = Preset.halfLife2()
    val HLMOV_DEFAULTS: Preset = Preset.hl1Movement()
    val RULES_DEFAULTS: Rules  = Rules.defaults()

    // Off. Minecraft terrain is a grid of cubes, and at high speed every block corner is a wall
    // that ends the run. This adds no speed; it stops block corners from taking away speed you
    // already have. Aimed at the Half-Life 2 preset, available to all three.
    const val DEFAULT_HL_BLOCK_RAMPS        = false
    // Fraction of a square-on run converted to climb when the ramp fires. 0.35 clears a block
    // comfortably from about 400 u/s without turning every wall into a launch pad.
    const val DEFAULT_HL_BLOCK_RAMP_LIFT    =   0.350

    // Speed-gain falloff: here for feel, and its default was settled in play rather than by a
    // measurement.
    //
    // Every other limit acts on the velocity itself. This one scales the increase instead. It only
    // ever touches a positive difference, so speed you reached is yours to keep; the falloff just
    // makes the next gain dearer. Direction is never scaled, so braking, steering and recovery
    // from a hard turn keep their full strength.
    const val DEFAULT_HL_GAIN_FALLOFF       = true
    // Linear and quadratic both reach zero one scale up and stop dead, which is the wall this
    // exists to avoid. Exponential keeps 0.37, 0.14 and 0.05 at one, two and three scales and
    // never reaches zero. Chosen over the gentler inverse in play.
    val DEFAULT_HL_GAIN_FALLOFF_CURVE       = SpeedGainCurve.EXPONENTIAL
    // Onset, x the walk target, so a Speed potion moves the mechanic with it. Below this nothing is
    // touched. Against a 220 u/s sprint target, 2.0 is 440 u/s = 11.2 m/s, well past anything
    // ordinary bunnyhopping produces.
    const val DEFAULT_HL_GAIN_FALLOFF_START =   2.000
    // How much excess, x the walk target, counts as one full scale. Sets how fast the curve
    // tightens. At 4.0 against 220 u/s: one scale up is 1320 u/s = 33.5 m/s and keeps 0.37 of each
    // gain.
    const val DEFAULT_HL_GAIN_FALLOFF_SCALE =   4.000

    // Take the walk target from Minecraft's own MOVEMENT_SPEED attribute instead of the preset's
    // full speed. Sprinting, Speed potions, Soul Speed and every other attribute modifier keep
    // working, because vanilla folds them into that attribute before travel runs. 2.15 lands
    // vanilla walk on 4.30 m/s and sprint on 5.59 against vanilla's 4.32 and 5.61.
    const val DEFAULT_HL_USE_VANILLA_SPEED  = true
    const val DEFAULT_HL_VANILLA_SPEED_MULT =   2.150

    // Blocks. Vanilla's 0.6 makes a single block a jump, which breaks a run on uneven ground.
    const val DEFAULT_HL_STEP_HEIGHT        =   1.000
    // @formatter:on

    fun load(path: Path): SourcedConfig {
      if (!path.exists()) return SourcedConfig(path)

      val text = Files.newInputStream(path).bufferedReader().use { it.readText() }
      return try {
        json.decodeFromString<SourcedConfig>(text).also { it.path = path }
      } catch (e: Exception) {
        // A broken file must not stop the game from starting. Run on defaults; the file is only
        // overwritten if the player saves from the config screen.
        SourcedMod.logger.warn("Could not read {}, using defaults: {}", path, e.message)
        SourcedConfig(path)
      }
    }
  }
}
