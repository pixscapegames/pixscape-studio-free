package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.asset.TilesetRenderSize;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetAtlasImportRequest;
import games.pixscape.studio.ui.asset.ImportDialog;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SceneServiceImportTypeResolutionTest {

    @Test
    public void resolveImportType_prefersExplicitSpritesheetTypeOverFileExtensionInference() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("sheet.png"));
        item.type = ImportDialog.ImportType.SPRITESHEET;

        assertEquals(ImportDialog.ImportType.SPRITESHEET, SceneService.resolveImportType(item));
    }

    @Test
    public void resolveImportType_prefersExplicitTilesetTypeOverFileExtensionInference() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;

        assertEquals(ImportDialog.ImportType.TILESET, SceneService.resolveImportType(item));
    }

    @Test
    public void resolveImportType_fallsBackToAutoInferenceWhenTypeIsNull() {
        ImportDialog.ImportItem particle = new ImportDialog.ImportItem(new FileHandle("effect.p"));
        particle.type = null;

        ImportDialog.ImportItem image = new ImportDialog.ImportItem(new FileHandle("image.png"));
        image.type = null;

        assertEquals(ImportDialog.ImportType.PARTICLE_EFFECT, SceneService.resolveImportType(particle));
        assertEquals(ImportDialog.ImportType.IMAGE, SceneService.resolveImportType(image));
    }

    @Test
    public void resolveImportType_fallsBackToTsxTilesetForTsxFiles() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("terrain.tsx"));
        item.type = null;

        assertEquals(ImportDialog.ImportType.TILESET_TSX, SceneService.resolveImportType(item));
    }

    @Test
    public void manualTilesetImportRequest_usesCompactSlicingDefaults() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;

        TilesetAtlasImportRequest request = SceneService.tilesetAtlasImportRequestForManualImport(
                item,
                new FileHandle("orig/tiles")
        );

        assertEquals(32, request.tileWidth());
        assertEquals(32, request.tileHeight());
        assertEquals(0, request.spacing());
        assertEquals(0, request.margin());
        assertEquals(32, request.profileSettings().referenceCellWidth());
        assertEquals(32, request.profileSettings().referenceCellHeight());
        assertEquals(SceneMetaRuntime.TiledProjection.ORTHO, request.profileSettings().projection());
        assertEquals(TilesetAnchor.TOP_CENTER, request.profileSettings().anchor());
        assertEquals(0, request.profileSettings().offsetX());
        assertEquals(0, request.profileSettings().offsetY());
        assertEquals(TilesetRenderSize.NATIVE, request.profileSettings().renderSize());
    }

    @Test
    public void manualTilesetImportRequest_passesSlicingSettings() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;
        item.tileWidth = 16;
        item.tileHeight = 24;
        item.tileSpacing = 3;
        item.tileMargin = 2;
        item.referenceCellWidth = 64;
        item.referenceCellHeight = 48;
        item.projection = SceneMetaRuntime.TiledProjection.ISO;
        item.anchor = TilesetAnchor.BOTTOM_LEFT;
        item.offsetX = -4;
        item.offsetY = 9;

        TilesetAtlasImportRequest request = SceneService.tilesetAtlasImportRequestForManualImport(
                item,
                new FileHandle("orig/tiles")
        );

        assertEquals(16, request.tileWidth());
        assertEquals(24, request.tileHeight());
        assertEquals(3, request.spacing());
        assertEquals(2, request.margin());
        assertEquals(64, request.profileSettings().referenceCellWidth());
        assertEquals(48, request.profileSettings().referenceCellHeight());
        assertEquals(SceneMetaRuntime.TiledProjection.ISO, request.profileSettings().projection());
        assertEquals(TilesetAnchor.BOTTOM_LEFT, request.profileSettings().anchor());
        assertEquals(-4, request.profileSettings().offsetX());
        assertEquals(9, request.profileSettings().offsetY());
        assertEquals(TilesetRenderSize.NATIVE, request.profileSettings().renderSize());
    }

    @Test
    public void tsxTilesetImportRequest_usesTsxDescriptorAndResolvedImage() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-tsx-request");
        Path image = dir.resolve("tiles").resolve("terrain.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{1, 2, 3});
        Path tsx = dir.resolve("terrain.tsx");
        Files.writeString(tsx, """
                <tileset name="Terrain TSX" tilewidth="16" tileheight="24" spacing="3" margin="2" tilecount="1" columns="1">
                  <image source="tiles/terrain.png" width="16" height="24"/>
                </tileset>
                """, StandardCharsets.UTF_8);

        TilesetAtlasImportRequest request = SceneService.tilesetAtlasImportRequestForTsxImport(
                new FileHandle(tsx.toFile()),
                new FileHandle(dir.resolve("orig").resolve("tiles").toFile())
        );

        assertEquals(image.toFile().getCanonicalFile(), request.sourceFile().file().getCanonicalFile());
        assertEquals(16, request.tileWidth());
        assertEquals(24, request.tileHeight());
        assertEquals(3, request.spacing());
        assertEquals(2, request.margin());
        assertEquals("Terrain TSX", request.tilesetName());
        assertEquals(16, request.profileSettings().referenceCellWidth());
        assertEquals(24, request.profileSettings().referenceCellHeight());
        assertEquals(SceneMetaRuntime.TiledProjection.ORTHO, request.profileSettings().projection());
        assertEquals(TilesetAnchor.TOP_CENTER, request.profileSettings().anchor());
        assertEquals(TilesetRenderSize.NATIVE, request.profileSettings().renderSize());
    }

    @Test
    public void resolveParticleImage_findsImagePathRelativeToParticleFile() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-particle-image");
        Path image = dir.resolve("images").resolve("particle.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{1, 2, 3});

        FileHandle particleFile = new FileHandle(dir.resolve("effect.p").toFile());

        FileHandle resolved = SceneService.resolveParticleImage(particleFile, "images/particle.png");

        assertNotNull(resolved);
        assertEquals(image.toFile().getCanonicalFile(), resolved.file().getCanonicalFile());
    }
}
