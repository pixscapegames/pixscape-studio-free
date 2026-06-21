package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.atlas.AtlasInputSyncResult;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneServiceSaveAtlasRepackSkipTest {

    @Test
    public void saveWithUnchangedInputsAndUsableAtlasSkipsRepack() throws Exception {
        FileHandle studioDir = studioDir();

        assertTrue(SceneService.shouldSkipSaveAtlasRepack(
                studioDir,
                "scene",
                AtlasInputSyncResult.unchanged(),
                true,
                false
        ));
    }

    @Test
    public void saveWithChangedInputsStillRepacks() throws Exception {
        FileHandle studioDir = studioDir();

        assertFalse(SceneService.shouldSkipSaveAtlasRepack(
                studioDir,
                "scene",
                new AtlasInputSyncResult(true, 0, 0),
                true,
                false
        ));
    }

    @Test
    public void saveWithCopiedFilesStillRepacks() throws Exception {
        FileHandle studioDir = studioDir();

        assertFalse(SceneService.shouldSkipSaveAtlasRepack(
                studioDir,
                "scene",
                new AtlasInputSyncResult(false, 1, 0),
                true,
                false
        ));
    }

    @Test
    public void saveWithDeletedFilesStillRepacks() throws Exception {
        FileHandle studioDir = studioDir();

        assertFalse(SceneService.shouldSkipSaveAtlasRepack(
                studioDir,
                "scene",
                new AtlasInputSyncResult(false, 0, 1),
                true,
                false
        ));
    }

    @Test
    public void saveWithUnchangedInputsButMissingAtlasStillRepacks() throws Exception {
        FileHandle studioDir = studioDir();

        assertFalse(SceneService.shouldSkipSaveAtlasRepack(
                studioDir,
                "scene",
                AtlasInputSyncResult.unchanged(),
                false,
                false
        ));
    }

    @Test
    public void pendingPackPreventsNoOpSaveSkipSoExistingPackCanApply() throws Exception {
        FileHandle studioDir = studioDir();

        assertFalse(SceneService.shouldSkipSaveAtlasRepack(
                studioDir,
                "scene",
                AtlasInputSyncResult.unchanged(),
                true,
                true
        ));
    }

    @Test
    public void atlasPageFileNamesParsesAllListedPages() throws Exception {
        FileHandle studioDir = studioDir();
        FileHandle atlasFile = studioDir.child(StudioFs.DIR_ATLASES).child("scene.atlas");
        atlasFile.parent().mkdirs();
        atlasFile.writeString("""
                scene.png
                size: 2048,2048
                format: RGBA8888
                filter: Nearest,Nearest
                repeat: none
                grass
                  rotate: false

                scene-2.png
                size: 2048,2048
                format: RGBA8888
                filter: Nearest,Nearest
                repeat: none
                rock
                  rotate: false
                """, false, "UTF-8");

        Array<String> pages = SceneService.atlasPageFileNames(atlasFile);

        assertEquals(2, pages.size);
        assertTrue(pages.contains("scene.png", false));
        assertTrue(pages.contains("scene-2.png", false));
    }

    private static FileHandle studioDir() throws Exception {
        Path path = Files.createTempDirectory("scene-service-save-atlas-skip");
        return new FileHandle(path.toFile());
    }
}
