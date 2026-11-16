package my.celium.org.brewingplus.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a brewing stand's time left and fuel (server side), for the stand card and the ready message. */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccessor {
    @Accessor("brewTime")
    int brewingplus$brewTime();

    @Accessor("fuel")
    int brewingplus$fuel();
}
