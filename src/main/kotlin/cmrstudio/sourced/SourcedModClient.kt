package cmrstudio.sourced

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW
import java.util.function.Supplier
import kotlin.math.roundToInt

/** Client entrypoint. */
@Mod(value = SourcedMod.ID, dist = [Dist.CLIENT])
class SourcedModClientEntry(modBus: IEventBus, container: ModContainer) {
  init {
    // The config screen opens straight from NeoForge's mod list.
    container.registerExtensionPoint(
      IConfigScreenFactory::class.java,
      Supplier { IConfigScreenFactory { _, parent -> generateConfigScreen(parent) } },
    )

    modBus.addListener(RegisterKeyMappingsEvent::class.java, SourcedModClient::onRegisterKeyMappings)

    NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre::class.java) { SourcedModClient.onEndClientTick() }
    NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post::class.java, SourcedModClient::onRenderHud)

  }
}

object SourcedModClient {
  /** m/s of change below which the speed readout stays neutral instead of strobing. */
  private const val COLOUR_DEAD_BAND = 0.05
  private var indicatorVisible = false

  // All three ship unbound. A mod has no business claiming letter keys on a fresh install - B and
  // V belong to whatever the player already uses them for, and every one of these is a convenience
  // that the config screen also offers. Bind them in Controls if you want them.
  private val keyToggle = KeyMapping("key.${SourcedMod.ID}.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.${SourcedMod.ID}")
  private val keyConfig = KeyMapping("key.${SourcedMod.ID}.config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.${SourcedMod.ID}")
  private val keyCycleMode = KeyMapping("key.${SourcedMod.ID}.cycle_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.${SourcedMod.ID}")

  fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
    event.register(keyToggle)
    event.register(keyConfig)
    event.register(keyCycleMode)
  }

  fun onEndClientTick() {
    val client = Minecraft.getInstance()

    if (client.player != null)
      SourcedPlayer.jumping = client.player!!.input.jumping

    if (keyConfig.consumeClick()) {
      client.setScreen(generateConfigScreen(client.screen))
    }

    while (keyToggle.consumeClick()) {
      SourcedMod.config.quakeMovementEnabled = !SourcedMod.config.quakeMovementEnabled
      HalfLifeMovement.reset()
      SourcedMod.config.save()
      if (SourcedMod.config.quakeMovementEnabled)
        client.player?.displayClientMessage(Component.translatable("sourced.enabled"), true)
      else
        client.player?.displayClientMessage(Component.translatable("sourced.disabled"), true)
    }

    while (keyCycleMode.consumeClick()) {
      val mode = SourcedMod.config.movementMode.next()
      SourcedMod.config.movementMode = mode
      HalfLifeMovement.reset()
      // Persist immediately. Only YACL's Save button used to write the file, so a mode picked with
      // this key lived in memory alone and every relaunch silently came back as DEFAULT_MOVEMENT_MODE
      // - which quietly invalidates any A/B test run across a rebuild.
      SourcedMod.config.save()
      // Chat, not the action bar: the action bar fades and overlaps the speed indicator, and this
      // is the one message you want to still be able to read after the fact.
      client.player?.displayClientMessage(
        Component.translatable("sourced.mode", Component.translatable(mode.translationKey)), false)
    }
  }

  fun onRenderHud(event: RenderGuiEvent.Post) {
    val mc = Minecraft.getInstance()
    val ctx = event.guiGraphics

    val speed = SourcedPlayer.currentSpeed * 20
    val speedDifference = speed - (SourcedPlayer.previousSpeed * 20)

    if (!SourcedMod.config.speedDeltaIndicatorEnabled || SourcedPlayer.swimming) {
      indicatorVisible = false
      return
    }

    // Hysteresis on the threshold. Without it the indicator blinks on and off every frame while
    // your speed sits on the boundary, which is most of the flicker.
    val threshold = SourcedMod.config.speedDeltaThreshold
    indicatorVisible = when {
      speed >= threshold -> true
      speed < threshold * 0.9 -> false
      else -> indicatorVisible
    }
    if (!indicatorVisible || !SourcedPlayer.jumping) return

    val posX = mc.window.guiScaledWidth / 2.0f
    val posY = mc.window.guiScaledHeight / 2.0f
    val text = "%.2f".format(speed)

    val centerOffset = mc.font.width(text) / 2.0f

    // A dead band on the colour. Bunnyhop speed wobbles a few hundredths every tick, and colouring
    // that wobble made the readout strobe green/red twenty times a second. Only a change you could
    // actually act on gets a colour.
    val color = when {
      speedDifference > COLOUR_DEAD_BAND -> SourcedMod.config.speedGainColor
      speedDifference < -COLOUR_DEAD_BAND -> SourcedMod.config.speedLossColor
      else -> SourcedMod.config.speedUnchangedColor
    }

    ctx.drawString(mc.font, text, (posX - centerOffset).roundToInt(), (posY + 15).roundToInt(), color)
    if (SourcedMod.config.speedDiffIndicatorEnabled) {
      val differenceText = "%.2f".format(speedDifference)
      ctx.drawString(mc.font, differenceText, (posX - centerOffset).roundToInt(), (posY + 25).roundToInt(), color)
    }
  }
}
