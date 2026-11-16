package my.celium.org.brewingplus.forge;

import my.celium.org.brewingplus.MissingMycel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint. Without Mycel the game still starts: Brewing+ stays off and says so in chat.
 * Everything that needs Mycel lives in {@link ForgeSetup}.
 */
@Mod("brewingplus")
public final class BrewingPlusForge {
    public BrewingPlusForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        if (!ModList.isLoaded("mycel")) {
            MissingMycel.logOnce();
            PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
                if (event.getEntity() instanceof ServerPlayer player) {
                    MissingMycel.tell(player);
                }
            });
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientMissingMycel.register();
            }
            return;
        }
        ForgeSetup.start();
    }
}
