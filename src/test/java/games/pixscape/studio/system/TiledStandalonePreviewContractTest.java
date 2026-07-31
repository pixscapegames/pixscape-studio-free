package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.asset.TilesetRenderSize;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.binding;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TiledStandalonePreviewContractTest {

    @After
    public void clearTextureRegistry() {
        TextureRegistry.clear();
    }

    @Test
    public void tiledFallbackDoesNotPatchAssetPresentInRuntimeIndex() {
        int assetId = 17;
        AtomicInteger metadataLookups = new AtomicInteger();
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(assetId, "tile__a" + assetId, texture(16, 16))
        );
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                atlas,
                ignored -> {
                    metadataLookups.incrementAndGet();
                    return null;
                },
                new EmptyStandaloneAccess()
        );

        TiledMapRenderState tiledState = new TiledMapRenderState(1);
        TiledFallbackSystem fallback = new TiledFallbackSystem(
                tiledState,
                resolver,
                ignored -> {
                    metadataLookups.incrementAndGet();
                    return null;
                },
                null
        );
        World world = new World(new WorldConfiguration().setSystem(fallback));
        try {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.type = LayerComponent.TYPE_TILED;

            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).create(entityId);
            tiled.atlasTag = "main";
            tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
            tiled.data.setTile(0, 0, assetId);

            world.process();

            assertEquals(1, atlas.resolveCalls);
            assertEquals(0, metadataLookups.get());
            assertEquals(0, tiledState.getVisibleRefCount());
        } finally {
            world.dispose();
        }
    }

    @Test
    public void tiledFallbackWritesStandaloneVisualAndPreservesTransformFlags() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/ground",
                null,
                AssetMeta.AssetScope.USER
        );
        tileset.referenceCellWidth = 16;
        tileset.referenceCellHeight = 16;
        tileset.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        tileset.anchor = TilesetAnchor.TOP_CENTER;
        tileset.renderSize = TilesetRenderSize.NATIVE;
        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/ground/0",
                "orig/tiles/ground/0.png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = tileset.id();
        tile.sheetIndex = 0;
        tile.cellX = 0;
        tile.cellY = 0;

        Texture standaloneTexture = texture(24, 32);
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                db::findById,
                new StudioAssetVisualResolver.StandaloneAssetAccess() {
                    @Override
                    public Texture resolveTexture(String projectRelativePath) {
                        return standaloneTexture;
                    }

                    @Override
                    public String[] listPngFramePaths(String projectRelativeDirectory) {
                        return new String[0];
                    }
                }
        );
        TiledMapRenderState tiledState = new TiledMapRenderState(1);
        TiledFallbackSystem fallback = new TiledFallbackSystem(
                tiledState,
                resolver,
                db::findById,
                null
        );
        World world = new World(new WorldConfiguration().setSystem(fallback));
        try {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.type = LayerComponent.TYPE_TILED;

            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).create(entityId);
            tiled.atlasTag = "main";
            tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
            byte flags = (byte) (TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V);
            tiled.data.setTile(0, 0, tile.id(), flags);

            world.process();

            int renderRef = tiled.data.tiledRenderRefForTile(0, 0);
            assertTrue(tiledState.isRenderableRef(renderRef));
            assertEquals(TextureRegistry.handleOf(standaloneTexture),
                    tiledState.textureHandle[renderRef]);
            assertEquals(1, tiledState.getVisibleRefCount());
            assertEquals(flags, tiled.data.getTileTransformFlags(0, 0));
        } finally {
            world.dispose();
        }
    }

    private static final class EmptyStandaloneAccess
            implements StudioAssetVisualResolver.StandaloneAssetAccess {
        @Override
        public Texture resolveTexture(String projectRelativePath) {
            return null;
        }

        @Override
        public String[] listPngFramePaths(String projectRelativeDirectory) {
            return new String[0];
        }
    }
}
