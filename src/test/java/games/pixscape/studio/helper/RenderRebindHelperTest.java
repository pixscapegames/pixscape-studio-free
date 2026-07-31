package games.pixscape.studio.helper;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.ImageAssetMeta;
import games.pixscape.studio.service.GpuSnapshotManager;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.After;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.binding;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.*;

public class RenderRebindHelperTest {

    @After
    public void clearTextureRegistry() {
        TextureRegistry.clear();
    }

    @Test
    public void rebindAppliesResolvedAtlasVisual() {
        Texture texture = texture(32, 48);
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(new TextureAtlas(), binding(7, "tile__a7", texture));
        StudioAssetVisualResolver resolver = resolver(atlas, null, null);
        Fixture fixture = fixture(7);

        String result = rebind(fixture, resolver);

        assertEquals("atlas", result);
        assertEquals(1, atlas.resolveCalls);
        assertTrue(fixture.region.valid);
        assertEquals(0f, fixture.region.u1, 0.0001f);
        assertEquals(0f, fixture.region.v1, 0.0001f);
        assertEquals(1f, fixture.region.u2, 0.0001f);
        assertEquals(1f, fixture.region.v2, 0.0001f);
        assertEquals(32, fixture.region.pixW);
        assertEquals(48, fixture.region.pixH);
        assertEquals(TextureRegistry.handleOf(texture), fixture.material.textureHandle);
        assertEquals("main", fixture.material.debugAtlasTag);
    }

    @Test
    public void rebindAppliesStandaloneAndTransitionsToAtlas() {
        Texture standaloneTexture = texture(20, 30);
        ImageAssetMeta meta = new ImageAssetMeta(
                7,
                "images/hero",
                "orig/images/hero.png",
                AssetMeta.AssetScope.USER
        );
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        StudioAssetVisualResolver resolver =
                resolver(atlas, meta, standaloneTexture);
        Fixture fixture = fixture(7);

        assertEquals("standalone", rebind(fixture, resolver));
        assertEquals(20, fixture.region.pixW);
        assertEquals(30, fixture.region.pixH);
        assertEquals(TextureRegistry.handleOf(standaloneTexture),
                fixture.material.textureHandle);

        Texture packedTexture = texture(40, 50);
        atlas.publish(
                new TextureAtlas(),
                binding(7, "hero__a7", packedTexture)
        );
        assertEquals("atlas", rebind(fixture, resolver));
        assertEquals(40, fixture.region.pixW);
        assertEquals(50, fixture.region.pixH);
        assertEquals(TextureRegistry.handleOf(packedTexture),
                fixture.material.textureHandle);

        atlas.publish(null);
        resolver.invalidateAtlasTag("main");
        assertEquals("standalone", rebind(fixture, resolver));
        assertEquals(TextureRegistry.handleOf(standaloneTexture),
                fixture.material.textureHandle);
    }

    @Test
    public void rebindMarksAssetMissingEverywhereAsUnbound() {
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(new TextureAtlas());
        Fixture fixture = fixture(7);
        fixture.region.valid = true;
        fixture.material.textureHandle = 99;

        String result = rebind(fixture, resolver(atlas, null, null));

        assertEquals("unbound", result);
        assertEquals(1, atlas.resolveCalls);
        assertFalse(fixture.region.valid);
        assertEquals(0, fixture.material.textureHandle);
        assertEquals("main", fixture.material.debugAtlasTag);
    }

    @Test
    public void rebindDoesNotSendInvalidAssetIdsToStrictRuntimeApi() {
        for (int assetId : new int[]{0, -1}) {
            VisualResolverTestSupport.TrackingAtlasService atlas =
                    new VisualResolverTestSupport.TrackingAtlasService("main");
            atlas.publish(new TextureAtlas());
            Fixture fixture = fixture(assetId);

            String result = rebind(fixture, resolver(atlas, null, null));

            assertEquals("unbound", result);
            assertEquals(0, atlas.resolveCalls);
            assertFalse(fixture.region.valid);
            assertEquals(0, fixture.material.textureHandle);
        }
    }

    @Test
    public void globalRebindVisitsAllAssetsAndPreservesDirtyBoundaries()
            throws Exception {
        World world = worldWithDirtyTracker();
        Fixture first = fixture(world, 7);
        Fixture second = fixture(world, 8);
        Texture firstTexture = texture(32, 33);
        Texture secondTexture = texture(42, 43);
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(7, "first__a7", firstTexture),
                binding(8, "second__a8", secondTexture)
        );
        StudioAssetVisualResolver resolver = resolver(atlas, null, null);
        GpuSnapshotManager snapshots =
                new GpuSnapshotManager(new AtlasStudioService(null), null);
        WorldCanvas canvas = canvas(world, snapshots);

        RenderRebindHelper.rebindAfterAtlasChange(canvas, "main", resolver);

        assertEquals(TextureRegistry.handleOf(firstTexture),
                first.material.textureHandle);
        assertEquals(TextureRegistry.handleOf(secondTexture),
                second.material.textureHandle);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        assertTrue(dirty.isDirty(first.entityId, DirtyBits.MATERIAL));
        assertTrue(dirty.isDirty(second.entityId, DirtyBits.MATERIAL));
        assertTrue(hasSnapshotDirtyReason(
                snapshots,
                "main",
                "render-rebind-after-atlas-change"
        ));
    }

    @Test
    public void historyRebindVisitsOnlyRequestedEntityAndPreservesDirtyBoundaries()
            throws Exception {
        World world = worldWithDirtyTracker();
        Fixture requested = fixture(world, 7);
        Fixture untouched = fixture(world, 8);
        Texture firstTexture = texture(32, 33);
        Texture secondTexture = texture(42, 43);
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(7, "first__a7", firstTexture),
                binding(8, "second__a8", secondTexture)
        );
        StudioAssetVisualResolver resolver = resolver(atlas, null, null);
        GpuSnapshotManager snapshots =
                new GpuSnapshotManager(new AtlasStudioService(null), null);
        WorldCanvas canvas = canvas(world, snapshots);

        assertEquals(
                "atlas",
                RenderRebindHelper.rebindHistoryEntityRenderAssets(
                        canvas,
                        "main",
                        resolver,
                        requested.entityId
                )
        );

        assertEquals(TextureRegistry.handleOf(firstTexture),
                requested.material.textureHandle);
        assertEquals(0, untouched.material.textureHandle);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        assertTrue(dirty.isDirty(requested.entityId, DirtyBits.MATERIAL));
        assertFalse(dirty.isDirty(untouched.entityId, DirtyBits.MATERIAL));
        assertTrue(hasSnapshotDirtyReason(
                snapshots,
                "main",
                "history-entity-render-rebind"
        ));
    }

    private static StudioAssetVisualResolver resolver(
            VisualResolverTestSupport.TrackingAtlasService atlas,
            AssetMeta meta,
            Texture standaloneTexture) {
        return new StudioAssetVisualResolver(
                atlas,
                id -> meta != null && meta.id() == id ? meta : null,
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
    }

    private static String rebind(Fixture fixture,
                                 StudioAssetVisualResolver resolver) {
        return RenderRebindHelper.rebindEntity(
                fixture.entityId,
                "main",
                resolver,
                fixture.world.getMapper(AssetRefComponent.class),
                fixture.world.getMapper(TextureRegionComponent.class),
                fixture.world.getMapper(RenderMaterialComponent.class),
                null
        );
    }

    private static Fixture fixture(int assetId) {
        World world = new World(new WorldConfiguration());
        return fixture(world, assetId);
    }

    private static Fixture fixture(World world, int assetId) {
        int entityId = world.create();

        AssetRefComponent assetRef =
                world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = assetId;
        assetRef.atlasTag = "main";

        TextureRegionComponent region =
                world.getMapper(TextureRegionComponent.class).create(entityId);
        RenderMaterialComponent material =
                world.getMapper(RenderMaterialComponent.class).create(entityId);
        return new Fixture(world, entityId, region, material);
    }

    private static World worldWithDirtyTracker() {
        return new World(
                new WorldConfiguration().setSystem(new DirtyTrackerSystem(16))
        );
    }

    private static WorldCanvas canvas(World world,
                                      GpuSnapshotManager snapshots)
            throws Exception {
        Unsafe unsafe = unsafe();
        WorldCanvas canvas =
                (WorldCanvas) unsafe.allocateInstance(WorldCanvas.class);
        setField(unsafe, canvas, "world", world);
        setField(unsafe, canvas, "gpuSnapshotManager", snapshots);
        return canvas;
    }

    private static boolean hasSnapshotDirtyReason(
            GpuSnapshotManager snapshots,
            String sceneTag,
            String reason
    ) throws Exception {
        Method method = GpuSnapshotManager.class.getDeclaredMethod(
                "hasDirtyReason",
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(snapshots, sceneTag, reason);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Unsafe unsafe,
                                 Object target,
                                 String fieldName,
                                 Object value)
            throws Exception {
        Field field = WorldCanvas.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        unsafe.putObject(
                target,
                unsafe.objectFieldOffset(field),
                value
        );
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
