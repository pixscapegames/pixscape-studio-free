package games.pixscape.studio.service.runtimeavailability;

import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimeAvailabilityServiceTest {

    @Test
    public void addPrefab_deduplicatesAndListsByCategory() {
        RuntimeAvailabilityService service = new RuntimeAvailabilityService();
        SceneMeta scene = new SceneMeta("Main", "scene1.json");

        assertTrue(service.addPrefab(scene, "enemy_slime"));
        assertFalse(service.addPrefab(scene, "enemy_slime"));

        assertEquals(1, service.listPrefabIds(scene).size());
        assertEquals("enemy_slime", service.listPrefabIds(scene).get(0));
        assertTrue(service.listTiledAnimationIds(scene).isEmpty());
    }

    @Test
    public void removeTiledAnimation_removesOnlyDeclaration() {
        RuntimeAvailabilityService service = new RuntimeAvailabilityService();
        SceneMeta scene = new SceneMeta("Main", "scene1.json");

        assertTrue(service.addTiledAnimation(scene, 42));
        assertTrue(service.containsTiledAnimation(scene, 42));

        assertTrue(service.removeTiledAnimation(scene, 42));
        assertFalse(service.containsTiledAnimation(scene, 42));
        assertFalse(service.removeTiledAnimation(scene, 42));
    }

    @Test
    public void removePrefab_removesOnlyDeclaration() {
        RuntimeAvailabilityService service = new RuntimeAvailabilityService();
        SceneMeta scene = new SceneMeta("Main", "scene1.json");

        assertTrue(service.addPrefab(scene, "enemy_slime"));
        assertTrue(service.containsPrefab(scene, "enemy_slime"));

        assertTrue(service.removePrefab(scene, "enemy_slime"));
        assertFalse(service.containsPrefab(scene, "enemy_slime"));
        assertFalse(service.removePrefab(scene, "enemy_slime"));
    }

    @Test
    public void removeDeletedAsset_removesAnimationFromAllScenes() {
        ProjectConfig cfg = projectConfig();
        RuntimeAvailabilityService service = new RuntimeAvailabilityService();
        AssetMeta animation = new AssetMetaDatabase().registerIfAbsent(
                AssetType.ANIMATION,
                StudioFs.PREFIX_ANIMATIONS + "hero/run",
                StudioFs.DIR_ORIG_ANIMATIONS + "/hero/run",
                AssetMeta.AssetScope.USER
        );

        service.addAnimation(cfg.getSceneMeta("Main"), animation.id);
        service.addAnimation(cfg.getSceneMeta("Other"), animation.id);

        assertTrue(service.removeDeletedAsset(cfg, animation));

        assertFalse(service.containsAnimation(cfg.getSceneMeta("Main"), animation.id));
        assertFalse(service.containsAnimation(cfg.getSceneMeta("Other"), animation.id));
    }

    @Test
    public void removeDeletedAsset_removesParticleUsingRuntimeRelativePath() {
        ProjectConfig cfg = projectConfig();
        RuntimeAvailabilityService service = new RuntimeAvailabilityService();
        AssetMeta particle = new AssetMetaDatabase().registerIfAbsent(
                AssetType.PARTICLE,
                StudioFs.PREFIX_EFFECTS + "fire",
                StudioFs.DIR_ORIG_EFFECTS + "/fire.p",
                AssetMeta.AssetScope.USER
        );

        service.addParticle(cfg.getSceneMeta("Main"), "fire.p");

        assertTrue(service.removeDeletedAsset(cfg, particle));

        assertFalse(service.containsParticle(cfg.getSceneMeta("Main"), "fire.p"));
    }

    @Test
    public void removeDeletedTileset_removesRuntimeAvailableTilesFromAllScenes() {
        ProjectConfig cfg = projectConfig();
        RuntimeAvailabilityService service = new RuntimeAvailabilityService();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta tileset = db.registerIfAbsent(
                AssetType.TILESET,
                StudioFs.PREFIX_TILES + "terrain",
                StudioFs.DIR_ORIG_TILES + "/terrain",
                AssetMeta.AssetScope.USER
        );
        TileAssetMeta grass = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                StudioFs.PREFIX_TILES + "terrain/grass",
                StudioFs.DIR_ORIG_TILES + "/terrain/grass.png",
                AssetMeta.AssetScope.USER
        );
        grass.tilesetId = tileset.id;
        TileAssetMeta rock = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                StudioFs.PREFIX_TILES + "terrain/rock",
                StudioFs.DIR_ORIG_TILES + "/terrain/rock.png",
                AssetMeta.AssetScope.USER
        );
        rock.tilesetId = tileset.id;

        service.addTiledTile(cfg.getSceneMeta("Main"), grass.id);
        service.addTiledTile(cfg.getSceneMeta("Other"), rock.id);

        assertTrue(service.removeDeletedTileset(cfg, db, tileset.id));

        assertFalse(service.listTiledTileAssetIds(cfg.getSceneMeta("Main")).contains(grass.id));
        assertFalse(service.listTiledTileAssetIds(cfg.getSceneMeta("Other")).contains(rock.id));
    }

    private static ProjectConfig projectConfig() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Runtime Availability Service";
        cfg.projectFileName = "runtime-availability-service";
        cfg.projectDirectoryPath = "runtime-availability-service";
        cfg.exportRootPathDir = "runtime";
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Other");
        cfg.setCurrentSceneByName("Main");
        return cfg;
    }
}
