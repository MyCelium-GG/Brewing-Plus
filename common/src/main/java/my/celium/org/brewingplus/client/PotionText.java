package my.celium.org.brewingplus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Short labels for bottles. Long and strong variants share their name
 * ("Potion of Swiftness"), so lists add the first effect's level and
 * duration: {@code "II · 1:30"}, {@code "8:00"}. Client only.
 */
final class PotionText {
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private PotionText() {
    }

    static String name(ItemStack stack) {
        return stack.isEmpty() ? "?" : stack.getHoverName().getString();
    }

    /** Level and duration of the first effect, or {@code ""} for effectless bottles (water, awkward). */
    static String variant(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return "";
        }
        float scale = stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
        for (MobEffectInstance effect : contents.getAllEffects()) {
            List<String> parts = new ArrayList<>();
            if (effect.getAmplifier() > 0) {
                parts.add(roman(effect.getAmplifier() + 1));
            }
            if (!Effects.isInstantaneous(effect.getEffect().value())) {
                parts.add(MobEffectUtil.formatDuration(effect, scale, TICKS_PER_SECOND).getString());
            }
            return String.join(" · ", parts);
        }
        return "";
    }

    /** "Potion of Swiftness II · 1:30" style label. */
    static String full(ItemStack stack) {
        String variant = variant(stack);
        return variant.isEmpty() ? name(stack) : name(stack) + " " + variant;
    }

    static String roman(int level) {
        return level >= 1 && level <= NUMERALS.length ? NUMERALS[level - 1] : String.valueOf(level);
    }
}
