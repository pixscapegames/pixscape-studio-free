package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.ui.asset.ImportDialog;
import org.junit.Test;

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
