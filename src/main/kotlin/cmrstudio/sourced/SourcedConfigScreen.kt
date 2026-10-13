package cmrstudio.sourced

import dev.isxander.yacl3.api.*
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder
import dev.isxander.yacl3.api.controller.ColorControllerBuilder
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder
import cmrstudio.sourced.movecore.Preset
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.reflect.KMutableProperty0

// Every string on this screen comes from the language files. Keys are grouped as
//   sourced.cat.<id>    / .desc   category and its tooltip
//   sourced.group.<id>            option group heading
//   sourced.opt.<id>    / .desc   option name and its tooltip
// Presets share a key wherever the setting means the same thing in all of them, so gravity is
// translated once rather than three times.

private fun optName(key: String) = Component.translatable("sourced.opt.$key")
private fun optDesc(key: String) = Component.translatable("sourced.opt.$key.desc")

/** The preset groups are long and uniform; these keep them readable. */
private fun doubleOption(key: String, default: Double, get: () -> Double, set: (Double) -> Unit): Option<Double> =
  Option.createBuilder<Double>()
    .name(optName(key))
    .description(OptionDescription.of(optDesc(key)))
    .binding(default, get, set)
    .controller(DoubleFieldControllerBuilder::create)
    .build()

private fun boolOption(key: String, default: Boolean, get: () -> Boolean, set: (Boolean) -> Unit): Option<Boolean> =
  Option.createBuilder<Boolean>()
    .name(optName(key))
    .description(OptionDescription.of(optDesc(key)))
    .binding(default, get, set)
    .controller(BooleanControllerBuilder::create)
    .build()

private fun intOption(key: String, default: Int, get: () -> Int, set: (Int) -> Unit): Option<Int> =
  Option.createBuilder<Int>()
    .name(optName(key))
    .description(OptionDescription.of(optDesc(key)))
    .binding(default, get, set)
    .controller(IntegerFieldControllerBuilder::create)
    .build()

private fun colorOption(key: String, default: Color, get: () -> Int, set: (Int) -> Unit): Option<Color> =
  Option.createBuilder<Color>()
    .name(optName(key))
    .description(OptionDescription.of(optDesc(key)))
    .binding(default, { Color(get()) }, { set(it.rgb) })
    .controller(ColorControllerBuilder::create)
    .build()

private fun doubleOption(key: String, default: Double, prop: KMutableProperty0<Double>) =
  doubleOption(key, default, prop::get, prop::set)

private fun boolOption(key: String, default: Boolean, prop: KMutableProperty0<Boolean>) =
  boolOption(key, default, prop::get, prop::set)

private fun intOption(key: String, default: Int, prop: KMutableProperty0<Int>) =
  intOption(key, default, prop::get, prop::set)

/** The three preset groups are identical apart from which fields they edit. */
private class PresetFields(
  val gravity: KMutableProperty0<Double>,
  val fullSpeed: KMutableProperty0<Double>,
  val inputScale: KMutableProperty0<Double>,
  val groundAcceleration: KMutableProperty0<Double>,
  val airAcceleration: KMutableProperty0<Double>,
  val airSpeedCap: KMutableProperty0<Double>,
  val friction: KMutableProperty0<Double>,
  val edgeFriction: KMutableProperty0<Double>,
  val stopSpeed: KMutableProperty0<Double>,
  val idleFriction: KMutableProperty0<Double>,
  val maxVelocity: KMutableProperty0<Double>,
  val jumpHeight: KMutableProperty0<Double>,
  val commandsPerTick: KMutableProperty0<Int>,
  val hopClip: KMutableProperty0<Boolean>,
  val forwardBoost: KMutableProperty0<Boolean>,
  val additiveJump: KMutableProperty0<Boolean>,
  val softSpeedCap: KMutableProperty0<Boolean>,
)

private fun presetGroup(nameKey: String, d: Preset, f: PresetFields): OptionGroup = OptionGroup.createBuilder()
  .name(Component.translatable(nameKey))
  .collapsed(true)
  .option(doubleOption("gravity", d.gravity, f.gravity))
  .option(doubleOption("full_speed", d.fullSpeed, f.fullSpeed))
  .option(doubleOption("input_scale", d.inputScale, f.inputScale))
  .option(doubleOption("ground_acceleration", d.groundAcceleration, f.groundAcceleration))
  .option(doubleOption("air_acceleration", d.airAcceleration, f.airAcceleration))
  .option(doubleOption("air_speed_cap", d.airSpeedCap, f.airSpeedCap))
  .option(doubleOption("friction", d.friction, f.friction))
  .option(doubleOption("edge_friction", d.edgeFriction, f.edgeFriction))
  .option(doubleOption("stop_speed", d.stopSpeed, f.stopSpeed))
  .option(doubleOption("idle_friction", d.idleFriction, f.idleFriction))
  .option(doubleOption("max_velocity", d.maxVelocity, f.maxVelocity))
  .option(doubleOption("jump_height", d.jumpHeight, f.jumpHeight))
  .option(intOption("commands_per_tick", d.commandsPerTick, f.commandsPerTick))
  .option(boolOption("hop_clip", d.hopClip, f.hopClip))
  .option(boolOption("forward_boost", d.forwardBoost, f.forwardBoost))
  .option(boolOption("additive_jump", d.additiveJump, f.additiveJump))
  .option(boolOption("soft_speed_cap", d.softSpeedCap, f.softSpeedCap))
  .build()

private fun halfLifeCategory(): ConfigCategory {
  val c = SourcedMod.config
  val r = SourcedConfig.RULES_DEFAULTS
  return ConfigCategory.createBuilder()
  .name(Component.translatable("sourced.cat.halflife"))
  .tooltip(Component.translatable("sourced.cat.halflife.desc"))
  .group(
    OptionGroup.createBuilder()
      .name(Component.translatable("sourced.group.behaviour"))
      .collapsed(false)
      .option(boolOption("auto_hop", r.autoHop, c::hlAutoHop))
      .option(boolOption("forward_boost_enabled", r.forwardBoostEnabled, c::hlForwardBoostEnabled))
      .option(doubleOption("boost_ceiling", r.boostCeiling, c::hlBoostCeiling))
      .option(doubleOption("soft_cap_threshold", r.softCapThreshold, c::hlSoftCapThreshold))
      .option(doubleOption("soft_cap_keep", r.softCapKeep, c::hlSoftCapKeep))
      .option(boolOption("block_ramps",
        SourcedConfig.DEFAULT_HL_BLOCK_RAMPS, { SourcedMod.config.hlBlockRamps }, { SourcedMod.config.hlBlockRamps = it }))
      .option(doubleOption("ramp_lift",
        SourcedConfig.DEFAULT_HL_BLOCK_RAMP_LIFT, { SourcedMod.config.hlBlockRampLift }, { SourcedMod.config.hlBlockRampLift = it }))
      .option(boolOption("use_vanilla_speed",
        SourcedConfig.DEFAULT_HL_USE_VANILLA_SPEED, { SourcedMod.config.hlUseVanillaSpeed }, { SourcedMod.config.hlUseVanillaSpeed = it }))
      .option(doubleOption("vanilla_speed_mult",
        SourcedConfig.DEFAULT_HL_VANILLA_SPEED_MULT, { SourcedMod.config.hlVanillaSpeedMult }, { SourcedMod.config.hlVanillaSpeedMult = it }))
      .option(boolOption("gain_falloff",
        SourcedConfig.DEFAULT_HL_GAIN_FALLOFF, { SourcedMod.config.hlGainFalloff }, { SourcedMod.config.hlGainFalloff = it }))
      .option(
        Option.createBuilder<SpeedGainCurve>()
          .name(optName("gain_falloff_curve"))
          .description(OptionDescription.of(optDesc("gain_falloff_curve")))
          .binding(SourcedConfig.DEFAULT_HL_GAIN_FALLOFF_CURVE,
            { SourcedMod.config.hlGainFalloffCurve },
            { SourcedMod.config.hlGainFalloffCurve = it })
          .controller { opt ->
            EnumControllerBuilder.create(opt)
              .enumClass(SpeedGainCurve::class.java)
              .formatValue { Component.translatable(it.translationKey) }
          }
          .build()
      )
      .option(doubleOption("gain_falloff_start",
        SourcedConfig.DEFAULT_HL_GAIN_FALLOFF_START, { SourcedMod.config.hlGainFalloffStart }, { SourcedMod.config.hlGainFalloffStart = it }))
      .option(doubleOption("gain_falloff_scale",
        SourcedConfig.DEFAULT_HL_GAIN_FALLOFF_SCALE, { SourcedMod.config.hlGainFalloffScale }, { SourcedMod.config.hlGainFalloffScale = it }))
      .option(doubleOption("step_height",
        SourcedConfig.DEFAULT_HL_STEP_HEIGHT, { SourcedMod.config.hlStepHeight }, { SourcedMod.config.hlStepHeight = it }))
      .build()
  )
  .group(presetGroup("sourced.group.hl1", SourcedConfig.HL1_DEFAULTS, PresetFields(
    c::hl1Gravity, c::hl1FullSpeed, c::hl1InputScale, c::hl1GroundAcceleration, c::hl1AirAcceleration,
    c::hl1AirSpeedCap, c::hl1Friction, c::hl1EdgeFriction, c::hl1StopSpeed, c::hl1IdleFriction, c::hl1MaxVelocity,
    c::hl1JumpHeight, c::hl1CommandsPerTick, c::hl1HopClip, c::hl1ForwardBoost, c::hl1AdditiveJump, c::hl1SoftSpeedCap,
  )))
  .group(presetGroup("sourced.group.hlmov", SourcedConfig.HLMOV_DEFAULTS, PresetFields(
    c::hlmovGravity, c::hlmovFullSpeed, c::hlmovInputScale, c::hlmovGroundAcceleration, c::hlmovAirAcceleration,
    c::hlmovAirSpeedCap, c::hlmovFriction, c::hlmovEdgeFriction, c::hlmovStopSpeed, c::hlmovIdleFriction,
    c::hlmovMaxVelocity, c::hlmovJumpHeight, c::hlmovCommandsPerTick, c::hlmovHopClip, c::hlmovForwardBoost,
    c::hlmovAdditiveJump, c::hlmovSoftSpeedCap,
  )))
  .group(presetGroup("sourced.group.hl2", SourcedConfig.HL2_DEFAULTS, PresetFields(
    c::hl2Gravity, c::hl2FullSpeed, c::hl2InputScale, c::hl2GroundAcceleration, c::hl2AirAcceleration,
    c::hl2AirSpeedCap, c::hl2Friction, c::hl2EdgeFriction, c::hl2StopSpeed, c::hl2IdleFriction, c::hl2MaxVelocity,
    c::hl2JumpHeight, c::hl2CommandsPerTick, c::hl2HopClip, c::hl2ForwardBoost, c::hl2AdditiveJump, c::hl2SoftSpeedCap,
  )))
  .build()
}

fun generateConfigScreen(parent: Screen?): Screen = YetAnotherConfigLib.createBuilder()
  .save(SourcedMod.config::save)
  .title(Component.literal(SourcedMod.NAME))
  .category(
    ConfigCategory.createBuilder()
      .name(Component.translatable("sourced.cat.general"))
      .tooltip(Component.translatable("sourced.cat.general.desc"))
      .group(
        OptionGroup.createBuilder()
          .name(Component.translatable("sourced.group.movement"))
          .collapsed(false)
          .option(boolOption("custom_movement",
            SourcedConfig.DEFAULT_QUAKE_MOVEMENT_ENABLED,
            { SourcedMod.config.quakeMovementEnabled },
            { SourcedMod.config.quakeMovementEnabled = it }))
          .option(
            Option.createBuilder<MovementMode>()
              .name(optName("movement_mode"))
              .description(OptionDescription.of(optDesc("movement_mode")))
              .binding(SourcedConfig.DEFAULT_MOVEMENT_MODE,
                { SourcedMod.config.movementMode },
                { SourcedMod.config.movementMode = it; HalfLifeMovement.reset() })
              .controller { opt ->
                EnumControllerBuilder.create(opt)
                  .enumClass(MovementMode::class.java)
                  .formatValue { Component.translatable(it.translationKey) }
              }
              .build()
          )
          .option(boolOption("trimping",
            SourcedConfig.DEFAULT_TRIMPING_ENABLED,
            { SourcedMod.config.trimpingEnabled },
            { SourcedMod.config.trimpingEnabled = it }))
          .option(boolOption("sharking",
            SourcedConfig.DEFAULT_SHARKING_ENABLED,
            { SourcedMod.config.sharkingEnabled },
            { SourcedMod.config.sharkingEnabled = it }))
          .build()
      )
      .group(
        OptionGroup.createBuilder()
          .name(Component.translatable("sourced.group.misc"))
          .collapsed(false)
          .option(boolOption("uncapped_bunnyhop",
            SourcedConfig.DEFAULT_UNCAPPED_BUNNYHOP,
            { SourcedMod.config.uncappedBunnyhop },
            { SourcedMod.config.uncappedBunnyhop = it }))
          .option(boolOption("no_jump_cooldown",
            SourcedConfig.DEFAULT_NO_JUMP_COOLDOWN,
            { SourcedMod.config.noJumpCooldown },
            { SourcedMod.config.noJumpCooldown = it }))
          .option(intOption("jump_particles",
            SourcedConfig.DEFAULT_JUMP_PARTICLES,
            { SourcedMod.config.jumpParticles },
            { SourcedMod.config.jumpParticles = it }))
          .build()
      )
      .group(
        OptionGroup.createBuilder()
          .name(Component.translatable("sourced.group.indicator"))
          .collapsed(false)
          .option(boolOption("show_speed",
            SourcedConfig.DEFAULT_SPEED_DELTA_INDICATOR_ENABLED,
            { SourcedMod.config.speedDeltaIndicatorEnabled },
            { SourcedMod.config.speedDeltaIndicatorEnabled = it }))
          .option(boolOption("show_change",
            SourcedConfig.DEFAULT_SPEED_DIFF_INDICATOR_ENABLED,
            { SourcedMod.config.speedDiffIndicatorEnabled },
            { SourcedMod.config.speedDiffIndicatorEnabled = it }))
          .option(doubleOption("speed_threshold",
            SourcedConfig.DEFAULT_SPEED_DELTA_THRESHOLD,
            { SourcedMod.config.speedDeltaThreshold },
            { SourcedMod.config.speedDeltaThreshold = it }))
          .option(colorOption("color_gain", SourcedConfig.SPEED_GAIN_COLOR,
            { SourcedMod.config.speedGainColor }, { SourcedMod.config.speedGainColor = it }))
          .option(colorOption("color_loss", SourcedConfig.SPEED_LOSS_COLOR,
            { SourcedMod.config.speedLossColor }, { SourcedMod.config.speedLossColor = it }))
          .option(colorOption("color_steady", SourcedConfig.SPEED_UNCHANGED_COLOR,
            { SourcedMod.config.speedUnchangedColor }, { SourcedMod.config.speedUnchangedColor = it }))
          .build()
      )
      .build()
  )
  .category(
    ConfigCategory.createBuilder()
      .name(Component.translatable("sourced.cat.constants"))
      .tooltip(Component.translatable("sourced.cat.constants.desc"))
      .group(
        OptionGroup.createBuilder()
          .name(Component.translatable("sourced.group.quake"))
          .collapsed(false)
          .option(doubleOption("quake_soft_cap_threshold",
            SourcedConfig.DEFAULT_SOFT_CAP_THRESHOLD,
            { SourcedMod.config.softCapThreshold },
            { SourcedMod.config.softCapThreshold = it }))
          .option(doubleOption("quake_hard_cap_threshold",
            SourcedConfig.DEFAULT_HARD_CAP_THRESHOLD,
            { SourcedMod.config.hardCapThreshold },
            { SourcedMod.config.hardCapThreshold = it }))
          .option(doubleOption("quake_acceleration",
            SourcedConfig.DEFAULT_ACCELERATION,
            { SourcedMod.config.acceleration },
            { SourcedMod.config.acceleration = it }))
          .option(doubleOption("quake_air_acceleration",
            SourcedConfig.DEFAULT_AIR_ACCELERATION,
            { SourcedMod.config.airAcceleration },
            { SourcedMod.config.airAcceleration = it }))
          .option(doubleOption("quake_max_air_accel",
            SourcedConfig.DEFAULT_MAX_AACEL_PER_TICK,
            { SourcedMod.config.maxAAccPerTick },
            { SourcedMod.config.maxAAccPerTick = it }))
          .option(doubleOption("quake_soft_cap_degen",
            SourcedConfig.DEFAULT_SOFT_CAP_DEGEN,
            { SourcedMod.config.softCapDegen },
            { SourcedMod.config.softCapDegen = it }))
          .build()
      )
      .group(
        OptionGroup.createBuilder()
          .name(Component.translatable("sourced.group.trimping"))
          .collapsed(false)
          .option(doubleOption("trimp_multiplier",
            SourcedConfig.DEFAULT_TRIMP_MULTIPLIER,
            { SourcedMod.config.trimpMultiplier },
            { SourcedMod.config.trimpMultiplier = it }))
          .build()
      )
      .group(
        OptionGroup.createBuilder()
          .name(Component.translatable("sourced.group.sharking"))
          .collapsed(false)
          .option(doubleOption("surface_tension",
            SourcedConfig.DEFAULT_SHARKING_SURFACE_TENSION,
            { SourcedMod.config.sharkingSurfaceTension },
            { SourcedMod.config.sharkingSurfaceTension = it }))
          .option(doubleOption("sharking_friction",
            SourcedConfig.DEFAULT_SHARKING_FRICTION,
            { SourcedMod.config.sharkingFriction },
            { SourcedMod.config.sharkingFriction = it }))
          .build()
      )
      .build()
  )
  .category(halfLifeCategory())
  .build()
  .generateScreen(parent)
