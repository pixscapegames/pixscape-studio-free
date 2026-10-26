package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class RuntimeExportConeInclinationTest {
    @Test public void sceneAndRuntimeExportKeepContinuousInclinationsAndMissingFieldIsHorizontal() throws Exception {
        Path studio=Files.createTempDirectory("cone-inclination-studio"),user=Files.createTempDirectory("cone-inclination-export");
        Files.createDirectories(studio.resolve(StudioFs.DIR_SCENES));
        World authored=newWorld(),loaded=newWorld();
        try {
            SaveFileFormat request=new SaveFileFormat();
            for(float pitch:new float[]{0,-37.125f,-90,90}){
                int e=authored.create();ConeLightComponent cone=authored.getMapper(ConeLightComponent.class).create(e);
                cone.inclinationDeg=pitch;cone.height=200;request.entities.add(e);
            }
            authored.process();Path scene=studio.resolve(StudioFs.DIR_SCENES).resolve("scene1.json");
            try(OutputStream out=Files.newOutputStream(scene)){authored.getSystem(WorldSerializationManager.class).save(out,request);}
            try(InputStream in=Files.newInputStream(scene)){
                SaveFileFormat restored=loaded.getSystem(WorldSerializationManager.class).load(in,SaveFileFormat.class);
                assertValues(loaded,restored);
            }
            new AssetMetaDatabase().save(new FileHandle(studio.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));
            ProjectConfig config=new ProjectConfig();config.projectTitle="Cone Inclination";config.projectFileName="cone-inclination";
            config.exportRootPathDir=user.toString();config.createSceneMeta("Main");
            RuntimeExport.exportRuntime(config,new FileHandle(studio.toFile()),new FileHandle(user.toFile()));
            World runtime=newWorld();try(InputStream in=Files.newInputStream(user.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("scenes/scene1.json"))){
                assertValues(runtime,runtime.getSystem(WorldSerializationManager.class).load(in,SaveFileFormat.class));
            }finally{runtime.dispose();}
            assertEquals(0,new Json().fromJson(ConeLightComponent.class,"{}").inclinationDeg,0);
        }finally{authored.dispose();loaded.dispose();}
    }
    private static void assertValues(World world,SaveFileFormat loaded){
        assertEquals(4,loaded.entities.size());float[] pitches={0,-37.125f,-90,90};
        for(int i=0;i<pitches.length;i++){
            ConeLightComponent c=world.getMapper(ConeLightComponent.class).get(loaded.entities.get(i));
            assertEquals(pitches[i],c.inclinationDeg,0);assertEquals(200,c.height,0);
        }
    }
    private static World newWorld(){
        World w=new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
        w.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(w));return w;
    }
}
