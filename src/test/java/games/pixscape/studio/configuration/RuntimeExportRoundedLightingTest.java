package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.lighting.RoundedWallDescription;
import games.pixscape.runtime.render.lighting.TileSurfaceDescription;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuntimeExportRoundedLightingTest {
    @Test public void cleanExportAndReloadPreserveExplicitRoundedProfile()throws Exception{
        Path studio=Files.createTempDirectory("rounded-export-studio"),user=Files.createTempDirectory("rounded-export-user");Files.createDirectories(studio.resolve(StudioFs.DIR_SCENES));
        World authored=world(),runtime=world();try{
            int e=authored.create();TiledLayerComponent t=authored.getMapper(TiledLayerComponent.class).create(e);
            TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=42;d.rounded=new RoundedWallDescription();d.rounded.radiusCells=.75f;t.lightingDescriptions.add(d);authored.process();
            SaveFileFormat request=new SaveFileFormat();request.entities.add(e);
            try(var output=Files.newOutputStream(studio.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"))){authored.getSystem(WorldSerializationManager.class).save(output,request);}
            new AssetMetaDatabase().save(new FileHandle(studio.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));
            ProjectConfig config=new ProjectConfig();config.projectTitle="Rounded";config.projectFileName="rounded";config.exportRootPathDir=user.toString();config.createSceneMeta("Main");
            RuntimeExport.exportRuntime(config,new FileHandle(studio.toFile()),new FileHandle(user.toFile()));
            try(var input=Files.newInputStream(user.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("scenes/scene1.json"))){
                SaveFileFormat loaded=runtime.getSystem(WorldSerializationManager.class).load(input,SaveFileFormat.class);
                TileSurfaceDescription actual=runtime.getMapper(TiledLayerComponent.class).get(loaded.entities.get(0)).lightingDescriptions.first();
                actual.validate();assertEquals(42,actual.assetId);assertEquals(.75f,actual.rounded.radiusCells,0);assertNull(actual.geometry);
            }
        }finally{authored.dispose();runtime.dispose();}
    }
    private static World world(){World w=new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());w.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(w));return w;}
}
