package games.pixscape.studio.helper;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RenderRebindHelperTest {

    @Test
    public void rebindAppliesRuntimeAtlasMetadata() throws Exception {
        AtlasRegionMetadata metadata = metadata(
                0.1f, 0.2f, 0.7f, 0.8f,
                41, 32, 48
        );
        TrackingAtlasRuntimeService atlas = new TrackingAtlasRuntimeService(metadata);
        Fixture fixture = fixture(7);

        String result = RenderRebindHelper.rebindEntity(
                fixture.entityId,
                "main",
                atlas,
                null,
                fixture.world.getMapper(AssetRefComponent.class),
                fixture.world.getMapper(TextureRegionComponent.class),
                fixture.world.getMapper(RenderMaterialComponent.class),
                null
        );

        assertEquals("atlas", result);
        assertEquals(1, atlas.resolveCalls);
        assertEquals(7, atlas.lastAssetId);
        assertTrue(fixture.region.valid);
        assertEquals(0.1f, fixture.region.u1, 0.0001f);
        assertEquals(0.2f, fixture.region.v1, 0.0001f);
        assertEquals(0.7f, fixture.region.u2, 0.0001f);
        assertEquals(0.8f, fixture.region.v2, 0.0001f);
        assertEquals(32, fixture.region.pixW);
        assertEquals(48, fixture.region.pixH);
        assertEquals(41, fixture.material.textureHandle);
        assertEquals("main", fixture.material.debugAtlasTag);
    }

    @Test
    public void rebindMarksAssetMissingEverywhereAsUnbound() {
        TrackingAtlasRuntimeService atlas = new TrackingAtlasRuntimeService(null);
        Fixture fixture = fixture(7);
        fixture.region.valid = true;
        fixture.material.textureHandle = 99;

        String result = RenderRebindHelper.rebindEntity(
                fixture.entityId,
                "main",
                atlas,
                null,
                fixture.world.getMapper(AssetRefComponent.class),
                fixture.world.getMapper(TextureRegionComponent.class),
                fixture.world.getMapper(RenderMaterialComponent.class),
                null
        );

        assertEquals("unbound", result);
        assertEquals(1, atlas.resolveCalls);
        assertFalse(fixture.region.valid);
        assertEquals(0, fixture.material.textureHandle);
        assertEquals("main", fixture.material.debugAtlasTag);
    }

    @Test
    public void rebindDoesNotSendInvalidAssetIdsToStrictRuntimeApi() {
        for (int assetId : new int[]{0, -1}) {
            TrackingAtlasRuntimeService atlas = new TrackingAtlasRuntimeService(null);
            Fixture fixture = fixture(assetId);

            String result = RenderRebindHelper.rebindEntity(
                    fixture.entityId,
                    "main",
                    atlas,
                    null,
                    fixture.world.getMapper(AssetRefComponent.class),
                    fixture.world.getMapper(TextureRegionComponent.class),
                    fixture.world.getMapper(RenderMaterialComponent.class),
                    null
            );

            assertEquals("unbound", result);
            assertEquals(0, atlas.resolveCalls);
            assertFalse(fixture.region.valid);
            assertEquals(0, fixture.material.textureHandle);
        }
    }

    private static Fixture fixture(int assetId) {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();

        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = assetId;
        assetRef.atlasTag = "main";

        TextureRegionComponent region =
                world.getMapper(TextureRegionComponent.class).create(entityId);
        RenderMaterialComponent material =
                world.getMapper(RenderMaterialComponent.class).create(entityId);
        return new Fixture(world, entityId, region, material);
    }

    private static AtlasRegionMetadata metadata(float u1,
                                                float v1,
                                                float u2,
                                                float v2,
                                                int textureHandle,
                                                int pixelWidth,
                                                int pixelHeight) throws Exception {
        Constructor<AtlasRegionMetadata> constructor =
                AtlasRegionMetadata.class.getDeclaredConstructor(
                        String.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        int.class,
                        int.class,
                        int.class
                );
        constructor.setAccessible(true);
        return constructor.newInstance(
                "tile__a7",
                u1,
                v1,
                u2,
                v2,
                textureHandle,
                pixelWidth,
                pixelHeight
        );
    }

    private static final class TrackingAtlasRuntimeService extends AtlasRuntimeService {
        private final AtlasRegionMetadata metadata;
        int resolveCalls;
        int lastAssetId;

        TrackingAtlasRuntimeService(AtlasRegionMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public AtlasRegionMetadata resolveCached(int assetId, String tag) {
            resolveCalls++;
            lastAssetId = assetId;
            return metadata;
        }
    }

    private static final class Fixture {
        final World world;
        final int entityId;
        final TextureRegionComponent region;
        final RenderMaterialComponent material;

        Fixture(World world,
                int entityId,
                TextureRegionComponent region,
                RenderMaterialComponent material) {
            this.world = world;
            this.entityId = entityId;
            this.region = region;
            this.material = material;
        }
    }
}
