package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

public class GameObjectCompositionExportTest {
    @Test
    public void availableDormantSettingsSurviveAndInvalidOrphansAreNotExported() throws Exception {
        Fixture fixture = new Fixture();
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        GameObjectAsset.GameObjectEntityData root = new GameObjectAsset.GameObjectEntityData();
        root.sourceEntityId = 1;
        root.gameObject = new GameObjectAsset.GameObjectData();
        root.transform = new GameObjectAsset.TransformData();
        root.transform.scaleX = root.transform.scaleY = 1;
        root.spatialRepresentation = new GameObjectAsset.SpatialRepresentationData();
        root.spatialRepresentation.rectangles = new ActorSpatialRectangles();
        ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
        zone.x = .2f;
        zone.y = -.1f;
        zone.width = .7f;
        zone.height = 1.2f;
        zone.pivotX = 13;
        zone.pivotY = -7;
        root.spatialRepresentation.rectangles.staticRectangle = zone;
        asset.entities.add(root);
        FileHandle source = fixture.source.child("gameobjects/dormant.gameobject");
        source.writeString(new GameObjectAssetLoader().toJson(asset), false, "UTF-8");
        fixture.source.child("gameobjects/orphan.gameobject").writeString("invalid orphan", false);
        fixture.config.getCurrentSceneMeta().runtimeAvailability.gameObjectIds.add("gameobjects/dormant.gameobject");
        Map<String, String> before = snapshot(fixture.source);

        fixture.export();

        assertEquals(source.readString(), fixture.output.child("gameobjects/dormant.gameobject").readString());
        assertFalse(fixture.output.child("gameobjects/orphan.gameobject").exists());
        GameObjectAsset loaded = new GameObjectAssetLoader().load(fixture.output.child("gameobjects/dormant.gameobject"));
        assertNull(loaded.entities.first().spatialFootprint);
        assertEquals(.7f, loaded.entities.first().spatialRepresentation.rectangles.staticRectangle.width, 0);
        assertEquals(before, snapshot(fixture.source));

        Map<String, String> published = snapshot(fixture.output);
        fixture.config.getCurrentSceneMeta().runtimeAvailability.gameObjectIds.add("gameobjects/missing.gameobject");
        assertThrows(RuntimeException.class, fixture::export);
        assertEquals(published, snapshot(fixture.output));
        assertEquals(before, snapshot(fixture.source));
    }

    @Test
    public void normalPreparationNeverWritesSourcesAndFailurePreservesPublishedExport() throws Exception {
        Fixture fixture = new Fixture();
        TileAssetMeta tile = (TileAssetMeta) fixture.assets.registerIfAbsent(
                AssetType.TILE, "tiles/wall", "orig/tiles/wall.png", AssetMeta.AssetScope.USER);
        tile.normalSourceRelPath = "orig/tiles/wall-normal.png";
        fixture.saveAssets();
        FileHandle color = fixture.source.child("atlases/input/scene1/wall.png");
        color.parent().mkdirs();
        BufferedImage colorImage = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                colorImage.setRGB(x, y, 0xffff9900);
            }
        }
        ImageIO.write(colorImage, "png", color.file());
        FileHandle normal = fixture.source.child(tile.normalSourceRelPath);
        normal.parent().mkdirs();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", normal.file());
        color.copyTo(fixture.source.child("atlases/scene1.png"));
        fixture.source.child("atlases/scene1.atlas").writeString(
                "scene1.png\nsize:8,8\nformat:RGBA8888\nfilter:Nearest,Nearest\nrepeat:none\n"
                        + "wall__a" + tile.id() + "\nbounds:0,0,8,8\noffsets:0,0,8,8\n", false);
        fixture.source.child("atlases/orphan.atlas").writeString("invalid orphan", false);
        Map<String, String> before = snapshot(fixture.source);

        fixture.export();

        assertFalse(fixture.output.child("atlases/orphan.atlas").exists());
        assertTrue(fixture.output.child("atlases/scene1.normals.atlas").exists());
        assertFalse(fixture.output.child("atlases/input").exists());
        assertEquals(before, snapshot(fixture.source));
        Map<String, String> published = snapshot(fixture.output);
        assertTrue(normal.delete());
        Map<String, String> beforeFailure = snapshot(fixture.source);
        assertThrows(RuntimeException.class, fixture::export);
        assertEquals(beforeFailure, snapshot(fixture.source));
        assertEquals(published, snapshot(fixture.output));
        assertEquals(1, fixture.destination.list().length);
    }

    private static Map<String, String> snapshot(FileHandle directory) throws Exception {
        Map<String, String> result = new TreeMap<>();
        Path base = directory.file().toPath();
        try (var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                result.put(base.relativize(path).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private static final class Fixture {
        final FileHandle source;
        final FileHandle destination;
        final FileHandle output;
        final ProjectConfig config = new ProjectConfig();
        final AssetMetaDatabase assets = new AssetMetaDatabase();

        Fixture() throws Exception {
            source = new FileHandle(Files.createTempDirectory("composition-export-source").toFile());
            destination = new FileHandle(Files.createTempDirectory("composition-export-target").toFile());
            output = destination.child(RuntimeExport.RUNTIME_DIR_NAME);
            config.projectTitle = "Composition export";
            config.projectFileName = "composition-export";
            config.exportRootPathDir = destination.path();
            config.createSceneMeta("Main");
            source.child("scenes/scene1.json").writeString("{}", false);
            saveAssets();
        }

        void saveAssets() {
            assets.save(source.child(StudioFs.FILE_ASSETS_JSON));
        }

        void export() {
            RuntimeExport.exportRuntime(config, source, destination);
        }
    }
}
