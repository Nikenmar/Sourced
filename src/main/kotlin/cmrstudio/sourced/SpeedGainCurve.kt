package cmrstudio.sourced

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.exp

/**
 * Falloff shapes for `hlGainFalloff`. Maps normalised excess `t` to the fraction of a speed gain
 * that survives; 0 is the onset, 1 is one full scale past it. All return 1.0 at t = 0, so nothing
 * snaps on at the threshold.
 *
 * Whether a curve reaches zero is the whole distinction. INVERSE and EXPONENTIAL never do, so gains
 * get expensive but stay possible. QUADRATIC and LINEAR hit zero one scale up, which makes them a
 * hard cap with extra steps. Kept anyway, since a wall is a fair thing to want.
 */
@Serializable
enum class SpeedGainCurve {
  /** `1/(1+t)`. Diminishing returns. Half the gain one scale up, a third at two, a quarter at three. */
  @SerialName("inverse") INVERSE,

  /** `e^-t`. Never zero either, but tightens fast: 0.37, 0.14, 0.05 at one, two and three scales. */
  @SerialName("exponential") EXPONENTIAL,

  /** `(1-t)^2`, zero from t = 1. Lazy at first, then collapses. Soft landing into a hard stop. */
  @SerialName("quadratic") QUADRATIC,

  /** `1-t`, zero from t = 1. Constant loss, then dead stop. Not the default: reads as a wall. */
  @SerialName("linear") LINEAR;

  /** Callers only pass t > 0, where every curve lands in (0, 1]. Keep that true if you add one. */
  fun factor(t: Double): Double = when (this) {
    INVERSE -> 1.0 / (1.0 + t)
    EXPONENTIAL -> exp(-t)
    QUADRATIC -> if (t >= 1.0) 0.0 else (1.0 - t) * (1.0 - t)
    LINEAR -> if (t >= 1.0) 0.0 else 1.0 - t
  }

  /** Language-file key for the name shown in the config screen. */
  val translationKey: String
    get() = when (this) {
      INVERSE -> "sourced.curve.inverse"
      EXPONENTIAL -> "sourced.curve.exponential"
      QUADRATIC -> "sourced.curve.quadratic"
      LINEAR -> "sourced.curve.linear"
    }
}
