package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
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
                new AnimationFallbackSystem(null, resolver);
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
                new AnimationFallbackSystem(null, resolver)
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
        animation.animation = "run";
        animation.fps = 10f;
        animation.playing = true;
        animation.loop = true;
        animation.currentClip = "default";
        animation.clips = new ObjectMap<>();
        animation.clips.put("default", new AnimationComponent.Clip(0, 1));
        return entityId;
    }

    private static AnimationAssetMeta animationMeta() {
        return new AnimationAssetMeta(
                7,
                "animations/run",
                "orig/animations/run",
                AssetMeta.AssetScope.USER
        );
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
