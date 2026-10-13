package cmrstudio.sourced

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Keeps a server and its players on the same build of Sourced.
 *
 * NeoForge negotiates every mod network channel when a player connects: a channel both sides have
 * must carry the same version, and a required channel the other side lacks refuses the connection.
 * This channel is versioned with the exact mod version and carries nothing - it exists only to be
 * compared.
 *
 * Required on a dedicated server, so a player without it cannot join. Builds before 1.0.2 have no
 * channel at all, which makes an outdated client indistinguishable from a client without Sourced:
 * both are turned away. Optional on the physical client, so a player can still take the mod to
 * servers that do not run it; that also leaves a singleplayer world opened to LAN unenforced, since
 * its server runs inside the host's client.
 */
object VersionCheck {
  /** Never sent. The handshake compares its channel; the payload itself has nothing to say. */
  object VersionPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<VersionPayload>(ResourceLocation.fromNamespaceAndPath(SourcedMod.ID, "version"))
    val CODEC: StreamCodec<RegistryFriendlyByteBuf, VersionPayload> = StreamCodec.unit(VersionPayload)

    override fun type() = TYPE
  }

  fun register(event: RegisterPayloadHandlersEvent, version: String) {
    val registrar = event.registrar(version)
    (if (FMLEnvironment.dist.isClient) registrar.optional() else registrar)
      .playToClient(VersionPayload.TYPE, VersionPayload.CODEC) { _, _ -> }
  }
}
