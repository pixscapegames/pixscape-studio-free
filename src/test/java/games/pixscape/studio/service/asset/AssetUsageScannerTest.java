package games.pixscape.studio.service.asset;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssetUsageScannerTest {

    @Test
    public void scanAssetFindsNonActiveAnimationInCurrentScene() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta active = db.registerIfAbsent(
                AssetType.ANIMATION, "animations/idle", "orig/animations/idle",
                AssetMeta.AssetScope.USER);
        AssetMeta nonActive = db.registerIfAbsent(
                AssetType.ANIMATION, "animations/run", "orig/animations/run",
                AssetMeta.AssetScope.USER);
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(active.id());
        animation.animationAssetIds.add(nonActive.id());
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = active.id();

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(world, cfg, db).scanAsset(nonActive.id());

        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.referencedInCurrentLoadedScene());
    }

    @Test
    public void scanAssetFindsAnimationUsageInInactiveSceneFile() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta animation = db.registerIfAbsent(
                AssetType.ANIMATION,
                StudioFs.PREFIX_ANIMATIONS + "Attack 1",
                StudioFs.DIR_ORIG_ANIMATIONS + "/Attack 1__a1",
                AssetMeta.AssetScope.USER
        );

        writeSceneWithAnimationAssetIds(cfg.getSceneMeta("Other"), cfg, animation.id());

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(new World(new WorldConfiguration()), cfg, db)
                        .scanAsset(animation.id());

        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.sceneNames().contains("Other", false));
    }

    @Test
    public void scanAssetFindsParticleUsageWithRuntimeRelativePathInInactiveSceneFile() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta particle = db.registerIfAbsent(
                AssetType.PARTICLE,
                StudioFs.PREFIX_EFFECTS + "fire",
                StudioFs.DIR_ORIG_EFFECTS + "/fire.p",
                AssetMeta.AssetScope.USER
        );

        writeSceneWithParticle(cfg.getSceneMeta("Other"), cfg, "fire.p");

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(new World(new WorldConfiguration()), cfg, db)
                        .scanAsset(particle.id());

        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.sceneNames().contains("Other", false));
    }

    @Test
    public void scanAssetFindsTileUsageInCurrentLoadedScene() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta tile = db.registerIfAbsent(
                AssetType.TILE,
                StudioFs.PREFIX_TILES + "ground/ground_0_0",
                StudioFs.DIR_ORIG_TILES + "/ground/ground_0_0.png",
                AssetMeta.AssetScope.USER
        );

        World world = new World(new WorldConfiguration().setSystem(new BaseSystem() {
            @Override
            protected void processSystem() {
            }
        }));
        int layerId = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(4, 4, 16, 16, 8, SceneMetaRuntime.TiledProjection.ORTHO);
        tiled.data.setTile(1, 1, tile.id());
        tiled.tileAssetIds.add(tile.id());
        world.process();

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(world, cfg, db).scanAsset(tile.id());

        assertTrue(report.used());
        assertTrue(report.occurrenceCount() >= 1);
        assertTrue(report.sceneNames().contains("Main", false));
    }

    private static ProjectConfig projectConfig() throws Exception {
        Path dir = Files.createTempDirectory("pixscape-asset-usage-scanner");
        Files.createDirectories(dir.resolve(StudioFs.DIR_SCENES));

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Asset Usage Scanner";
        cfg.projectFileName = "asset-usage-scanner";
        cfg.projectDirectoryPath = dir.toString();
        cfg.exportRootPathDir = dir.resolve("runtime").toString();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Other");
        cfg.setCurrentSceneByName("Main");
        return cfg;
    }

    private static void writeSceneWithAssetRef(SceneMeta scene, ProjectConfig cfg, int assetId) {
        FileHandle sceneFile = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile())
                .child(StudioFs.DIR_SCENES)
                .child(scene.getFile());
        sceneFile.writeString(
                """
                        {
                          "entities": {
                            "1": {
                              "components": {
                                "AssetRefComponent": {
                                  "assetId": %d
                                }
                              }
                            }
                          }
                        }
                        """.formatted(assetId),
                false,
                "UTF-8"
        );
    }

    private static void writeSceneWithAnimationAssetIds(SceneMeta scene,
                                                        ProjectConfig cfg,
                                                        int assetId) {
        FileHandle sceneFile = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile())
                .child(StudioFs.DIR_SCENES)
                .child(scene.getFile());
        sceneFile.writeString(
                """
                        {
                          "entities": {
                            "1": {
                              "components": {
                                "AnimationComponent": {
                                  "animationAssetIds": [%d]
                                }
                              }
                            }
                          }
                        }
                        """.formatted(assetId),
                false,
                "UTF-8"
        );
    }

    private static void writeSceneWithParticle(SceneMeta scene, ProjectConfig cfg, String effectPath) {
        FileHandle sceneFile = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile())
                .child(StudioFs.DIR_SCENES)
                .child(scene.getFile());
        sceneFile.writeString(
                """
                        {
                          "entities": {
                            "1": {
                              "components": {
                                "ParticleEmitterComponent": {
                                  "effectPath": "%s"
                                }
                              }
                            }
                          }
                        }
                        """.formatted(effectPath),
                false,
                "UTF-8"
        );
    }
}
