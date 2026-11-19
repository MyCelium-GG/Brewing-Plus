package my.celium.org.brewingplus.client;

import net.minecraft.client.resources.language.I18n;

/** Texts of this mod in the game's language (assets/brewingplus/lang). Client only. */
final class Tr {
    private Tr() {
    }

    static String s(String path, Object... args) {
        return I18n.get("brewingplus." + path, args);
    }

    static String plural(int n, String path) {
        return s(n == 1 ? path + ".one" : path, n);
    }

    /** "12 blocks north": the compass direction from {@code dx}/{@code dz}, or "right here". */
    static String where(double dx, double dz) {
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance < 2) {
            return s("where.here");
        }
        return s("where.blocks", distance, s("dir." + direction(dx, dz)));
    }

    static String direction(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "east" : "west")
                : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "south" : "north")
                : (dz > 0 ? "south" : "north") + (dx > 0 ? "east" : "west");
    }
}
