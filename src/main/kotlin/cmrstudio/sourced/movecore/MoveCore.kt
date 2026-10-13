package cmrstudio.sourced.movecore

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One movement preset: every value that makes a preset move the way it does.
 */
class Preset(
  /** Downward acceleration, units/s². */
  val gravity: Double,
  /** Reference speed for the hop clip, the crouch-launch, the boost ceiling and the air push rate. */
  val fullSpeed: Double,
  /** Speed asked for by one unit of strafe or forward input. */
  val inputScale: Double,
  /** Ground acceleration factor. */
  val groundAcceleration: Double,
  /** Air acceleration factor. */
  val airAcceleration: Double,
  /** Most the air step may ask for along the steering direction. */
  val airSpeedCap: Double,
  /** Ground friction factor. */
  val friction: Double,
  /** Friction multiplier where the ground drops away ahead; 1.0 turns it off. */
  val edgeFriction: Double,
  /** Speed below which friction acts as if the body moved at this speed. */
  val stopSpeed: Double,
  /** Friction for ground commands without input; 0 or less turns it off. */
  val idleFriction: Double,
  /** Per-axis velocity bound. */
  val maxVelocity: Double,
  /** Jump height in units. */
  val jumpHeight: Double,
  /** Commands each tick is split into (used clamped to 1..20). */
  val commandsPerTick: Int,
  /** Whether a jump clips an excessive speed. */
  val hopClip: Boolean,
  /** Whether this preset offers the forward boost on jumps. */
  val forwardBoost: Boolean,
  /** True: the jump impulse adds to the vertical speed; false: it replaces it. */
  val additiveJump: Boolean,
  /** Whether this preset softens horizontal speed above a ceiling when jumping. */
  val softSpeedCap: Boolean,
) {
  companion object {
    /** The "Half-Life" preset's defaults. */
    fun halfLife(): Preset = Preset(
      gravity = 1260.0,
      fullSpeed = 320.0,
      inputScale = 400.0,
      groundAcceleration = 10.0,
      airAcceleration = 10.0,
      airSpeedCap = 30.0,
      friction = 4.0,
      edgeFriction = 2.0,
      stopSpeed = 100.0,
      idleFriction = 10.96,
      maxVelocity = 2000.0,
      jumpHeight = 58.7,
      commandsPerTick = 5,
      hopClip = false,
      forwardBoost = false,
      additiveJump = false,
      softSpeedCap = false,
    )

    /** The "Half-Life 2" preset's defaults. */
    fun halfLife2(): Preset = Preset(
      gravity = 1260.0,
      fullSpeed = 320.0,
      inputScale = 450.0,
      groundAcceleration = 10.0,
      airAcceleration = 10.0,
      airSpeedCap = 30.0,
      friction = 4.0,
      edgeFriction = 1.0,
      stopSpeed = 100.0,
      idleFriction = 10.96,
      maxVelocity = 3500.0,
      jumpHeight = 58.7,
      commandsPerTick = 3,
      hopClip = false,
      forwardBoost = true,
      additiveJump = true,
      softSpeedCap = false,
    )

    /** The "HL1 Movement (Source)" preset's defaults. */
    fun hl1Movement(): Preset = Preset(
      gravity = 1260.0,
      fullSpeed = 320.0,
      inputScale = 450.0,
      groundAcceleration = 10.0,
      airAcceleration = 30.0,
      airSpeedCap = 30.0,
      friction = 4.0,
      edgeFriction = 1.0,
      stopSpeed = 100.0,
      idleFriction = 10.96,
      maxVelocity = 3500.0,
      jumpHeight = 58.7,
      commandsPerTick = 3,
      hopClip = false,
      forwardBoost = false,
      additiveJump = false,
      softSpeedCap = false,
    )
  }
}

/**
 * Behaviour switches shared by every preset.
 */
class Rules(
  /** Holding jump keeps jumping on every landing. */
  val autoHop: Boolean,
  /** Master switch for the forward boost. */
  val forwardBoostEnabled: Boolean,
  /** Boost ceiling as a multiple of full speed; 0 or less means no ceiling. */
  val boostCeiling: Double,
  /** Soft ceiling threshold as a multiple of the target speed. */
  val softCapThreshold: Double,
  /** Fraction of the speed above the soft ceiling that is kept; 1.0 turns it off. */
  val softCapKeep: Double,
) {
  companion object {
    /** The defaults. */
    fun defaults(): Rules = Rules(
      autoHop = true,
      forwardBoostEnabled = true,
      boostCeiling = 0.0,
      softCapThreshold = 6.0,
      softCapKeep = 0.7,
    )
  }
}

object MoveCore {
  private const val MAX_COMMANDS = 20

  /** Runs one tick. Pure: no state survives between calls. */
  fun tick(preset: Preset, rules: Rules, input: TickInput, world: Surroundings): TickResult {
    val body = Body(input.velocity)
    if (input.touchingGround && body.y < 0.0) body.y = 0.0
    var grounded = input.touchingGround && !input.jumpHeld

    val commands = preset.commandsPerTick.coerceIn(1, MAX_COMMANDS)
    val dt = input.tickSeconds / commands
    val control = shapeInput(preset, input)
    val turn = wrapDegrees(input.yawEnd - input.yawStart)

    var jumpPending = input.jumpHeld && input.touchingGround && (rules.autoHop || !input.jumpHeldBefore)
    var jumped = false
    var boost = 0.0

    for (k in 1..commands) {
      val yaw = input.yawStart + turn * (k.toDouble() / commands)
      val (dirX, dirZ) = steeringDirection(control, yaw)

      val g = preset.gravity * world.gravityScale(body.y)
      body.y -= g * 0.5 * dt
      body.bound(preset.maxVelocity)

      if (jumpPending) {
        boost = jump(preset, rules, input, world, body, control, g, dt)
        jumpPending = false
        grounded = false
        jumped = true
      }

      if (grounded) {
        body.y = 0.0
        groundFriction(preset, world, body, control.wanted, dt)
      }
      body.bound(preset.maxVelocity)

      if (grounded) {
        groundAccelerate(preset, body, dirX, dirZ, control.wanted, input.targetSpeed, dt)
        body.y = 0.0
      } else {
        val before = body.toVelocity()
        val after = airAccelerate(preset, before, dirX, dirZ, control.wanted, dt)
        body.set(world.filterGain(before, after))
      }

      body.y -= g * 0.5 * dt
      body.bound(preset.maxVelocity)
      if (grounded) body.y = 0.0
    }

    return TickResult(body.toVelocity(), jumped, boost)
  }

  /** The tick's shaped input: scaled strafe and forward, limited to the target speed. */
  private class Control(val strafe: Double, val forward: Double, val wanted: Double)

  private fun shapeInput(preset: Preset, input: TickInput): Control {
    var s = input.strafe * preset.inputScale
    var f = input.forward * preset.inputScale
    val m = sqrt(s * s + f * f)
    if (m > input.targetSpeed && m != 0.0) {
      val scale = input.targetSpeed / m
      s *= scale
      f *= scale
    }
    val wanted = min(sqrt(s * s + f * f), input.targetSpeed)
    return Control(s, f, wanted)
  }

  /** Folds an angle difference into [-180, 180). */
  private fun wrapDegrees(angle: Double): Double {
    var r = angle % 360.0
    if (r >= 180.0) r -= 360.0
    if (r < -180.0) r += 360.0
    return r
  }

  /** Horizontal unit direction the input asks for when looking along [yaw], or zero. */
  private fun steeringDirection(control: Control, yaw: Double): Pair<Double, Double> {
    val theta = yaw * Math.PI / 180.0
    val sinT = sin(theta)
    val cosT = cos(theta)
    val ax = control.strafe * cosT - control.forward * sinT
    val az = control.forward * cosT + control.strafe * sinT
    val length = sqrt(ax * ax + az * az)
    return if (length == 0.0) 0.0 to 0.0 else (ax / length) to (az / length)
  }

  /** Slows a grounded body. The body's vertical speed is already zero. */
  private fun groundFriction(preset: Preset, world: Surroundings, body: Body, wanted: Double, dt: Double) {
    val speed = body.length()
    if (speed < 0.1) return

    if (preset.idleFriction > 0.0 && wanted <= 0.0) {
      val loss = speed * preset.idleFriction * world.surfaceFriction() * dt
      body.scale(max(speed - loss, 0.0) / speed)
      return
    }

    var mu = preset.friction * world.surfaceFriction()
    if (preset.edgeFriction != 1.0 && world.dropAhead(body.x / speed, body.z / speed)) {
      mu *= preset.edgeFriction
    }
    val base = if (speed >= preset.stopSpeed) speed else preset.stopSpeed
    val loss = base * mu * dt
    body.scale(max(speed - loss, 0.0) / speed)
  }

  /** Ground acceleration towards the wanted speed at a rate set by the target speed. */
  private fun groundAccelerate(
    preset: Preset, body: Body, dirX: Double, dirZ: Double, wanted: Double, targetSpeed: Double, dt: Double,
  ) {
    val along = body.x * dirX + body.z * dirZ
    val gap = wanted - along
    if (gap <= 0.0) return
    var push = preset.groundAcceleration * dt * targetSpeed
    if (push > gap) push = gap
    body.x += push * dirX
    body.z += push * dirZ
  }

  /** Air steering: capped along the direction, but pushing at a rate set by full speed. */
  private fun airAccelerate(
    preset: Preset, v: Velocity, dirX: Double, dirZ: Double, wanted: Double, dt: Double,
  ): Velocity {
    val cappedWanted = if (wanted > preset.airSpeedCap) preset.airSpeedCap else wanted
    val along = v.x * dirX + v.z * dirZ
    val gap = cappedWanted - along
    if (gap <= 0.0) return v
    var push = preset.airAcceleration * preset.fullSpeed * dt
    if (push > gap) push = gap
    return Velocity(v.x + push * dirX, v.y, v.z + push * dirZ)
  }

  /** Performs the jump inside the first command. Returns the tick's boost result. */
  private fun jump(
    preset: Preset, rules: Rules, input: TickInput, world: Surroundings,
    body: Body, control: Control, g: Double, dt: Double,
  ): Double {
    val full = preset.fullSpeed

    if (preset.hopClip) {
      val limit = 1.7 * full
      val speed = body.length()
      if (limit > 0.0 && speed > limit) body.scale((limit / speed) * 0.65)
    }

    if (preset.softSpeedCap && rules.softCapKeep != 1.0) {
      val ceiling = input.targetSpeed * rules.softCapThreshold
      val horizontal = body.horizontalLength()
      if (horizontal > ceiling && horizontal > 0.0) {
        val kept = (horizontal - ceiling) * rules.softCapKeep + ceiling
        val factor = kept / horizontal
        body.x *= factor
        body.z *= factor
      }
    }

    val launched = world.crouchLaunch(body.toVelocity(), full)
    if (launched != null) {
      body.set(Velocity(launched.x, launched.y - g * 0.5 * dt, launched.z))
      return 0.0
    }

    val impulse = sqrt(2.0 * preset.gravity * preset.jumpHeight) + input.extraJumpSpeed
    body.y = if (preset.additiveJump && !input.crouching) body.y + impulse else impulse

    var boost = 0.0
    val f = control.forward
    val side = sqrt(max(control.wanted * control.wanted - f * f, 0.0))
    val earned = f < 0.0 || side >= 1.0
    if (preset.forwardBoost && earned && rules.forwardBoostEnabled) {
      val theta = input.yawEnd * Math.PI / 180.0
      val facingX = -sin(theta)
      val facingZ = cos(theta)
      val horizontal = body.horizontalLength()
      var add = abs(f * 0.5)
      val ceiling = if (rules.boostCeiling > 0.0) full * rules.boostCeiling else Double.MAX_VALUE
      if (add + horizontal > ceiling) add -= (add + horizontal) - ceiling
      if (add < 0.0) add = 0.0
      if (f < 0.0) add = -add
      val before = body.toVelocity()
      val candidate = Velocity(before.x + facingX * add, before.y, before.z + facingZ * add)
      body.set(world.filterGain(before, candidate))
      boost = add
    }

    body.y -= g * 0.5 * dt
    return boost
  }

  /** The velocity being worked on during one tick; local to a single call. */
  private class Body(start: Velocity) {
    var x = start.x
    var y = start.y
    var z = start.z

    fun length(): Double = sqrt(x * x + y * y + z * z)

    fun horizontalLength(): Double = sqrt(x * x + z * z)

    fun scale(k: Double) {
      x *= k
      y *= k
      z *= k
    }

    /** Clamps each axis to [-limit, limit]; written as min(max(..)) so a negative limit cannot throw. */
    fun bound(limit: Double) {
      x = min(max(x, -limit), limit)
      y = min(max(y, -limit), limit)
      z = min(max(z, -limit), limit)
    }

    fun set(v: Velocity) {
      x = v.x
      y = v.y
      z = v.z
    }

    fun toVelocity(): Velocity = Velocity(x, y, z)
  }
}
