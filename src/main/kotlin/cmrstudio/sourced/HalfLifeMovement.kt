package cmrstudio.sourced

import cmrstudio.sourced.movecore.MoveCore
import cmrstudio.sourced.movecore.Preset
import cmrstudio.sourced.movecore.Surroundings
import cmrstudio.sourced.movecore.TickInput
import cmrstudio.sourced.movecore.Velocity
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.stats.Stats
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.CommonHooks
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Connects the three Half-Life presets to Minecraft.
 *
 * The movement itself - how the velocity changes over one tick - is decided by
 * [MoveCore]. This object only moves data between the game and that
 * core: it reads the player into a [TickInput], answers the core's questions about the world
 * through [Surroundings], and hands the resulting velocity to Minecraft's own collision. It carries
 * no movement rules of its own beyond what is Minecraft-specific: which ticks vanilla keeps, unit
 * conversion, status effects, and what happens after the move.
 */
object HalfLifeMovement {
  /** The core works in inches; one inch is 0.0254 m. */
  private const val METERS_PER_UNIT = 0.0254

  /** One Minecraft tick, in seconds. */
  private const val TICK_SECONDS = 0.05

  /** Minecraft velocities are blocks per tick; the core's are units per second. */
  private const val VEL_SCALE = 20.0 / METERS_PER_UNIT

  /**
   * Vanilla's slow-falling gravity over its normal gravity: -0.01 against -0.08. Applied as a
   * ratio so the preset's own gravity keeps meaning what it says.
   */
  private const val SLOW_FALLING_RATIO = 0.01 / 0.08

  /** Vanilla's per-tick drag on a normal block: `Block.friction` 0.6 times the 0.91 air term. */
  private const val VANILLA_GROUND_DRAG = 0.6 * 0.91

  /**
   * The ledge probe, in blocks. Ground counts as dropping away when nothing solid lies within
   * [LEDGE_PROBE_DEPTH] below a point [LEDGE_PROBE_AHEAD] ahead of the feet. Slightly more than a
   * block deep, so walking down a single step is not a ledge.
   */
  private const val LEDGE_PROBE_AHEAD = 0.5
  private const val LEDGE_PROBE_DEPTH = 1.05

  /** Whether jump was held on the previous tick, for the no-auto-hop case. */
  private var jumpHeldLastTick = false

  /** The view yaw at the end of the previous tick, so the core can spread the turn over the tick. */
  private var lastYaw = Double.NaN

  /** Per-tick tracing, switched on with -Dsourced.debug. Off in any normal launch. */
  private val DEBUG = System.getProperty("sourced.debug") != null
  private var debugTick = 0L

  private fun toUnits(v: Vec3) = Velocity(v.x * VEL_SCALE, v.y * VEL_SCALE, v.z * VEL_SCALE)
  private fun toBlocks(v: Velocity) = Vec3(v.x / VEL_SCALE, v.y / VEL_SCALE, v.z / VEL_SCALE)
  private fun toVec(v: Velocity) = Vec3(v.x, v.y, v.z)
  private fun toVelocity(v: Vec3) = Velocity(v.x, v.y, v.z)

  /**
   * Runs one tick of a Half-Life preset. Returns false to hand the tick back to vanilla
   * Minecraft movement.
   */
  fun travel(player: Player, movementInput: Vec3, mode: MovementMode): Boolean {
    val cfg = SourcedMod.config

    // Ladders and the swimming pose are not modelled here; let vanilla have them.
    if (player.onClimbable() || player.isVisuallySwimming) return false
    if (player.isInLava) return false

    // Water belongs to sharking, in every mode, so the feel of it never depends on the mode.
    if (player.isInWater) {
      return if (cfg.sharkingEnabled) SourcedPlayer.sharkingTravel(player, movementInput.x, movementInput.z)
      else false
    }
    // Another mod's fluid is neither water nor lava, so both tests above miss it and the player
    // would swim through it on land physics. Vanilla routes every fluid type through its own
    // movement, so hand those ticks back.
    if (player.isInFluidType) return false
    SourcedPlayer.swimming = false

    val preset = cfg.preset(mode)
    val rules = cfg.rules()

    // The speed walking converges on. By default it is Minecraft's: Player.aiStep has already
    // folded sprinting, Speed potions and Soul Speed into player.speed by the time travel runs, so
    // reading it there keeps every vanilla speed effect alive.
    val targetSpeed =
      if (cfg.hlUseVanillaSpeed) player.speed.toDouble() * cfg.hlVanillaSpeedMult * VEL_SCALE
      else preset.fullSpeed

    // Only the local player's own view history means anything; everyone else starts and ends the
    // tick at the same yaw.
    val client = player.level().isClientSide
    val yawEnd = player.yRot.toDouble()
    val yawStart = if (client && player.isLocalPlayer && !lastYaw.isNaN()) lastYaw else yawEnd
    if (client && player.isLocalPlayer) lastYaw = yawEnd

    val jumpHeld = SourcedPlayer.jumping
    val jumpHeldBefore = jumpHeldLastTick
    jumpHeldLastTick = jumpHeld

    // Vanilla adds 0.1 blocks/tick of jump velocity per Jump Boost level.
    val jumpBoost = player.getEffect(MobEffects.JUMP)?.let { (it.amplifier + 1) * 0.1 * VEL_SCALE } ?: 0.0

    val velIn = toUnits(player.deltaMovement)
    val groundIn = player.onGround()
    val world = PlayerSurroundings(player, preset, targetSpeed)
    val result = MoveCore.tick(
      preset, rules,
      TickInput(
        velocity = velIn,
        touchingGround = player.onGround(),
        jumpHeld = jumpHeld,
        jumpHeldBefore = jumpHeldBefore,
        yawStart = yawStart,
        yawEnd = yawEnd,
        strafe = movementInput.x,
        forward = movementInput.z,
        crouching = player.isShiftKeyDown,
        targetSpeed = targetSpeed,
        extraJumpSpeed = jumpBoost,
        tickSeconds = TICK_SECONDS,
      ),
      world,
    )
    val jumped = result.jumped
    var vel = toVec(result.velocity)

    // Entity.move only reports onGround when the move was clipped going down, and the core leaves a
    // grounded body with no vertical speed at all. Hand Minecraft one tick of gravity to collide
    // with; the floor clips it, so the position does not move.
    if (player.onGround() && !jumped) {
      vel = Vec3(vel.x, -preset.gravity * world.gravityScale(vel.y) * TICK_SECONDS, vel.z)
    }

    vel = applyVanillaVerticalEffects(player, vel)
    player.deltaMovement = toBlocks(toVelocity(vel))

    // Everything vanilla's own jump does besides moving the player. `Player.jumpFromGround` is
    // cancelled for these presets, so without this a hop would cost no hunger, never reach the jump
    // statistic, never fire LivingJumpEvent and never tell the server to resend the velocity. It
    // hangs off the jump the core actually performed, because auto-hop makes that differ from the
    // ticks vanilla would have jumped on. The event fires before the move, so a listener that edits
    // deltaMovement is still obeyed.
    if (jumped) {
      player.hasImpulse = true
      CommonHooks.onLivingJump(player)
      player.awardStat(Stats.JUMP)
      player.causeFoodExhaustion(if (player.isSprinting) 0.2f else 0.05f)
    }

    val handedY = player.deltaMovement.y
    player.move(MoverType.SELF, player.deltaMovement)

    // Vanilla closes travel by writing the vertical velocity back damped, so a player standing on
    // the ground holds y = -0.0784 rather than nothing. Other mods read that value (footstep sound
    // mods among them) to decide whether the player is falling, so keep it. Grounded only, and the
    // core discards any downward y it starts a grounded tick with, so this cannot reach the physics.
    if (player.onGround() && !jumped) {
      val dm = player.deltaMovement
      player.deltaMovement = Vec3(dm.x, handedY * 0.98, dm.z)
    }

    // "Blocks as ramps" - off by default. A Minecraft world is a grid of cubes, and at speed every
    // block corner is a wall that takes the run away. This adds no speed; it stops collisions from
    // removing it. Grazing a corner rescales whatever motion survived back to the speed carried
    // into the move; a square-on hit, which leaves nothing to rescale, turns the run into climb.
    // Only deltaMovement is touched, never the position, so collision still has the last word.
    if (cfg.hlBlockRamps && player.horizontalCollision) {
      val kept = toUnits(player.deltaMovement)
      val beforeH = sqrt(vel.x * vel.x + vel.z * vel.z)
      val keptH = sqrt(kept.x * kept.x + kept.z * kept.z)
      if (beforeH > preset.fullSpeed) {
        if (keptH > 1.0) {
          val scale = beforeH / keptH
          player.deltaMovement = toBlocks(Velocity(kept.x * scale, kept.y, kept.z * scale))
        } else {
          val climb = beforeH * cfg.hlBlockRampLift
          player.deltaMovement = toBlocks(Velocity(vel.x, kept.y.coerceAtLeast(climb), vel.z))
        }
      }
    }

    val after = toUnits(player.deltaMovement)

    if (DEBUG && client) {
      val before = sqrt(velIn.x * velIn.x + velIn.z * velIn.z)
      val now = sqrt(after.x * after.x + after.z * after.z)
      SourcedMod.logger.info(
        "t=%d ground=%b->%b jump=%b fired=%b spd=%.2f->%.2f m/s yawd=%.3f side=%.2f fwd=%.2f boost=%.1f ledge=%d gain=%.3f"
          .format(
            java.util.Locale.ROOT,
            debugTick++, groundIn, player.onGround(), jumpHeld, jumped,
            before * METERS_PER_UNIT, now * METERS_PER_UNIT,
            Mth.wrapDegrees(yawEnd - yawStart), movementInput.x, movementInput.z,
            result.boost, world.ledgeHits, world.smallestGainFactor,
          )
      )
    }

    SourcedPlayer.recordSpeed(player, sqrt(after.x * after.x + after.z * after.z) / VEL_SCALE)
    return true
  }

  /** The world as one player sees it, for one tick. */
  private class PlayerSurroundings(
    private val player: Player,
    private val preset: Preset,
    private val targetSpeed: Double,
  ) : Surroundings {
    /** Trace only: how many probes found a ledge, and the hardest gain cut this tick. */
    var ledgeHits = 0
    var smallestGainFactor = 1.0

    override fun gravityScale(verticalVelocity: Double): Double {
      if (player.isNoGravity) return 0.0
      // Levitation drives the vertical directly, the way vanilla does; gravity would fight it.
      if (player.hasEffect(MobEffects.LEVITATION)) return 0.0
      // Vanilla suspends gravity client-side while the chunk beneath you has not arrived.
      if (player.level().isClientSide) {
        val pos = player.blockPosition()
        val loaded = player.level().chunkSource
          .hasChunk(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))
        if (!loaded) return 0.0
      }
      // Gravity is an entity attribute, which is where datapacks and other mods reach for it. Read
      // it as a ratio against vanilla's default, so at the default nothing changes.
      var scale = player.getAttributeValue(Attributes.GRAVITY) / LivingEntity.DEFAULT_BASE_GRAVITY
      if (verticalVelocity <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) scale *= SLOW_FALLING_RATIO
      return scale
    }

    /**
     * `Block.friction` folded into the core's friction as a pure multiplier. Vanilla decays speed
     * by `0.91 * friction` per tick, so dividing `1 - 0.91 * f` by the same expression for an
     * ordinary block gives exactly 1.0 there, 0.238 on ice and 1.401 on honey.
     */
    override fun surfaceFriction(): Double {
      if (!player.onGround()) return 1.0
      val below = BlockPos.containing(player.x, player.boundingBox.minY - 1.0, player.z)
      val f = player.level().getBlockState(below).block.friction.toDouble()
      if (f <= 0.0) return 1.0
      return ((1.0 - 0.91 * f) / (1.0 - VANILLA_GROUND_DRAG)).coerceIn(0.0, 4.0)
    }

    override fun dropAhead(dirX: Double, dirZ: Double): Boolean {
      val start = Vec3(
        player.x + dirX * LEDGE_PROBE_AHEAD,
        player.boundingBox.minY,
        player.z + dirZ * LEDGE_PROBE_AHEAD,
      )
      val end = start.subtract(0.0, LEDGE_PROBE_DEPTH, 0.0)
      val hit = player.level().clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
      val ledge = hit.type == HitResult.Type.MISS
      if (ledge) ledgeHits++
      return ledge
    }

    override fun crouchLaunch(velocity: Velocity, fullSpeed: Double): Velocity? =
      SourcedPlayer.tryTrimpUnits(player, toVec(velocity), fullSpeed)?.let { toVelocity(it) }

    /**
     * Speed-gain falloff. Shortens only the part of a step that raises horizontal speed, keyed off
     * the walk target so a Speed potion moves the whole mechanic with it. Direction is never
     * touched, and steps that lose or hold speed pass through.
     */
    override fun filterGain(before: Velocity, after: Velocity): Velocity {
      val cfg = SourcedMod.config
      if (!cfg.hlGainFalloff) return after

      val had = sqrt(before.x * before.x + before.z * before.z)
      val got = sqrt(after.x * after.x + after.z * after.z)
      if (got <= had) return after

      val onset = targetSpeed * cfg.hlGainFalloffStart
      if (had <= onset) return after
      // Scale is a free-text field in the screen, so zero is reachable.
      val scale = targetSpeed * cfg.hlGainFalloffScale
      if (scale <= 0.0) return after
      val factor = cfg.hlGainFalloffCurve.factor((had - onset) / scale)
      // Below the onset the step must come back untouched, not recomputed, so ordinary movement is
      // identical to having no falloff at all.
      if (factor >= 1.0) return after
      smallestGainFactor = min(smallestGainFactor, factor)

      val kept = had + (got - had) * factor
      val k = kept / got
      return Velocity(after.x * k, after.y, after.z * k)
    }
  }

  /**
   * The two vanilla vertical effects that are velocity nudges rather than a change of gravity.
   * Both are per-tick in vanilla, so they are applied once per tick, after the core.
   */
  private fun applyVanillaVerticalEffects(player: Player, velocity: Vec3): Vec3 {
    var y = velocity.y

    // Powder snow lets you climb it while pressing into it. Ladders are handed back to vanilla
    // before the core runs, so only the powder-snow half of vanilla's test is needed here.
    if ((player.horizontalCollision || SourcedPlayer.jumping)
      && player.inBlockState.`is`(Blocks.POWDER_SNOW)
      && PowderSnowBlock.canEntityWalkOnPowderSnow(player)
    ) {
      y += 0.2 * VEL_SCALE
    }

    val levitation = player.getEffect(MobEffects.LEVITATION)
    if (levitation != null) {
      val target = 0.05 * (levitation.amplifier + 1) * VEL_SCALE
      y += (target - y) * 0.2
      player.resetFallDistance()
    } else if (y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
      player.resetFallDistance()
    }

    return Vec3(velocity.x, y, velocity.z)
  }

  /** Called when the mode changes so a stale "jump was held" does not block the first hop. */
  fun reset() {
    jumpHeldLastTick = false
    lastYaw = Double.NaN
  }
}
