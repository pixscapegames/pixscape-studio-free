package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TsxTilesetImportParserTest {

    @Test
    public void parsesTileAnimations() throws Exception {
        Path dir = Files.createTempDirectory("tsx-parser-animation");
        FileHandle image = writeFile(dir.resolve("terrain.png"), "fake png");
        FileHandle tsx = writeFile(dir.resolve("terrain.tsx"), """
                <tileset name="terrain" tilewidth="16" tileheight="16" tilecount="20" columns="5">
                  <image source="terrain.png" width="80" height="64"/>
                  <tile id="12">
                    <animation>
                      <frame tileid="13" duration="100"/>
                      <frame tileid="14" duration="150"/>
                    </animation>
                  </tile>
                </tileset>
                """);

        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(tsx);

        assertEquals(image.file().toPath().toAbsolutePath().normalize().toString(),
                descriptor.imageFile().file().toPath().toAbsolutePath().normalize().toString());
        assertEquals(1, descriptor.tileAnimations().size());
        TsxTilesetDescriptor.TileAnimation animation = descriptor.tileAnimations().get(0);
        assertEquals(12, animation.baseLocalTileId());
        assertEquals(2, animation.frames().size());
        assertEquals(13, animation.frames().get(0).localTileId());
        assertEquals(100, animation.frames().get(0).durationMs());
        assertEquals(14, animation.frames().get(1).localTileId());
        assertEquals(150, animation.frames().get(1).durationMs());
    }

    @Test
    public void rejectsInvalidAnimationFrameTileId() throws Exception {
        assertParseFailure("""
                <tileset name="terrain" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                  <image source="terrain.png" width="32" height="16"/>
                  <tile id="0"><animation><frame tileid="4" duration="100"/></animation></tile>
                </tileset>
                """, "Tile animation frame tile id is outside the tileset tile range: 4");
    }

    @Test
    public void rejectsInvalidAnimationFrameDuration() throws Exception {
        assertParseFailure("""
                <tileset name="terrain" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                  <image source="terrain.png" width="32" height="16"/>
                  <tile id="0"><animation><frame tileid="1" duration="0"/></animation></tile>
                </tileset>
                """, "Tile animation frame duration must be > 0 ms: tile 0, frame 0");
    }

    @Test
    public void rejectsEmptyAnimation() throws Exception {
        assertParseFailure("""
                <tileset name="terrain" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                  <image source="terrain.png" width="32" height="16"/>
                  <tile id="0"><animation/></tile>
                </tileset>
                """, "Tile animation has no frames: tile 0");
    }

    private static void assertParseFailure(String tsxText, String expectedMessage) throws Exception {
        Path dir = Files.createTempDirectory("tsx-parser-invalid-animation");
        writeFile(dir.resolve("terrain.png"), "fake png");
        FileHandle tsx = writeFile(dir.resolve("terrain.tsx"), tsxText);

        try {
            new TsxTilesetImportParser().parse(tsx);
            fail("Expected TSX parser failure");
        } catch (IllegalArgumentException ex) {
            assertEquals(expectedMessage, ex.getMessage());
        }
    }

    private static FileHandle writeFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }
}
