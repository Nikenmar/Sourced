package cmrstudio.sourced.movecore

/** A velocity in units per second. `x` and `z` are horizontal, `y` is up. */
data class Velocity(val x: Double, val y: Double, val z: Double)

/**
 * What the core asks of the world it moves in. The caller implements it; the core calls it
 * at fixed points of the tick, and nowhere else.
 */
interface Surroundings {
  /** Factor on the preset's gravity for a body whose vertical velocity is [verticalVelocity]. */
  fun gravityScale(verticalVelocity: Double): Double

  /** Factor on ground friction from the surface underfoot. */
  fun surfaceFriction(): Double

  /** True when the ground drops away ahead in the horizontal unit direction ([dirX], [dirZ]). */
  fun dropAhead(dirX: Double, dirZ: Double): Boolean

  /**
   * The crouch-launch: given the velocity at jump time and the preset's full speed, returns the
   * velocity that replaces the whole jump, or null when no launch happens.
   */
  fun crouchLaunch(velocity: Velocity, fullSpeed: Double): Velocity?

  /**
   * Gives the caller the chance to shorten a horizontal speed gain. Called with the velocity
   * before and after a step; the returned velocity is kept.
   */
  fun filterGain(before: Velocity, after: Velocity): Velocity
}

/** Everything one tick needs besides the preset, the rules and the surroundings. */
class TickInput(
  /** Velocity at the start of the tick. */
  val velocity: Velocity,
  /** Whether the body is standing on something at the start of the tick. */
  val touchingGround: Boolean,
  /** Whether the jump control is held this tick. */
  val jumpHeld: Boolean,
  /** Whether the jump control was held on the previous tick. */
  val jumpHeldBefore: Boolean,
  /** View yaw in degrees where the previous tick ended. */
  val yawStart: Double,
  /** View yaw in degrees now. */
  val yawEnd: Double,
  /** Sideways input, nominally -1..1. */
  val strafe: Double,
  /** Forward input, nominally -1..1; negative is backwards. */
  val forward: Double,
  /** Whether the crouch control is held. */
  val crouching: Boolean,
  /** The walking speed the ground converges on, in units per second. */
  val targetSpeed: Double,
  /** Extra upward speed added to every jump impulse, in units per second. */
  val extraJumpSpeed: Double,
  /** Length of the tick in seconds. */
  val tickSeconds: Double,
)

/** What one tick produced. */
class TickResult(
  /** Velocity at the end of the tick. */
  val velocity: Velocity,
  /** Whether a jump happened this tick (a crouch-launch counts). */
  val jumped: Boolean,
  /** The signed forward-boost speed added this tick; 0.0 when none was added. */
  val boost: Double,
)
