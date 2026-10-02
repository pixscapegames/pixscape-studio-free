package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.runtime.hud.HudTextureProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class AtlasPackingServiceTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void newlyAddedCarMustBePackedBeforeAtlasCanBeReused() throws Exception {
        FileHandle input = new FileHandle(temporaryFolder.newFolder("input"));
        FileHandle stale = new FileHandle(temporaryFolder.newFolder("stale"));
        FileHandle fresh = new FileHandle(temporaryFolder.newFolder("fresh"));
        writeSolidPng(input.child("driver__a1698.png"), 0xff00ff00);
        writeSolidPng(input.child("wheel__a1699.png"), 0xffff0000);
        writeSolidPng(input.child("idle__a1302_0000.png"), 0xff00ffff);
        writeSolidPng(input.child("idle__a1302_0001.png"), 0xffffff00);
        writeSolidPng(input.child("__pixscape_internal__/__ps_internal_white_px.png"), 0xffffffff);
        writeSolidPng(input.child("transparent__a1700.png"), 0x00000000);
        AtlasPackingService.packScene(input, stale, "scene1");
        assertTrue(SceneAtlasCoverage.coversInput(input, stale.child("scene1.atlas")));

        writeSolidPng(input.child("car__a1697.png"), 0xff267bd9);
        assertFalse(SceneAtlasCoverage.coversInput(input, stale.child("scene1.atlas")));
        AtlasPackingService.packScene(input, fresh, "scene1");
        FileHandle descriptor = fresh.child("scene1.atlas");
        assertTrue(SceneAtlasCoverage.coversInput(input, descriptor));

        TextureAtlasData data = new TextureAtlasData(descriptor, fresh, false);
        TextureAtlasData.Region car = null;
        for (TextureAtlasData.Region region : data.getRegions()) {
            if ("car__a1697".equals(region.name)) car = region;
        }
        assertNotNull(car);
        BufferedImage page = ImageIO.read(car.page.textureFile.file());
        assertEquals(0xff267bd9, page.getRGB(car.left + 4, car.top + 4));
    }

    @Test public void mutationInsidePackBoundaryRejectsObsoleteOutput() throws Exception {
        FileHandle input = new FileHandle(temporaryFolder.newFolder("changing"));
        FileHandle obsolete = new FileHandle(temporaryFolder.newFolder("obsolete"));
        FileHandle current = new FileHandle(temporaryFolder.newFolder("current"));
        writeSolidPng(input.child("driver.png"), 0xff00ff00);
        try {
            SceneAtlasLoaderService.packWithInputSnapshot(input, obsolete.child("scene.atlas"),
                    "scene", () -> {
                        AtlasPackingService.packScene(input, obsolete, "scene");
                        try {
                            writeSolidPng(input.child("car.png"), 0xff267bd9);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    });
            fail("A pack cannot publish an atlas for changed inputs");
        } catch (SceneAtlasLoaderService.InputsChangedDuringPackException expected) {
            assertTrue(expected.getMessage().contains("scene"));
        }
        assertFalse(SceneAtlasCoverage.coversInput(input, obsolete.child("scene.atlas")));
        SceneAtlasLoaderService.packWithInputSnapshot(input, current.child("scene.atlas"),
                "scene", () -> AtlasPackingService.packScene(input, current, "scene"));
        assertTrue(SceneAtlasCoverage.coversInput(input, current.child("scene.atlas")));
    }

    private static void writeSolidPng(FileHandle output, int argb) throws Exception {
        output.parent().mkdirs();
        BufferedImage image = new BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, argb);
        }
        ImageIO.write(image, "png", output.file());
    }
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
