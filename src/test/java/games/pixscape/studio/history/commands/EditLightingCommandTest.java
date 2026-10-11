package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.lighting.LocalSurfaceDescription;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EditLightingCommandTest {
    @Test public void optionalSpritePlacementUndoRestoresAbsenceAndRedoValues(){
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(16);
        int e=world.create();history.historyIds().ensureForEntity(e);
        history.execute(new EditLightingCommand(world,history.historyIds(),e,EditLightingCommand.Property.CAST,1));
        for(int i=0;i<3;i++){
            Assert.assertTrue(world.getMapper(SurfaceLightingComponent.class).get(e).shadowCaster);
            history.undo();Assert.assertFalse(world.getMapper(SurfaceLightingComponent.class).has(e));
            history.redo();
        }
        world.dispose();
    }
    @Test public void sourceQualityAndDrawnPlaneRoundTripThroughHistoryAndJson(){
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(16);
        int e=world.create();history.historyIds().ensureForEntity(e);
        PointLightComponent light=world.getMapper(PointLightComponent.class).create(e);
        history.execute(new EditLightingCommand(world,history.historyIds(),e,EditLightingCommand.Property.HEIGHT,224));
        history.execute(new EditLightingCommand(world,history.historyIds(),e,EditLightingCommand.Property.QUALITY,2));
        history.undo();Assert.assertEquals(1,light.shadowQuality);history.redo();
        Json json=new Json();PointLightComponent loaded=json.fromJson(PointLightComponent.class,json.toJson(light));
        Assert.assertEquals(224,loaded.height,0);Assert.assertEquals(2,loaded.shadowQuality);
        int map=world.create();history.historyIds().ensureForEntity(map);
        TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(map);
        tiled.projection=games.pixscape.runtime.tiled.TiledProjection.ISO;
        tiled.tileWidth=32;tiled.tileHeight=16;tiled.mapWidthCells=tiled.mapHeightCells=4;tiled.chunkSize=4;
        tiled.data=tiled.createMapData();world.process();
        history.execute(new EditTiledLayerSpatialDefaultsCommand(world,history.historyIds(),map,
                new EditTiledLayerSpatialDefaultsCommand.Snapshot(0,0),
                new EditTiledLayerSpatialDefaultsCommand.Snapshot(128,0)));
        history.undo();Assert.assertEquals(0,tiled.defaultTileAltitude,0);history.redo();
        Assert.assertEquals(128,json.fromJson(TiledLayerComponent.class,json.toJson(tiled)).defaultTileAltitude,0);
        world.dispose();
    }
    @Test public void localDescriptionsSurvivePersistenceWithIndependentCopies(){
        SurfaceLightingComponent source=new SurfaceLightingComponent();source.shadowCaster=true;source.anchorV=.25f;
        LocalSurfaceDescription d=source.description=new LocalSurfaceDescription();
        d.xyz=new float[]{0,0,0,1,0,0,0,1,0};d.uv=new float[]{0,0,1,0,0,1};
        d.normals=new float[]{0,0,1,0,0,1,0,0,1};d.triangles=new int[]{0,1,2};d.shadowCaster=true;
        Json json=new Json();SurfaceLightingComponent restored=json.fromJson(SurfaceLightingComponent.class,json.toJson(source));
        restored.description.validate();Assert.assertTrue(restored.shadowCaster);Assert.assertEquals(.25f,restored.anchorV,0);
        SurfaceLightingComponent copied=source.copy();source.description.xyz[0]=7;Assert.assertEquals(0,copied.description.xyz[0],0);
    }
}
