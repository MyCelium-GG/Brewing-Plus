package my.celium.org.brewingplus.neoforge;

import my.celium.org.brewingplus.MissingMycel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge entrypoint. Without Mycel the game still starts: Brewing+ stays off and says so in chat.
 * Everything that needs Mycel lives in {@link NeoForgeSetup}.
 */
@Mod("brewingplus")
public final class BrewingPlusNeoForge {
    public BrewingPlusNeoForge(IEventBus modBus, @SuppressWarnings("unused") ModContainer container) {
        if (!ModList.get().isLoaded("mycel")) {
            MissingMycel.logOnce();
            NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof ServerPlayer player) {
                    MissingMycel.tell(player);
                }
            });
            if (FMLEnvironment.getDist().isClient()) {
                ClientMissingMycel.register();
            }
            return;
        }
        NeoForgeSetup.start();
    }
}
