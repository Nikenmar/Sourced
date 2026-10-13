package cmrstudio.sourced.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import cmrstudio.sourced.SourcedPlayer;
import cmrstudio.sourced.TravelHandoff;

@SuppressWarnings("unused")
@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {
  protected MixinPlayer(EntityType<? extends LivingEntity> entityType, Level level) {
    super(entityType, level);
  }

  // The Half-Life modes apply their own jump impulse from inside travel, so vanilla's has to go.
  @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
  public void jumpHeadInject(CallbackInfo ci) {
    Player player = (Player) (Object) this;
    if (SourcedPlayer.INSTANCE.onJumpFromGround(player)) {
      ci.cancel();
    }
  }

  // Quake mode layers its caps and trimping on top of the vanilla jump.
  @Inject(method = "jumpFromGround", at = @At("TAIL"))
  public void jumpInject(CallbackInfo ci) {
    Player player = (Player) (Object) this;
    SourcedPlayer.INSTANCE.afterJump(player);
  }

  // A handled tick is not cancelled here: it lets super.travel run with its movement block switched
  // off (see MixinLivingEntity), so anything hooked onto the end of travel still sees the tick.
  @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
  public void travelInject(Vec3 movementInput, CallbackInfo ci) {
    Player player = (Player) (Object) this;
    boolean handled = SourcedPlayer.INSTANCE.travel(player, movementInput);

    // Player.travel's own prelude has two branches, flying and swimming. Flying never reaches a
    // handled tick, but the swimming flag can lead the swimming pose by a tick while sharking, and
    // its prelude would then rewrite the velocity we just integrated. Keep that tick cancelled.
    if (handled && player.isSwimming()) {
      player.calculateEntityAnimation(false);
      ci.cancel();
      return;
    }

    ((TravelHandoff) player).sourcedSetMovementHandled(handled);
  }

  @Inject(method = "tick", at = @At("HEAD"))
  public void tickInject(CallbackInfo ci) {
    Player player = (Player) (Object) this;
    SourcedPlayer.INSTANCE.beforeTick(player);
  }

  public boolean velocityChanged = false;

  @Inject(at = @At("HEAD"), method = "causeFallDamage")
  private void beforeFall(float fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
    // Store velocity changed state before fall damage is applied
    if (!this.level().isClientSide)
      velocityChanged = this.hurtMarked;
  }

  @Inject(method = "causeFallDamage", at = @At("RETURN"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/resources/ResourceLocation;I)V"), to = @At("TAIL")))
  private void afterFall(float fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
    // Undo the knockback a landing would apply, by restoring the state from before fall damage ran,
    // so a landing does not eat a bunnyhop
    if (!this.level().isClientSide)
      this.hurtMarked = velocityChanged;
  }
}
