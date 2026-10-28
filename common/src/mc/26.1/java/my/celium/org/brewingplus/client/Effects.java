package my.celium.org.brewingplus.client;

import net.minecraft.world.effect.MobEffect;

/** Mob effect calls that differ between Minecraft lines; the 26.1 copy (one per line in common/src/mc). */
final class Effects {
    private Effects() {
    }

    // Spelled isInstantenous before 26.2.
    static boolean isInstantaneous(MobEffect effect) {
        return effect.isInstantenous();
    }
}
