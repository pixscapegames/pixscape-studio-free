package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import org.junit.After;
import org.junit.Test;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.binding;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.*;

public class TiledFallbackSystemGateTest {

    @After
    public void clearTextureRegistry() {
        TextureRegistry.clear();
    }

    @Test
    public void completeAtlasRunsOnePassThenSkipsEveryFollowingFrame() {
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(7, "tile__a7", texture(16, 16))
        );
        Fixture fixture = fixture(
                resolver(atlas, null, null),
                null,
                null,
                1,
                1
        );
        fixture.map.setTile(0, 0, 7);

        fixture.world.process();

        assertFalse(fixture.system.isEnabled());
        assertEquals(1, fixture.system.validationPassCount());
        assertEquals(1L, fixture.system.visitedCellCount());
        assertEquals(1, atlas.resolveCalls);

        for (int i = 0; i < 100; i++) {
            fixture.world.process();
        }

        assertEquals(1, fixture.system.validationPassCount());
        assertEquals(1L, fixture.system.visitedCellCount());
        assertEquals(1, atlas.resolveCalls);
        fixture.dispose();
    }

    @Test
    public void emptySceneRunsOnePassThenDisables() {
        TiledFallbackSystem system = new TiledFallbackSystem(
                new TiledMapRenderState(1),
                resolver(
                        new VisualResolverTestSupport.TrackingAtlasService("main"),
                        null,
                        null
                ),
                id -> null,
                null
        );
        World world = new World(new WorldConfiguration().setSystem(system));
        try {
            world.process();

            assertFalse(system.isEnabled());
            assertEquals(1, system.validationPassCount());
            assertEquals(0L, system.visitedCellCount());
        } finally {
            world.dispose();
        }
    }

    @Test
    public void standaloneTileKeepsSystemEnabledAndWritesFallbackSlot() {
        AssetFixture assets = assetFixture(7);
        Texture standalone = texture(24, 32);
        Fixture fixture = fixture(
                resolver(
                        new VisualResolverTestSupport.TrackingAtlasService("main"),
                        assets.database,
                        standalone
                ),
                assets.database,
                null,
                1,
                1
        );
        fixture.map.setTile(0, 0, assets.tileAssetIds[0]);

        fixture.world.process();

        assertTrue(fixture.system.isEnabled());
        int renderRef = fixture.map.tiledRenderRefForTile(0, 0);
        assertTrue(fixture.tiledState.isRenderableRef(renderRef));
        assertEquals(
                TextureRegistry.handleOf(standalone),
                fixture.tiledState.textureHandle[renderRef]
        );
        fixture.dispose();
    }

    @Test
    public void standaloneToAtlasValidationDisablesAfterPublication() {
        AssetFixture assets = assetFixture(7);
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        Fixture fixture = fixture(
                resolver(atlas, assets.database, texture(16, 16)),
                assets.database,
                null,
                1,
                1
        );
        int tileAssetId = assets.tileAssetIds[0];
        fixture.map.setTile(0, 0, tileAssetId);
        fixture.world.process();
        assertTrue(fixture.system.isEnabled());

        atlas.publish(
                new TextureAtlas(),
                binding(
                        tileAssetId,
                        "tile__a" + tileAssetId,
                        texture(16, 16)
                )
        );
        fixture.system.requestValidation();
        fixture.world.process();

        assertFalse(fixture.system.isEnabled());
        assertEquals(2, fixture.system.validationPassCount());
        fixture.dispose();
    }

    @Test
    public void futureStandaloneAnimationFrameKeepsFallbackActive() {
        AssetFixture assets = assetFixture(7, 8);
        int firstFrameAssetId = assets.tileAssetIds[0];
        int secondFrameAssetId = assets.tileAssetIds[1];
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(
                        firstFrameAssetId,
                        "first__a" + firstFrameAssetId,
                        texture(16, 16)
                )
        );
        TileAnimationRegistry animations =
                animation(100, firstFrameAssetId, secondFrameAssetId);
        Fixture fixture = fixture(
                resolver(atlas, assets.database, texture(16, 16)),
                assets.database,
                animations,
                1,
                1
        );
        fixture.map.setTile(0, 0, 100);

        fixture.world.process();

        assertTrue(fixture.system.isEnabled());
        assertEquals(1, fixture.system.animationCertificationCount());
        assertEquals(0, fixture.tiledState.getVisibleRefCount());
        fixture.dispose();
    }

    @Test
    public void allAtlasAnimationFramesDisableFallback() {
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(7, "first__a7", texture(16, 16)),
                binding(8, "second__a8", texture(16, 16))
        );
        Fixture fixture = fixture(
                resolver(atlas, null, null),
                null,
                animation(100, 7, 8),
                1,
                1
        );
        fixture.map.setTile(0, 0, 100);

        fixture.world.process();

        assertFalse(fixture.system.isEnabled());
        assertEquals(1, fixture.system.animationCertificationCount());
        fixture.dispose();
    }

    @Test
    public void currentStandaloneAnimationFrameWritesFallbackAndStaysActive() {
        AssetFixture assets = assetFixture(7, 8);
        int firstFrameAssetId = assets.tileAssetIds[0];
        int secondFrameAssetId = assets.tileAssetIds[1];
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(
                        secondFrameAssetId,
                        "second__a" + secondFrameAssetId,
                        texture(16, 16)
                )
        );
        Fixture fixture = fixture(
                resolver(atlas, assets.database, texture(24, 32)),
                assets.database,
                animation(100, firstFrameAssetId, secondFrameAssetId),
                1,
                1
        );
        fixture.map.setTile(0, 0, 100);

        fixture.world.process();

        assertTrue(fixture.system.isEnabled());
        int renderRef = fixture.map.tiledRenderRefForTile(0, 0);
        assertTrue(fixture.tiledState.isRenderableRef(renderRef));
        fixture.dispose();
    }

    @Test
    public void repeatedAnimationIsCertifiedOncePerPass() {
        int occurrenceCount = 1_000;
        AssetFixture assets = assetFixture(7, 8);
        Fixture fixture = fixture(
                resolver(
                        new VisualResolverTestSupport.TrackingAtlasService("main"),
                        assets.database,
                        texture(16, 16)
                ),
                assets.database,
                animation(
                        100,
                        assets.tileAssetIds[0],
                        assets.tileAssetIds[1]
                ),
                occurrenceCount,
                1
        );
        for (int x = 0; x < occurrenceCount; x++) {
            fixture.map.setTile(x, 0, 100);
        }

        fixture.world.process();

        assertTrue(fixture.system.isEnabled());
        assertEquals(1, fixture.system.animationCertificationCount());
        assertEquals(occurrenceCount, fixture.system.visitedCellCount());
        fixture.dispose();
    }

    @Test
    public void animationCertificationDistinguishesLayerAtlasTags() {
        AssetFixture assets = assetFixture(7, 8);
        TileAnimationRegistry animations = animation(
                100,
                assets.tileAssetIds[0],
                assets.tileAssetIds[1]
        );
        Fixture fixture = fixture(
                resolver(
                        new VisualResolverTestSupport.TrackingAtlasService("main"),
                        assets.database,
                        texture(16, 16)
                ),
                assets.database,
                animations,
                1,
                1
        );
        fixture.map.setTile(0, 0, 100);
        int secondLayerEntity = fixture.world.create();
        LayerComponent secondLayer = fixture.world
                .getMapper(LayerComponent.class)
                .create(secondLayerEntity);
        secondLayer.type = LayerComponent.TYPE_TILED;
        secondLayer.layerIndex = 1;
        int secondMapEntity = fixture.world.create();
        fixture.world.getMapper(EntityIndexComponent.class)
                .create(secondMapEntity).layerIndex = 1;
        TiledLayerComponent secondTiled = fixture.world
                .getMapper(TiledLayerComponent.class)
                .create(secondMapEntity);
        secondTiled.atlasTag = "secondary";
        secondTiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
        secondTiled.data.setTile(0, 0, 100);

        fixture.world.process();

        assertEquals(2, fixture.system.animationCertificationCount());
        fixture.dispose();
    }

    private static Fixture fixture(StudioAssetVisualResolver resolver,
                                   AssetMetaDatabase database,
                                   TileAnimationRegistry animations,
                                   int width,
                                   int height) {
        TiledMapRenderState tiledState = new TiledMapRenderState(1);
        TiledFallbackSystem system = new TiledFallbackSystem(
                tiledState,
                resolver,
                database != null ? database::findById : id -> null,
                animations
        );
        World world = new World(new WorldConfiguration().setSystem(system));
        int entityId = world.create();
        LayerComponent layer =
                world.getMapper(LayerComponent.class).create(entityId);
        layer.type = LayerComponent.TYPE_TILED;
        int mapEntityId = world.create();
        world.getMapper(EntityIndexComponent.class).create(mapEntityId).layerIndex = 0;
        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class).create(mapEntityId);
        tiled.atlasTag = "main";
        tiled.data = new TiledMapLayerData(width, height, 16, 16, 32);
        return new Fixture(world, system, tiledState, tiled.data);
    }

    private static StudioAssetVisualResolver resolver(
            VisualResolverTestSupport.TrackingAtlasService atlas,
            AssetMetaDatabase database,
            Texture standaloneTexture) {
        return new StudioAssetVisualResolver(
                atlas,
                database != null ? database::findById : id -> null,
                new StudioAssetVisualResolver.StandaloneAssetAccess() {
                    @Override
                    public Texture resolveTexture(String projectRelativePath) {
                        return standaloneTexture;
                    }

                    @Override
                    public String[] listPngFramePaths(
                            String projectRelativeDirectory) {
                        return new String[0];
                    }
                }
        );
    }

    private static AssetFixture assetFixture(int... ignoredAssetIds) {
        AssetMetaDatabase database = new AssetMetaDatabase();
        TilesetAssetMeta tileset = (TilesetAssetMeta) database.registerIfAbsent(
                AssetType.TILESET,
                "tiles/ground",
                null,
                AssetMeta.AssetScope.USER
        );
        tileset.referenceCellWidth = 16;
        tileset.referenceCellHeight = 16;
        tileset.projection = TiledProjection.ORTHO;
        tileset.anchor = TilesetAnchor.TOP_CENTER;
        tileset.renderSize = TilesetRenderSize.NATIVE;
        int[] tileAssetIds = new int[ignoredAssetIds.length];
        for (int i = 0; i < ignoredAssetIds.length; i++) {
            TileAssetMeta tile = (TileAssetMeta) database.registerIfAbsent(
                    AssetType.TILE,
                    "tiles/ground/" + i,
                    "orig/tiles/ground/" + i + ".png",
                    AssetMeta.AssetScope.USER
            );
            tile.tilesetId = tileset.id();
            tileAssetIds[i] = tile.id();
        }
        return new AssetFixture(database, tileAssetIds);
    }

    private static TileAnimationRegistry animation(int id, int... frameAssetIds) {
        int[] durations = new int[frameAssetIds.length];
        for (int i = 0; i < durations.length; i++) {
            durations[i] = 100;
        }
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(id, frameAssetIds, durations);
        return registry;
    }

    private record AssetFixture(AssetMetaDatabase database,
                                int[] tileAssetIds) {
    }

    private record Fixture(World world,
                           TiledFallbackSystem system,
                           TiledMapRenderState tiledState,
                           TiledMapLayerData map) {
        void dispose() {
            world.dispose();
        }
    }
}
