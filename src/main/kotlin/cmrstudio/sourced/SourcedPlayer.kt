package cmrstudio.sourced

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.PowderSnowBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.phys.Vec3
import kotlin.math.*


object SourcedPlayer {
  private var baseVelocities = mutableListOf<Pair<Double, Double>>()

  var previousSpeed: Double = 0.0
  var currentSpeed: Double = 0.0
  var jumping: Boolean = false
  var swimming: Boolean = false

  /**
   * Raises the vanilla 0.6 step height while a Half-Life mode is active, so a single block is
   * walked up rather than jumped and a run carries on over uneven ground.
   */
  private val STEP_HEIGHT_MODIFIER: ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(SourcedMod.ID, "hl_step_height")

  /** Mode-agnostic A/B tracing, switched on with -Dsourced.debug. */
  private val MOVE_DEBUG = System.getProperty("sourced.debug") != null
  private var traceLastYaw = Double.NaN

  private fun collectSpeed(speed: Double) {
    previousSpeed = currentSpeed
    currentSpeed = speed
  }

  /**
   * Speed bookkeeping for the HUD indicator, callable from [HalfLifeMovement].
   *
   * Gated to the local client player on purpose. In singleplayer travel() runs twice a tick - once
   * for the client player and once for the integrated server's - so leaving this ungated wrote
   * client and server speeds into the same pair alternately, and the indicator's colour compared
   * one against the other. That is the 20 Hz flicker.
   */
  internal fun recordSpeed(player: Player, speed: Double) {
    if (!player.level().isClientSide || !player.isLocalPlayer) return
    collectSpeed(speed)
  }

  internal fun spawnParticles(player: Player, numParticles: Int) = player.spawnBunnyhopParticles(numParticles)

  /**
   * Water movement, shared by every mode so that sharking feels the same whichever movement is
   * selected. Mirrors the water branch of [travelQuake].
   */
  internal fun sharkingTravel(player: Player, sidemove: Double, forwardmove: Double): Boolean {
    val wishdir = player.getMovementDirection(sidemove, forwardmove)
    val wishspeed = if (sidemove != 0.0 || forwardmove != 0.0) player.getBaseSpeedCurrent() else 0.0
    return player.travelWaterQuake(wishspeed, wishdir.first, wishdir.second)
  }

  /**
   * Trimping expressed in whatever unit the caller's velocity uses. Every term is a ratio except
   * the vertical one, which is derived from the same speed, so the maths is scale-invariant and
   * the Half-Life modes can reuse it unchanged.
   *
   * Returns the post-trimp velocity, or null when the trimp does not fire.
   */
  internal fun tryTrimpUnits(player: Player, velocity: Vec3, maxBaseSpeed: Double): Vec3? {
    val cfg = SourcedMod.config
    if (!cfg.trimpingEnabled || !player.isShiftKeyDown) return null

    // The stat is awarded before the speed test, so it counts attempts rather than trimps.
    if (player is ServerPlayer) player.awardStat(SourcedStats.TRIMPS, 1)

    val speed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
    if (speed <= maxBaseSpeed || maxBaseSpeed <= 0.0) return null

    var speedBonus = speed / maxBaseSpeed * 0.5
    if (speedBonus > 1.0) speedBonus = 1.0

    val trimpMultiplier = cfg.trimpMultiplier
    var result = velocity
    if (trimpMultiplier > 0) {
      val multiplier = 1.0 / trimpMultiplier
      result = Vec3(velocity.x * multiplier, speedBonus * speed * trimpMultiplier, velocity.z * multiplier)
    }

    player.spawnBunnyhopParticles(cfg.jumpParticles * 2)
    return result
  }

  /** Applied every tick so that toggling the mode or editing the value takes effect immediately. */
  internal fun updateStepHeight(player: Player) {
    val attribute = player.getAttribute(Attributes.STEP_HEIGHT) ?: return
    val cfg = SourcedMod.config
    val active = cfg.quakeMovementEnabled && cfg.movementMode.isHalfLife

    if (!active) {
      if (attribute.hasModifier(STEP_HEIGHT_MODIFIER)) attribute.removeModifier(STEP_HEIGHT_MODIFIER)
      return
    }

    // getValue() already folds in the base and every modifier, so this is idempotent and avoids
    // dirtying the attribute - and its network sync - on ticks where nothing changed.
    if (abs(attribute.value - cfg.hlStepHeight) > 1.0E-9) {
      attribute.removeModifier(STEP_HEIGHT_MODIFIER)
      attribute.addTransientModifier(
        AttributeModifier(
          STEP_HEIGHT_MODIFIER,
          cfg.hlStepHeight - attribute.baseValue,
          AttributeModifier.Operation.ADD_VALUE,
        )
      )
    }
  }

  // API
  fun updateVelocity(player: Entity, speed: Float, sidemove: Double, forwardmove: Double): Boolean {
    // When the bunnyhop is enabled
    if (!SourcedMod.config.quakeMovementEnabled) return false

    // Base velocities are a quake-mode concept. The Half-Life pipeline never calls moveRelative,
    // so wherever it *is* called we are in a fallback case that vanilla owns - leave it alone.
    if (SourcedMod.config.movementMode != MovementMode.QUAKE) return false

    if (player !is Player) return false // We are only interested in players
    if (!player.level().isClientSide) return false // And only in the client player
    if (player.isVisuallyCrawling) return false // Figure out a better solution later

    // And only on land
    if ((player.abilities.flying && player.vehicle == null) || player.isInWater || player.isInLava || player.onClimbable())
      return false

    val wishspeed = speed.toDouble() * 2.15
    val wishdir = player.getMovementDirection(sidemove, forwardmove)
    val wishvel = Pair(wishdir.first * wishspeed, wishdir.second * wishspeed)
    baseVelocities.add(wishvel)

    return true
  }

  /**
   * Called at the head of `Player.jumpFromGround`. The Half-Life modes drive the jump from the held
   * jump key inside [HalfLifeMovement.travel] - that is what makes auto-hop possible - so vanilla's
   * own impulse has to be suppressed, otherwise it would stack with the additive jump style.
   *
   * Returns true when the vanilla jump should be cancelled.
   */
  fun onJumpFromGround(player: Player): Boolean {
    if (!SourcedMod.config.quakeMovementEnabled) return false
    if (!SourcedMod.config.movementMode.isHalfLife) return false
    // Vanilla still owns the cases the Half-Life pipeline hands back: water, lava, ladders.
    if (player.isInWater || player.isInLava || player.onClimbable()) return false
    if (player.abilities.flying || player.isFallFlying || player.vehicle != null) return false
    return true
  }

  fun afterJump(player: Player) {
    if (SourcedMod.config.quakeMovementEnabled) {
      val maxMoveSpeed = player.getBaseSpeedMax()
      if (player.level().isClientSide) {
        if (player.isSprinting) {
          val f = player.yRot * 0.017453292f
          val xVel = player.deltaMovement.x + sin(f) * 0.2
          val zVel = player.deltaMovement.z - cos(f) * 0.2
          player.deltaMovement = Vec3(xVel, player.deltaMovement.y, zVel)
        }

        // Update last recorded speed
        val speed = player.getHorizontalSpeed()
        recordSpeed(player, speed)

        player.applySoftCap(maxMoveSpeed, speed)
      }

      val didTrimp = player.tryTrimp()
      if (!didTrimp) player.applyHardCap(maxMoveSpeed)
      else player.spawnBunnyhopParticles(SourcedMod.config.jumpParticles)
    }
  }

  fun travel(player: Player, movementInput: Vec3): Boolean {
    if (!SourcedMod.config.quakeMovementEnabled
      || player.abilities.flying
      || player.isFallFlying
      || player.vehicle != null
      // Vanilla gates its whole movement block on this. Without it we integrate physics for every
      // other player's entity on this client too, whose position the server is already dictating.
      || !player.isControlledByLocalInstance
    )
      return false

    val preX = player.x
    val preY = player.y
    val preZ = player.z

    // One trace shared by all three modes, so a mode switch mid-session produces directly
    // comparable numbers: how far the view swept, and how far the velocity vector actually
    // followed it. Everything else has been measured per-mode and proved nothing.
    val mode = SourcedMod.config.movementMode
    val trace = MOVE_DEBUG && player.level().isClientSide && player.isLocalPlayer
    val velBefore = player.deltaMovement
    val yawNow = player.yRot.toDouble()
    val viewSweep = if (trace && !traceLastYaw.isNaN()) Mth.wrapDegrees(yawNow - traceLastYaw) else 0.0
    if (trace) traceLastYaw = yawNow

    val handled = when (mode) {
      MovementMode.QUAKE -> player.travelQuake(movementInput.x, movementInput.z)
      else -> HalfLifeMovement.travel(player, movementInput, mode)
    }

    if (trace && handled) {
      val after = player.deltaMovement
      val s0 = sqrt(velBefore.x * velBefore.x + velBefore.z * velBefore.z)
      val s1 = sqrt(after.x * after.x + after.z * after.z)
      val turned = if (s0 < 0.005) 0.0 else Mth.wrapDegrees(
        Math.toDegrees(atan2(after.z, after.x)) - Math.toDegrees(atan2(velBefore.z, velBefore.x))
      )
      SourcedMod.logger.info(
        "AB mode=%s ground=%b spd=%.2f->%.2f m/s view=%.2f turned=%.2f side=%.2f fwd=%.2f".format(
          java.util.Locale.ROOT,
          mode.name, player.onGround(), s0 * 20.0, s1 * 20.0, viewSweep, turned,
          movementInput.x, movementInput.z,
        )
      )
    }

    if (handled) {
      val xPow = (player.x - preX).pow(2)
      val yPow = (player.y - preY).pow(2)
      val zPow = (player.z - preZ).pow(2)
      val distance = (xPow + yPow + zPow).pow(1.0 / 2.0)

      val flying = (player.abilities.flying || player.isFallFlying)

      // Apparently stats are stored with 2-digit fixed point precision
      if (player is ServerPlayer && jumping && distance > 0.0) {
        if (player.isInWater && !flying) {
          player.awardStat(SourcedStats.SHARK_ONE_CM, (distance * 100.0).roundToInt())
        } else {
          player.awardStat(SourcedStats.BHOP_ONE_CM, (distance * 100.0).roundToInt())
        }
      }

      // Limbs are animated by vanilla travel, which still runs to its end on a handled tick.
      return true
    }

    jumping = false
    return false
  }

  fun beforeTick(player: Player) {
    updateStepHeight(player)

    if (player.level().isClientSide && baseVelocities.isNotEmpty())
      baseVelocities.clear()
  }

  // Quake movement
  private fun Player.getMovementDirection(sidemoveInitial: Double, forwardmoveInitial: Double): Pair<Double, Double> {
    val preSpeed = sidemoveInitial * sidemoveInitial + forwardmoveInitial * forwardmoveInitial
    if (preSpeed >= 0) {
      var speed = sqrt(preSpeed)

      speed = if (speed < 1.0) 1.0
      else 1.0 / speed

      val sidemove = sidemoveInitial * speed
      val forwardmove = forwardmoveInitial * speed
      val f1 = sin(this.yRot * Math.PI / 180.0)
      val f2 = cos(this.yRot * Math.PI / 180.0)

      return Pair(
        (sidemove * f2 - forwardmove * f1),
        (forwardmove * f2 + sidemove * f1),
      )
    }

    return Pair(0.0, 0.0)
  }

  private const val QUAKE_MOVEMENT_SPEED_MULTIPLIER = 2.15
  private const val QUAKE_SNEAKING_SPEED_MULTIPLIER = 1.10
  private fun Player.getSlipperiness(): Double {
    if (this.onGround()) {
      val groundPos = BlockPos.containing(this.x, this.boundingBox.minY - 1, this.z)
      return this.level().getBlockState(groundPos).block.friction.toDouble()
    }

    return 0.0
  }

  private fun Player.getBaseSpeedCurrent(): Double {
    val baseSpeed = this.speed.toDouble()
    return if (!this.isShiftKeyDown) baseSpeed * QUAKE_MOVEMENT_SPEED_MULTIPLIER else baseSpeed * QUAKE_SNEAKING_SPEED_MULTIPLIER
  }

  private fun Player.getBaseSpeedMax(): Double {
    val baseSpeed = this.speed.toDouble()
    return baseSpeed * QUAKE_MOVEMENT_SPEED_MULTIPLIER
  }

  /**
   * Horizontal speed of the player. Not called `getSpeed`, because `LivingEntity` already has a
   * `getSpeed()` and a member always wins over an extension.
   */
  private fun Player.getHorizontalSpeed(): Double {
    val x = this.deltaMovement.x
    val z = this.deltaMovement.z
    return sqrt((x * x + z * z))
  }

  private fun Player.travelQuake(sidemove: Double, forwardmove: Double): Boolean {
    // Fallback to default minecraft movement
    if (this.onClimbable() || this.isVisuallySwimming) return false

    val flying = (this.abilities.flying || this.isFallFlying)
    if (this.isInLava && !flying) return false // Swimming in lava

    // Collect all relevant movement values
    val wishdir = this.getMovementDirection(sidemove, forwardmove)
    val wishspeed = if (sidemove != 0.0 || forwardmove != 0.0) this.getBaseSpeedCurrent() else 0.0
    val onGroundForReal = this.onGround() && !jumping

    // Sharking
    if (this.isInWater && !flying) {
      // Use default minecraft water movement if disabled
      return if (SourcedMod.config.sharkingEnabled) this.travelWaterQuake(wishspeed, wishdir.first, wishdir.second) else false
    } else {
      swimming = false
    }

    // Ground movement
    if (onGroundForReal) {
      val slipperiness = this.getSlipperiness()
      val xVel = this.deltaMovement.x * slipperiness
      val zVel = this.deltaMovement.z * slipperiness
      this.deltaMovement = Vec3(xVel, this.deltaMovement.y, zVel)

      if (wishspeed != 0.0) { // Alter based on the surface friction
        val acceleration = SourcedMod.config.acceleration * (0.16277136 / (slipperiness * slipperiness * slipperiness))
        this.accelerate(wishspeed, wishdir.first, wishdir.second, acceleration, slipperiness)
      }

      if (baseVelocities.isNotEmpty()) {
        var x = this.deltaMovement.x
        var z = this.deltaMovement.z
        val speedMod = wishspeed / this.getBaseSpeedMax()

        // add in base velocities
        for (baseVel in baseVelocities) {
          x += baseVel.first * speedMod
          z += baseVel.second * speedMod
        }

        this.deltaMovement = Vec3(x, this.deltaMovement.y, z)
      }
    }

    // Air movement
    else {
      val airAcceleration = SourcedMod.config.airAcceleration
      this.airAccelerate(wishspeed, wishdir.first, wishdir.second, airAcceleration)

      // Movement on top of water
      if (SourcedMod.config.sharkingEnabled && SourcedMod.config.sharkingSurfaceTension > 0.0 && jumping && this.deltaMovement.y < 0.0) {
        val isFallingIntoWater = this.level().containsAnyLiquid(this.boundingBox.move(this.deltaMovement))
        if (isFallingIntoWater) this.deltaMovement = Vec3(this.deltaMovement.x, this.deltaMovement.y * SourcedMod.config.sharkingSurfaceTension, this.deltaMovement.z)
      }
    }

    // Apply velocity
    this.move(MoverType.SELF, this.deltaMovement)

    // Gravity in one step per tick, after the move
    this.applyQuakeGravity()

    // Cancel default minecraft movement behavior
    return true
  }

  private fun Player.applyQuakeGravity() {
    val levitating = hasEffect(MobEffects.LEVITATION)

    var yVel = this.deltaMovement.y
    var gravity = -0.08 // gravity

    // Powdered snow
    if ((this.horizontalCollision || jumping)
      && (this.onClimbable() || inBlockState.`is`(Blocks.POWDER_SNOW)
          && PowderSnowBlock.canEntityWalkOnPowderSnow(this))
    ) {
      yVel += 0.2
    }

    // Slow falling
    if (deltaMovement.y <= 0.0 && hasEffect(MobEffects.SLOW_FALLING)) {
      gravity = -0.01
      this.resetFallDistance()
    }

    // Levitation
    if (levitating) {
      yVel += (0.05 * (getEffect(MobEffects.LEVITATION)!!.amplifier + 1).toDouble() - yVel) * 0.2
      resetFallDistance()
    }

    // Apply gravity
    if (!level().isClientSide || level().chunkSource.hasChunk(SectionPos.blockToSectionCoord(blockPosition().x), SectionPos.blockToSectionCoord(blockPosition().z))) {
      if (!isNoGravity && !levitating)
        yVel += gravity

      val airResistance = 0.9800000190734863
      this.deltaMovement = Vec3(this.deltaMovement.x, yVel * airResistance, this.deltaMovement.z)
    } else { // If chunk is not loaded slowly fall to bottomY
      yVel = if (this.y > level().minBuildHeight.toDouble()) -0.1 else 0.0
      this.deltaMovement = Vec3(this.deltaMovement.x, yVel, this.deltaMovement.z)
    }

  }

  private fun Player.accelerate(wishspeed: Double, wishX: Double, wishZ: Double, acceleration: Double, slipperiness: Double) {
    // Speed already carried along the wanted direction
    val currentSpeed = this.deltaMovement.x * wishX + this.deltaMovement.z * wishZ

    // How much is still missing; nothing to do once it is reached
    val addSpeed = wishspeed - currentSpeed
    if (addSpeed <= 0) return

    // This tick's push, never more than what is missing
    var accelSpeed = acceleration * wishspeed / slipperiness * 0.05
    if (accelSpeed > addSpeed) accelSpeed = addSpeed

    val x = this.deltaMovement.x + accelSpeed * wishX
    val z = this.deltaMovement.z + accelSpeed * wishZ
    this.deltaMovement = Vec3(x, this.deltaMovement.y, z)
  }

  private fun Player.airAccelerate(wishspeedInitial: Double, wishX: Double, wishZ: Double, accel: Double) {
    val maxAirAcceleration = SourcedMod.config.maxAAccPerTick
    val wishspeed = if (wishspeedInitial > maxAirAcceleration) maxAirAcceleration else wishspeedInitial

    // Speed already carried along the wanted direction
    val currentSpeed = this.deltaMovement.x * wishX + this.deltaMovement.z * wishZ

    // How much is still missing, against the capped target; nothing to do once it is reached
    val addSpeed = wishspeed - currentSpeed
    if (addSpeed <= 0) return

    // The push uses the uncapped target, so the cap limits how far, not how fast
    var accelSpeed = accel * wishspeedInitial * 0.05
    if (accelSpeed > addSpeed) accelSpeed = addSpeed

    val x = this.deltaMovement.x + accelSpeed * wishX
    val z = this.deltaMovement.z + accelSpeed * wishZ
    this.deltaMovement = Vec3(x, this.deltaMovement.y, z)
  }

  private fun Player.applySoftCap(baseSpeed: Double, speed: Double) {
    var softCapPercent = SourcedMod.config.softCapThreshold
    var softCapDegen = SourcedMod.config.softCapDegen
    if (SourcedMod.config.uncappedBunnyhop) {
      softCapPercent = 1.0
      softCapDegen = 1.0
    }

    val softCap = baseSpeed * softCapPercent

    // apply soft cap first; if soft -> hard is not done, then you can continually trigger only the hard cap and stay at the hard cap
    if (speed > softCap) {
      if (softCapDegen != 1.0) {
        val appliedCap = (speed - softCap) * softCapDegen + softCap
        val multiplier = appliedCap / speed

        val x = this.deltaMovement.x * multiplier
        val z = this.deltaMovement.z * multiplier
        this.deltaMovement = Vec3(x, this.deltaMovement.y, z)
      }
    }
  }

  private fun Player.applyHardCap(baseSpeed: Double) {
    if (SourcedMod.config.uncappedBunnyhop) return

    val hardCapPercent = SourcedMod.config.hardCapThreshold
    val hardCap = baseSpeed * hardCapPercent
    val speed = this.getHorizontalSpeed()

    if (speed > hardCap && hardCap != 0.0) {
      val multiplier = hardCap / speed
      val xVel = this.deltaMovement.x * multiplier
      val zVel = this.deltaMovement.z * multiplier
      this.deltaMovement = Vec3(xVel, this.deltaMovement.y, zVel)
    }
  }

  // Trimping
  private fun Player.tryTrimp(): Boolean {
    if (SourcedMod.config.trimpingEnabled && this.isShiftKeyDown) {
      if (this is ServerPlayer) {
        this.awardStat(SourcedStats.TRIMPS, 1)
      }

      val speed = this.getHorizontalSpeed()
      val maxBaseSpeed = this.getBaseSpeedMax()

      // Player has gained at least some additional speed to walking speed
      if (speed > maxBaseSpeed) {
        var speedBonus = speed / maxBaseSpeed * 0.5
        if (speedBonus > 1.0) speedBonus = 1.0

        val trimpMultiplier = SourcedMod.config.trimpMultiplier

        if (trimpMultiplier > 0) {
          val multiplier = 1.0 / trimpMultiplier
          val xVel = this.deltaMovement.x * multiplier
          val zVel = this.deltaMovement.z * multiplier
          val yVel = speedBonus * speed * trimpMultiplier
          this.deltaMovement = Vec3(xVel, yVel, zVel)
        }

        this.spawnBunnyhopParticles(SourcedMod.config.jumpParticles * 2)

        return true
      }
    }

    return false
  }

  // Sharking
  private fun Player.travelWaterQuake(wishspeed: Double, wishX: Double, wishZ: Double): Boolean {
    if (!level().isClientSide) return true

    // Collect all relevant movement values
    val speed = this.getHorizontalSpeed()

    // Move in water
    if (!jumping || !this.isFree(0.0, 1.0, 0.0) || speed < 0.078f) {
      swimming = true
      return false
    }

    // Swim in water
    else {
      swimming = false

      // Update last recorded speed
      collectSpeed(speed)

      // Apply friction
      if (speed > 0.090) this.deltaMovement = this.deltaMovement.scale(SourcedMod.config.sharkingFriction)

      // Accelerate
      if (speed > 0.098) this.airAccelerate(wishspeed, wishX, wishZ, SourcedMod.config.acceleration)
      else this.accelerate(wishspeed, wishX, wishZ, SourcedMod.config.acceleration, 1.0)

      this.move(MoverType.SELF, this.deltaMovement)
      this.deltaMovement = Vec3(deltaMovement.x, if (deltaMovement.y >= 0) deltaMovement.y else 0.0, deltaMovement.z)
    }

    return true
  }

  // Particles
  private fun Player.spawnBunnyhopParticles(numParticles: Int) {
    if (numParticles < 1) return

    // taken from sprint
    val i = floor(this.x).toInt()
    val j = floor(this.y - 0.20000000298023224).toInt()
    val k = floor(this.z).toInt()

    val blockState = this.level().getBlockState(BlockPos(i, j, k))
    if (blockState.renderShape != RenderShape.INVISIBLE) {
      for (iParticle in 0 until numParticles) {
        val x = this.x + (this.random.nextFloat() - 0.5) * this.bbWidth
        val z = this.z + (this.random.nextFloat() - 0.5) * this.bbWidth
        val y = this.boundingBox.minY + 0.1

        val xVel = -this.deltaMovement.x * 4.0
        val zVel = -this.deltaMovement.z
        val yVel = 1.5

        val effect = BlockParticleOption(ParticleTypes.BLOCK, blockState)
        this.level().addParticle(effect, x, y, z, xVel, yVel, zVel)
      }
    }
  }
}
