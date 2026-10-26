package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetDirectoryImportRequest;
import games.pixscape.studio.service.asset.TilesetReimportService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class TilesetImportAtlasTransactionTest {
    private static FileHandle project() throws Exception {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter(){},new HeadlessApplicationConfiguration());
        return new FileHandle(Files.createTempDirectory("tileset-atlas-transaction").toFile());
    }
    @Test public void laterScenePackingFailureRestoresSourcesCatalogInputsAndWholePageSets() throws Exception {
        FileHandle project=project(), source=project.child("external/terrain"), tiles=project.child("orig/tiles");
        png(source.child("a.png"),0xff0000ff);
        AssetMetaDatabase db=new AssetMetaDatabase(); var importer=new TilesetReimportService(db);
        var req=new TilesetDirectoryImportRequest(source,tiles);
        int set=importer.apply(importer.prepareDirectory(req,-1,ids->Map.of()),
                AtlasStudioService::validateTilesetImportCandidate,()->db.save(project.child("assets.json"))).tilesetAssetId();
        String color=db.assetAt(1).sourceRelPath(); byte[] original=project.child(color).readBytes();
        Set<String> tags=new LinkedHashSet<>(List.of("scene1","scene2"));
        for(String tag:tags) {
            project.child("atlases/"+tag+".atlas").writeString("old descriptor",false);
            project.child("atlases/"+tag+".png").writeBytes(new byte[]{1,2},false);
            project.child("atlases/"+tag+"-2.png").writeBytes(new byte[]{3,4},false);
            project.child("atlases/input/"+tag+"/old.png").writeBytes(new byte[]{5,6},false);
            project.child("atlases/input/"+tag+"/.repack-required").writeString("",false);
        }
        png(source.child("a.png"),0x00ff00ff);
        assertThrows(IllegalStateException.class,()->importer.apply(importer.prepareDirectory(req,set,ids->Map.of()),
                AtlasStudioService::validateTilesetImportCandidate,()->AtlasStudioService.repackImportedScenes(
                        new ProjectConfig(),project,tags,
                        ()->{ for(String tag:tags) project.child("atlases/input/"+tag+"/old.png").writeBytes(new byte[]{9},false); },
                        ()->fail("No resources may be accepted before every pack succeeds"),
                        (cfg,tag,root,output)->{
                            output.child(tag+".atlas").writeString("new descriptor",false);
                            output.child(tag+".png").writeBytes(new byte[]{7},false);
                            assertEquals("old descriptor",project.child("atlases/scene1.atlas").readString());
                            if(tag.equals("scene2")) throw new IllegalStateException("Injected second pack failure");
                            return null;
                        })));
        assertArrayEquals(original,project.child(color).readBytes());
        assertEquals(db.toSnapshotJson(),AssetMetaDatabase.load(project.child("assets.json")).toSnapshotJson());
        for(String tag:tags) {
            assertEquals("old descriptor",project.child("atlases/"+tag+".atlas").readString());
            assertArrayEquals(new byte[]{3,4},project.child("atlases/"+tag+"-2.png").readBytes());
            assertArrayEquals(new byte[]{5,6},project.child("atlases/input/"+tag+"/old.png").readBytes());
            assertTrue(project.child("atlases/input/"+tag+"/.repack-required").exists());
        }
    }
    @Test public void failedResourceAcceptanceRollsBackPublishedPageSetAndInputs() throws Exception {
        FileHandle project=project();
        project.child("atlases/scene1.atlas").writeString("old",false);
        project.child("atlases/scene1-2.png").writeBytes(new byte[]{1},false);
        assertThrows(IllegalStateException.class,()->AtlasStudioService.repackImportedScenes(new ProjectConfig(),project,Set.of("scene1"),
                ()->project.child("atlases/input/scene1/new.png").writeBytes(new byte[]{2},false),
                ()->{assertEquals("new",project.child("atlases/scene1.atlas").readString()); throw new IllegalStateException("Rejected resources");},
                (cfg,tag,root,output)->{output.child(tag+".atlas").writeString("new",false); output.child(tag+".png").writeBytes(new byte[]{3},false); return null;}));
        assertEquals("old",project.child("atlases/scene1.atlas").readString());
        assertTrue(project.child("atlases/scene1-2.png").exists()); assertFalse(project.child("atlases/scene1.png").exists());
        assertFalse(project.child("atlases/input/scene1").exists());
    }
    @Test public void realColorPackingUpdatesPixelsAndExcludesNormalSources() throws Exception {
        FileHandle project=project(); ProjectConfig cfg=new ProjectConfig(); cfg.projectDirectoryPath=project.path();
        String color="orig/tiles/terrain/0__a2.png"; png(project.child(color),0xff0000ff);
        png(project.child("orig/tiles/terrain/normals/2.png"),0x123456ff);
        SceneAtlasInputService inputs=new SceneAtlasInputService();
        Runnable sync=()->inputs.syncSceneAtlasInput(cfg,"scene1",project,Set.of(color));
        AtlasStudioService.repackImportedScenes(cfg,project,Set.of("scene1"),sync,()->{},SceneAtlasLoaderService::packSceneAtlasToDirectory);
        byte[] before=project.child("atlases/scene1.png").readBytes();
        png(project.child(color),0x00ff00ff);
        project.child("atlases/input/scene1/.repack-required").writeString("",false);
        AtlasStudioService.repackImportedScenes(cfg,project,Set.of("scene1"),sync,()->{},SceneAtlasLoaderService::packSceneAtlasToDirectory);
        assertFalse(Arrays.equals(before,project.child("atlases/scene1.png").readBytes()));
        String descriptor=project.child("atlases/scene1.atlas").readString();
        assertTrue(descriptor.contains("0__a2")); assertFalse(descriptor.contains("normals/2"));
        assertFalse(project.child("atlases/input/scene1/.repack-required").exists());
    }
    private static void png(FileHandle file,int color) {
        file.parent().mkdirs(); Pixmap p=new Pixmap(8,8,Pixmap.Format.RGBA8888);
        try {p.setBlending(Pixmap.Blending.None); p.setColor(color); p.fill(); PixmapIO.writePNG(file,p);}
        finally {p.dispose();}
    }
}
