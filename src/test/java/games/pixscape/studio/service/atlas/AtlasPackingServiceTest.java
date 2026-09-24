package games.pixscape.studio.service.atlas;

import games.pixscape.runtime.hud.HudTextureProfile;
import org.junit.Test;
import static org.junit.Assert.*;

public class AtlasPackingServiceTest {
    @Test public void effectiveHudSettingsKeepExistingPackerDefaults() {
        var settings = AtlasPackingService.hudSettings(HudTextureProfile.forId(HudTextureProfile.DEFAULT_ID));
        assertEquals(2048, settings.maxWidth);
        assertEquals(2048, settings.maxHeight);
        assertEquals(2048, settings.minWidth);
        assertEquals(2048, settings.minHeight);
        assertEquals(2, settings.paddingX);
        assertEquals(2, settings.paddingY);
        assertTrue(settings.duplicatePadding);
        assertTrue(settings.edgePadding);
        assertFalse(settings.rotation);
        assertFalse(settings.stripWhitespaceX);
        assertFalse(settings.stripWhitespaceY);
        assertTrue(settings.useIndexes);
        assertTrue(settings.alias);
        assertTrue(settings.combineSubdirectories);
        assertFalse(settings.premultiplyAlpha);
        assertTrue(settings.bleed);
        assertTrue(settings.ignoreBlankImages);
    }
}
