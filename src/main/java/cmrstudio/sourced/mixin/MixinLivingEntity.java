package cmrstudio.sourced.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import cmrstudio.sourced.SourcedMod;
import cmrstudio.sourced.TravelHandoff;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements TravelHandoff {
  @Shadow private int noJumpDelay;

  // Set by MixinPlayer each tick: true when a Sourced pipeline has already moved the player.
  @Unique private boolean sourced$movementHandled;

  @Override
  public void sourcedSetMovementHandled(boolean handled) {
    this.sourced$movementHandled = handled;
  }

  @Inject(method = "aiStep", at = @At(value = "HEAD"))
  public void tickMovement(CallbackInfo ci) {
    if (SourcedMod.config.getNoJumpCooldown()) {
      noJumpDelay = 0;
    }
  }

  // Everything vanilla travel does to move the entity sits inside one
  // `if (isControlledByLocalInstance())`. Answering false on a tick we already moved skips that block
  // and nothing else: travel still animates the limbs and returns normally, so other mods' RETURN
  // injections into it fire. Cancelling Player.travel at HEAD skipped those too, and mods that carry
  // a player standing on a moving platform do it from exactly such an injection - which is why those
  // platforms slid out from under anyone using custom movement.
  @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isControlledByLocalInstance()Z"))
  private boolean sourced$skipHandledMovement(boolean controlled) {
    if (!sourced$movementHandled) return controlled;
    sourced$movementHandled = false;
    return false;
  }
}
