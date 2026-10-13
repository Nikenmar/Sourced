package cmrstudio.sourced

/**
 * Lets the head of `Player.travel` tell `LivingEntity.travel` that a Sourced pipeline has already
 * moved the entity this tick. Implemented on every `LivingEntity` by `MixinLivingEntity`.
 */
interface TravelHandoff {
  fun sourcedSetMovementHandled(handled: Boolean)
}
