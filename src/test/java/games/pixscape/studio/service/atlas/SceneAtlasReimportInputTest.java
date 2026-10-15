package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;
import java.nio.file.Files;
import java.util.Set;
import static org.junit.Assert.*;

public class SceneAtlasReimportInputTest {
    @Test public void sameSizeAndTimestampReplacementRefreshesInputAndKeepsRepackMarker() throws Exception {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter(){},new HeadlessApplicationConfiguration());
        FileHandle project=new FileHandle(Files.createTempDirectory("atlas-reimport-input").toFile());
        String relative="orig/tiles/terrain/0__a2.png";
        FileHandle source=project.child(relative); source.writeBytes(new byte[]{1,2,3,4},false);
        assertTrue(source.file().setLastModified(1000));
        ProjectConfig cfg=new ProjectConfig(); cfg.projectDirectoryPath=project.path();
        SceneAtlasInputService service=new SceneAtlasInputService();
        service.syncSceneAtlasInput(cfg,"scene1",project,Set.of(relative));
        FileHandle input=project.child(StudioFs.DIR_ATLASES).child(StudioFs.DIR_INPUT).child("scene1");
        FileHandle marker=input.child(".repack-required"); marker.writeString("",false);
        source.writeBytes(new byte[]{5,6,7,8},false); assertTrue(source.file().setLastModified(1000));
        assertTrue(service.syncSceneAtlasInput(cfg,"scene1",project,Set.of(relative)).changed());
        assertArrayEquals(source.readBytes(),input.child(source.name()).readBytes());
        assertTrue(marker.exists());
        assertFalse(service.syncSceneAtlasInput(cfg,"scene1",project,Set.of(relative)).changed());
    }
}
