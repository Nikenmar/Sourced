package cmrstudio.sourced

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.stats.StatFormatter
import net.minecraft.stats.Stats
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.registries.DeferredRegister

object SourcedStats {
  val BHOP_ONE_CM: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SourcedMod.ID, "bhop_one_cm")
  val SHARK_ONE_CM: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SourcedMod.ID, "shark_one_cm")
  val TRIMPS: ResourceLocation = ResourceLocation.fromNamespaceAndPath(SourcedMod.ID, "trimps")

  private val CUSTOM_STATS: DeferredRegister<ResourceLocation> =
    DeferredRegister.create(Registries.CUSTOM_STAT, SourcedMod.ID)

  init {
    // The registry entry *is* the identifier, same as Registry.register(CUSTOM_STAT, id, key).
    CUSTOM_STATS.register("bhop_one_cm") { _ -> BHOP_ONE_CM }
    CUSTOM_STATS.register("shark_one_cm") { _ -> SHARK_ONE_CM }
    CUSTOM_STATS.register("trimps") { _ -> TRIMPS }
  }

  fun register(modBus: IEventBus) {
    CUSTOM_STATS.register(modBus)
    modBus.addListener(FMLCommonSetupEvent::class.java) { event ->
      // Stats.CUSTOM is not thread safe and the entries must already be in the registry,
      // so the formatters are attached on the main thread after registration has run.
      event.enqueueWork {
        Stats.CUSTOM.get(BHOP_ONE_CM, StatFormatter.DISTANCE)
        Stats.CUSTOM.get(SHARK_ONE_CM, StatFormatter.DISTANCE)
        Stats.CUSTOM.get(TRIMPS, StatFormatter.DEFAULT)
      }
    }
  }
}
