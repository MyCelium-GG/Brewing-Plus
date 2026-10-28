package my.celium.org.brewingplus.client;

import net.minecraft.world.effect.MobEffect;

/** Mob effect calls that differ between Minecraft lines; the 26.3 copy (one per line in common/src/mc). */
final class Effects {
    private Effects() {
    }

    static boolean isInstantaneous(MobEffect effect) {
        return effect.isInstantaneous();
    }
}
