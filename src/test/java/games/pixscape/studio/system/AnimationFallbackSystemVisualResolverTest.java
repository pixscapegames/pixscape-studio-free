package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.StudioAnimationPreviewRefresher;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import org.junit.After;
import org.junit.Test;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.binding;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnimationFallbackSystemVisualResolverTest {

    @After
    public void clearTextureRegistry() {
        TextureRegistry.clear();
    }

    @Test
    public void standaloneAnimationUsesFrameAwareResolver() {
        Texture first = texture(10, 11);
        Texture second = texture(20, 21);
        AnimationAssetMeta meta = animationMeta();
        TrackingStandaloneAccess standalone =
                new TrackingStandaloneAccess(first, second);
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                id -> id == meta.id() ? meta : null,
                standalone
        );
        AnimationFallbackSystem system =
                new AnimationFallbackSystem(null, resolver, id -> meta);
        World world = new World(new WorldConfiguration().setSystem(system));
        try {
            int entityId = createAnimationEntity(world, meta.id());
            world.setDelta(0.11f);
            world.process();

            AnimationComponent animation =
                    world.getMapper(AnimationComponent.class).get(entityId);
            TextureRegionComponent region =
                    world.getMapper(TextureRegionComponent.class).get(entityId);
            RenderMaterialComponent material =
                    world.getMapper(RenderMaterialComponent.class).get(entityId);
            assertEquals(1, animation.frame);
            assertTrue(region.valid);
            assertEquals(20, region.pixW);
            assertEquals(21, region.pixH);
            assertEquals(TextureRegistry.handleOf(second), material.textureHandle);
            assertEquals(1, standalone.listAttempts);
            assertEquals(2, standalone.textureAttempts);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void atlasBindingLeavesFallbackPlaybackStateUntouched() {
        AnimationAssetMeta meta = animationMeta();
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(
                new TextureAtlas(),
                binding(meta.id(), "run__a" + meta.id(), texture(10, 10), texture(10, 10))
        );
        TrackingStandaloneAccess standalone =
                new TrackingStandaloneAccess(texture(10, 10), texture(10, 10));
        StudioAssetVisualResolver resolver =
                new StudioAssetVisualResolver(atlas, id -> meta, standalone);
        World world = new World(new WorldConfiguration().setSystem(
                new AnimationFallbackSystem(null, resolver, id -> meta)
        ));
        try {
            int entityId = createAnimationEntity(world, meta.id());
            AnimationComponent animation =
                    world.getMapper(AnimationComponent.class).get(entityId);
            world.setDelta(1f);
            world.process();

            assertEquals(0f, animation.stateTime, 0f);
            assertEquals(0, standalone.listAttempts);
            assertEquals(0, standalone.textureAttempts);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void pausedAnimationRefreshesSelectedClipImmediately() {
        Texture first = texture(10, 11);
        Texture second = texture(20, 21);
        AnimationAssetMeta meta = animationMeta();
        meta.clips.put("second", new AnimationClipMeta(1, 1));
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                id -> meta,
                new TrackingStandaloneAccess(first, second)
        );
        World world = new World(new WorldConfiguration());
        try {
            int entityId = createAnimationEntity(world, meta.id());
            AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entityId);
            animation.currentClip = "second";
            animation.playing = false;
            animation.frame = -1;
            StudioAnimationPreviewRefresher refresher = new StudioAnimationPreviewRefresher(
                    new DynamicEntityRenderState(), resolver, id -> meta);
            refresher.bindWorld(world);

            refresher.refreshSelectedFrame(entityId);

            assertEquals(1, animation.frame);
            assertEquals(TextureRegistry.handleOf(second),
                    world.getMapper(RenderMaterialComponent.class).get(entityId).textureHandle);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void metadataRebindingMovesPreviewResolutionToPublishedDefinition() {
        Texture first = texture(10, 11);
        Texture second = texture(20, 21);
        AnimationAssetMeta previous = animationMeta();
        AnimationAssetMeta published = animationMeta();
        published.currentClip = "second";
        published.clips.put("second", new AnimationClipMeta(1, 1));
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                id -> previous,
                new TrackingStandaloneAccess(first, second)
        );
        World world = new World(new WorldConfiguration());
        try {
            int entityId = createAnimationEntity(world, previous.id());
            AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entityId);
            animation.currentClip = "second";
            animation.playing = false;
            animation.frame = -1;
            StudioAnimationPreviewRefresher refresher = new StudioAnimationPreviewRefresher(
                    new DynamicEntityRenderState(), resolver, id -> previous);
            refresher.bindWorld(world);

            refresher.refreshSelectedFrame(entityId);
            assertEquals(-1, animation.frame);

            resolver.setAssetMetaLookup(id -> published);
            refresher.setAssetMetaLookup(id -> published);
            refresher.refreshSelectedFrame(entityId);

            assertEquals(1, animation.frame);
            assertEquals(TextureRegistry.handleOf(second),
                    world.getMapper(RenderMaterialComponent.class).get(entityId).textureHandle);
        } finally {
            world.dispose();
        }
    }

    private static int createAnimationEntity(World world, int assetId) {
        int entityId = world.create();
        AssetRefComponent assetRef =
                world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = assetId;
        assetRef.atlasTag = "main";
        world.getMapper(TextureRegionComponent.class).create(entityId);
        world.getMapper(RenderMaterialComponent.class).create(entityId);
        AnimationComponent animation =
                world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(assetId);
        animation.fps = 10f;
        animation.playing = true;
        animation.loop = true;
        animation.currentClip = "default";
        return entityId;
    }

    private static AnimationAssetMeta animationMeta() {
        AnimationAssetMeta meta = new AnimationAssetMeta(
                7,
                "animations/run",
                "orig/animations/run",
                AssetMeta.AssetScope.USER
        );
        meta.frameCount = 2;
        meta.fps = 10f;
        meta.currentClip = "default";
        meta.clips.put("default", new AnimationClipMeta(0, 1));
        return meta;
    }

    private static final class TrackingStandaloneAccess
            implements StudioAssetVisualResolver.StandaloneAssetAccess {
        private final Texture first;
        private final Texture second;
        int listAttempts;
        int textureAttempts;

        TrackingStandaloneAccess(Texture first, Texture second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Texture resolveTexture(String projectRelativePath) {
            textureAttempts++;
            return projectRelativePath.endsWith("01.png") ? first : second;
        }

        @Override
        public String[] listPngFramePaths(String projectRelativeDirectory) {
            listAttempts++;
            return new String[]{
                    projectRelativeDirectory + "/01.png",
                    projectRelativeDirectory + "/02.png"
            };
        }
    }
}
