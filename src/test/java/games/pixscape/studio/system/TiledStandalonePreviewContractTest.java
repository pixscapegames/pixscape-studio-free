package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TiledStandalonePreviewContractTest {

    @Test
    public void tiledGhostPreviewRejectsNonTileAssetMetadataBeforeStandaloneLoad() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/TiledGhostPreviewSystem.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("if (meta.type != AssetType.TILE)"));
        assertTrue(source.indexOf("if (meta.type != AssetType.TILE)")
                < source.indexOf("StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath)"));
    }

    @Test
    public void tiledFallbackRejectsNonTileAssetMetadataBeforeStandaloneLoad() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/TiledFallbackSystem.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("if (meta.type != AssetType.TILE)"));
        assertTrue(source.indexOf("if (meta.type != AssetType.TILE)")
                < source.indexOf("StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath)"));
    }

    @Test
    public void tiledFallbackDoesNotPatchAssetPresentInRuntimeIndex() throws Exception {
        int assetId = 17;
        AtomicInteger bindingLookups = new AtomicInteger();
        AtomicInteger standaloneLookups = new AtomicInteger();
        AtlasAssetBinding indexedBinding = bindingSentinel(assetId);

        AtlasRuntimeService atlasRuntimeService = new AtlasRuntimeService() {
            @Override
            public AtlasAssetBinding resolveBinding(int requestedAssetId, String tag) {
                bindingLookups.incrementAndGet();
                return requestedAssetId == assetId && "main".equals(tag) ? indexedBinding : null;
            }
        };

        TiledMapRenderState tiledState = new TiledMapRenderState(1);
        TiledFallbackSystem fallback = new TiledFallbackSystem(
                tiledState,
                atlasRuntimeService,
                ignored -> {
                    standaloneLookups.incrementAndGet();
                    return null;
                },
                null
        );
        World world = new World(new WorldConfiguration().setSystem(fallback));
        try {
            int entityId = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
            layer.type = LayerComponent.TYPE_TILED;

            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entityId);
            tiled.atlasTag = "main";
            tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
            tiled.data.setTile(0, 0, assetId);

            world.process();

            assertEquals(1, bindingLookups.get());
            assertEquals(0, standaloneLookups.get());
            assertEquals(0, tiledState.getVisibleRefCount());
        } finally {
            world.dispose();
        }
    }

    private static AtlasAssetBinding bindingSentinel(int assetId) throws Exception {
        Constructor<AtlasAssetBinding> constructor = AtlasAssetBinding.class.getDeclaredConstructor(
                int.class,
                String.class,
                TextureAtlas.AtlasRegion.class,
                Array.class,
                AtlasRegionMetadata.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(assetId, "tile__a" + assetId, null, new Array<>(), null);
    }
}
