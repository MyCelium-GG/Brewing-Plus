package my.celium.org.brewingplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import my.celium.org.Mycel;
import my.celium.org.brewingplus.client.GuideButton;
import my.celium.org.testing.MycelTesting;

/** The mod boots on Mycel with a tiny, well-defaulted config. */
class BrewingPlusModTest {
    @BeforeAll
    static void initOnce(@TempDir Path tempDir) {
        MycelTesting.useConfigDir(tempDir);
        BrewingPlusMod.init();
    }

    @Test
    void registersWithMycel() {
        assertTrue(Mycel.getRegisteredMods().contains(BrewingPlusMod.META));
        assertEquals("brewingplus", BrewingPlusMod.META.id());
    }

    @Test
    void configDefaults() {
        assertTrue(BrewingPlusMod.enabled.get());
        assertTrue(BrewingPlusMod.showButton.get());
        assertTrue(BrewingPlusMod.showStandHint.get());
        assertFalse(BrewingPlusMod.debug.get());
    }

    @Test
    void enableToggleFlowsThrough() {
        assertTrue(BrewingPlusMod.isModEnabled());
        Mycel.setEnabled(BrewingPlusMod.ID, false);
        try {
            assertFalse(BrewingPlusMod.isModEnabled());
        } finally {
            Mycel.setEnabled(BrewingPlusMod.ID, true);
        }
        assertTrue(BrewingPlusMod.isModEnabled());
    }

    @Test
    void hooksTolerateNulls() {
        // Loader screen hooks must never crash the frame.
        assertNull(GuideButton.create(null));
    }
}
