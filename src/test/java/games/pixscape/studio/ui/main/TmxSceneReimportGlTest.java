package games.pixscape.studio.ui.main;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import static org.junit.Assert.*;

public class TmxSceneReimportGlTest {
    @Test
    public void deleteActiveImportedSceneThenImportTheSameCollectionAgain() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(800, (studio, root) -> {
            var project = ResourceAssetInspectorGlTest.project(studio, root.child("project"));
            ResourceAssetInspectorGlTest.open(studio, project);
            var pixels = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            pixels.setColor(0.2f, 0.8f, 0.4f, 1f);
            pixels.fill();
            PixmapIO.writePNG(root.child("tile.png"), pixels);
            pixels.dispose();
            var map = root.child("map.tmx");
            map.writeString("""
                    <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                      <tileset firstgid="1" name="props" tilewidth="16" tileheight="16" tilecount="2" columns="0">
                        <tile id="1"><image source="tile.png" width="16" height="16"/></tile>
                        <tile id="7"><image source="tile.png" width="16" height="16"/>
                          <properties><property name="path" type="file" value="../missing file.txt"/></properties>
                          <animation><frame tileid="1" duration="100"/></animation>
                        </tile>
                      </tileset>
                      <layer name="Ground" width="1" height="1"><data encoding="csv">8</data></layer>
                    </map>
                    """, false);
            var service = studio.getSceneService();
            String catalog = null;
            String animations = null;
            for (int cycle = 0; cycle < 3; cycle++) {
                var imported = service.importTmxAsNewScene(new TmxSceneImportRequest(map, "Imported", true));
                assertTrue(String.valueOf(imported.failure()), imported.imported());
                assertEquals("Imported", ProjectConfig.getInstance().getCurrentSceneName());
                assertEquals(cycle == 0 ? 1 : 0, imported.importedTilesetCount());
                assertEquals(2, imported.importedTileCount());
                assertEquals(1, imported.importedCellCount());
                studio.render();
                studio.render();
                var database = service.getAssetMetaDatabase();
                String currentAnimations = project.parent().child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON).readString();
                if (catalog == null) {
                    catalog = database.toSnapshotJson();
                    animations = currentAnimations;
                } else {
                    assertEquals(catalog, database.toSnapshotJson());
                    assertEquals(animations, currentAnimations);
                }
                service.deleteScene(imported.sceneName());
                studio.render();
                studio.render();
                assertNull(ProjectConfig.getInstance().getSceneMeta("Imported"));
                assertEquals("Scene", ProjectConfig.getInstance().getCurrentSceneName());
                assertFalse(project.parent().child(StudioFs.DIR_SCENES).child(imported.sceneFileName()).exists());
                assertEquals(catalog, database.toSnapshotJson());
            }
        });
    }
}
