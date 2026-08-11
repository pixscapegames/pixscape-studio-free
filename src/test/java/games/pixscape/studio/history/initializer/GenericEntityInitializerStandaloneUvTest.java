package games.pixscape.studio.history.initializer;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenericEntityInitializerStandaloneUvTest {

    @Test
    public void configureStandaloneSpriteUsesLayerRelativeUvs() {
        World world = new World(new WorldConfigurationBuilder().build());

        int e = world.create();
        int pixW = 128;
        int pixH = 96;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        -1,
                        null,
                        pixW,
                        pixH,
                        0f,
                        0f,
                        pixW * 0.5f,
                        pixH * 0.5f,
                        0,
                        0,
                        123,
                        "hero",
                        0
                );
        init.init(e);

        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        TextureRegionComponent tr = mTR.get(e);

        assertEquals(0f, tr.u1, 0.0001f);
        assertEquals(0f, tr.v1, 0.0001f);
        assertEquals(1f, tr.u2, 0.0001f);
        assertEquals(1f, tr.v2, 0.0001f);
        assertTrue(tr.valid);
    }

    @Test
    public void configureAnimationKeepsStandaloneBaseUvSpan() {
        World world = new World(new WorldConfigurationBuilder().build());

        int e = world.create();
        int frameW = 64;
        int frameH = 32;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        -1,
                        null,
                        frameW,
                        frameH,
                        0f,
                        0f,
                        frameW * 0.5f,
                        frameH * 0.5f,
                        0,
                        0,
                        123,
                        "walk",
                        0
                )
                .configureAnimation(17, "default", 12f, true);
        init.init(e);

        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<AnimationComponent> mAnim = world.getMapper(AnimationComponent.class);

        TextureRegionComponent tr = mTR.get(e);
        AnimationComponent anim = mAnim.get(e);

        assertEquals(1, anim.animationAssetIds.size);
        assertEquals(17, anim.animationAssetIds.first());
        assertEquals(-1, anim.frame);
        assertEquals(0f, tr.u1, 0.0001f);
        assertEquals(1f, tr.u2, 0.0001f);
        assertEquals(0f, tr.v1, 0.0001f);
        assertEquals(1f, tr.v2, 0.0001f);
    }

    @Test
    public void configureStandaloneSpriteAndAnimationKeepExpectedFpsUvAndScaleDefaults() {
        World world = new World(new WorldConfigurationBuilder().build());

        int e = world.create();
        int frameW = 80;
        int frameH = 40;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        -1,
                        null,
                        frameW,
                        frameH,
                        10f,
                        12f,
                        frameW * 0.5f,
                        frameH * 0.5f,
                        0,
                        0,
                        321,
                        "run",
                        0
                )
                .configureAnimation(31, "default", 24f, true);
        init.init(e);

        ComponentMapper<AnimationComponent> mAnim = world.getMapper(AnimationComponent.class);
        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<DimensionsComponent> mDimensions = world.getMapper(DimensionsComponent.class);

        AnimationComponent anim = mAnim.get(e);
        TextureRegionComponent tr = mTR.get(e);
        TransformComponent transform = mTransform.get(e);
        DimensionsComponent dimensions = mDimensions.get(e);

        assertEquals(24f, anim.fps, 0.0001f);
        assertEquals(31, anim.animationAssetIds.first());
        assertEquals(0f, tr.u1, 0.0001f);
        assertEquals(0f, tr.v1, 0.0001f);
        assertEquals(1f, transform.scaleX, 0.0001f);
        assertEquals(1f, transform.scaleY, 0.0001f);
        assertEquals(frameW, dimensions.width, 0.0001f);
        assertEquals(frameH, dimensions.height, 0.0001f);
    }

    @Test
    public void syncFromPreservesSpatialHeightComponent() {
        World world = new World(new WorldConfigurationBuilder().build());

        int source = world.create();
        SpatialHeightComponent spatial = world.getMapper(SpatialHeightComponent.class).create(source);
        spatial.altitude = 3.5f;
        spatial.height = 12.25f;

        GenericEntityInitializer init = new GenericEntityInitializer(world);
        init.syncFrom(source);

        int restored = world.create();
        init.init(restored);

        SpatialHeightComponent restoredSpatial =
                world.getMapper(SpatialHeightComponent.class).get(restored);
        assertEquals(3.5f, restoredSpatial.altitude, 0.0001f);
        assertEquals(12.25f, restoredSpatial.height, 0.0001f);
    }

}
