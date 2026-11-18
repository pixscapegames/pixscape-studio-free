package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;

import static org.junit.Assert.*;

public class SkinSourcePackingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sourceNinePatchesRegenerateNativeHorizontalScrollbarDimensions() throws Exception {
        var input = temporary.newFolder("input").toPath();
        var output = temporary.newFolder("output");
        for (String name : new String[]{"scroll-horizontal.9.png", "scroll-knob-horizontal.9.png",
                "tiled-rectangle.png", "tiled-polygon.png", "footprint.png"}) {
            Files.copy(java.nio.file.Path.of("assets/ui/scpm/pixscape_data", name), input.resolve(name));
        }
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.silent = true;
        TexturePacker.process(settings, input.toString(), output.getPath(), "skin");
        var atlas = new TextureAtlas.TextureAtlasData(new FileHandle(new java.io.File(output, "skin.atlas")),
                new FileHandle(output), false);
        for (String name : new String[]{"scroll-horizontal", "scroll-knob-horizontal"}) {
            var region = region(atlas, name);
            assertNotNull(name, region);
            assertEquals(17, region.height);
            assertArrayEquals(name, new int[]{2, 2, 0, 16}, region.findValue("split"));
            assertArrayEquals(name, name.equals("scroll-horizontal")
                    ? new int[]{2, 2, 2, 2} : new int[]{2, 2, 6, 6}, region.findValue("pad"));
        }
        assertNotNull(region(atlas, "tiled-rectangle"));
        assertNotNull(region(atlas, "tiled-polygon"));
        assertNotNull(region(atlas, "footprint"));
    }
    private static TextureAtlas.TextureAtlasData.Region region(TextureAtlas.TextureAtlasData atlas, String name) {
        for (var region : atlas.getRegions()) {
            if (name.equals(region.name)) {
                return region;
            }
        }
        return null;
    }

}
