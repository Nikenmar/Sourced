package cmrstudio.sourced

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.LoggerFactory

/**
 * Mod-wide state. Kept as a Kotlin `object` so that `lateinit var config` compiles down to a
 * public static field - the mixins in [cmrstudio.sourced.mixin] read `SourcedMod.config`
 * directly from Java.
 */
object SourcedMod {
  const val ID = "sourced"
  const val NAME = "Sourced"

  val logger = LoggerFactory.getLogger(ID)

  lateinit var config: SourcedConfig
}

/** Common (both sides) entrypoint. */
@Mod(SourcedMod.ID)
class SourcedModEntry(modBus: IEventBus, container: ModContainer) {
  init {
    SourcedMod.config = SourcedConfig.load(FMLPaths.CONFIGDIR.get().resolve("${SourcedMod.ID}.json"))

    SourcedStats.register(modBus)

    val version = container.modInfo.version.toString()
    modBus.addListener(RegisterPayloadHandlersEvent::class.java) { VersionCheck.register(it, version) }

    SourcedMod.logger.info("Sourced initialized!")
  }
}
