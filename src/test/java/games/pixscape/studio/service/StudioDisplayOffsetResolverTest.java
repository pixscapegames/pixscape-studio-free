package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudioDisplayOffsetResolverTest {

    @Test
    public void renderSlotUsesTheExactRuntimeOffset() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            DynamicEntityRenderState renderState = new DynamicEntityRenderState();
            int slot = renderState.acquireSlotForEntity(entity);
            renderState.offsetX[slot] = 42f;
            renderState.offsetY[slot] = -17f;

            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, renderState, new LayerStateSOA(1), new OrthographicCamera());
            Vector2 actual = new Vector2();
            resolver.resolve(entity, actual);

            assertEquals(42f, actual.x, 0f);
            assertEquals(-17f, actual.y, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void nonRenderedEntityUsesItsLayerParallaxFallback() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 2;
            LayerStateSOA layers = new LayerStateSOA(4);
            layers.enabled[2] = true;
            layers.parallaxX[2] = 0.5f;
            layers.parallaxY[2] = 0.75f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);

            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, new DynamicEntityRenderState(), layers, camera);
            Vector2 actual = new Vector2();
            resolver.resolve(entity, actual);

            assertEquals(50f, actual.x, 0f);
            assertEquals(50f, actual.y, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void nonRenderedPhysicalEntityUsesScenePhysicsParallaxFallback() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 2;
            world.getMapper(PhysicsBodyComponent.class).create(entity);
            LayerStateSOA layers = new LayerStateSOA(4);
            layers.enabled[2] = true;
            layers.parallaxX[2] = 0.25f;
            layers.parallaxY[2] = 0.5f;
            layers.physicsParallaxX = 0.8f;
            layers.physicsParallaxY = 0.6f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);

            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, new DynamicEntityRenderState(), layers, camera);
            Vector2 actual = new Vector2();
            resolver.resolve(entity, actual);

            assertEquals(20f, actual.x, 0.0001f);
            assertEquals(80f, actual.y, 0.0001f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void physicalEntityRenderSlotStillUsesExactRuntimeOffset() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 0;
            world.getMapper(PhysicsBodyComponent.class).create(entity);
            LayerStateSOA layers = new LayerStateSOA(1);
            layers.enabled[0] = true;
            layers.physicsParallaxX = 0.8f;
            layers.physicsParallaxY = 0.6f;
            DynamicEntityRenderState renderState = new DynamicEntityRenderState();
            int slot = renderState.acquireSlotForEntity(entity);
            renderState.offsetX[slot] = 37f;
            renderState.offsetY[slot] = -11f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);

            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, renderState, layers, camera);
            Vector2 actual = new Vector2();
            resolver.resolve(entity, actual);

            assertEquals(37f, actual.x, 0f);
            assertEquals(-11f, actual.y, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void physicalFallbackDoesNotChangeWhenOwningLayerChanges() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            EntityIndexComponent index =
                    world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = 0;
            world.getMapper(PhysicsBodyComponent.class).create(entity);
            LayerStateSOA layers = new LayerStateSOA(2);
            layers.enabled[0] = true;
            layers.enabled[1] = true;
            layers.parallaxX[0] = 0.1f;
            layers.parallaxY[0] = 0.2f;
            layers.parallaxX[1] = 0.9f;
            layers.parallaxY[1] = 0.95f;
            layers.physicsParallaxX = 0.8f;
            layers.physicsParallaxY = 0.6f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, new DynamicEntityRenderState(), layers, camera);
            Vector2 first = new Vector2();
            Vector2 second = new Vector2();

            resolver.resolve(entity, first);
            index.layerIndex = 1;
            resolver.resolve(entity, second);

            assertEquals(first.x, second.x, 0f);
            assertEquals(first.y, second.y, 0f);
            assertEquals(20f, second.x, 0.0001f);
            assertEquals(80f, second.y, 0.0001f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void fallbackNormalizesNanAxesAndKeepsFactorOneAtZero() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 0;
            LayerStateSOA layers = new LayerStateSOA(1);
            layers.enabled[0] = true;
            layers.parallaxX[0] = Float.NaN;
            layers.parallaxY[0] = 0.5f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, new DynamicEntityRenderState(), layers, camera);
            Vector2 actual = new Vector2();

            resolver.resolve(entity, actual);
            assertEquals(0f, actual.x, 0f);
            assertEquals(100f, actual.y, 0f);

            layers.parallaxX[0] = 1f;
            layers.parallaxY[0] = 1f;
            resolver.resolve(entity, actual);
            assertEquals(0f, actual.x, 0f);
            assertEquals(0f, actual.y, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void disabledOrInvalidLayerHasNoFallbackOffset() {
        World world = new World(new WorldConfiguration());
        try {
            int disabledEntity = world.create();
            int invalidEntity = world.create();
            world.getMapper(EntityIndexComponent.class).create(disabledEntity).layerIndex = 0;
            world.getMapper(EntityIndexComponent.class).create(invalidEntity).layerIndex = 3;
            LayerStateSOA layers = new LayerStateSOA(1);
            layers.parallaxX[0] = 0.25f;
            layers.parallaxY[0] = 0.25f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, new DynamicEntityRenderState(), layers, camera);
            Vector2 actual = new Vector2();

            resolver.resolve(disabledEntity, actual);
            assertEquals(0f, actual.x, 0f);
            assertEquals(0f, actual.y, 0f);

            resolver.resolve(invalidEntity, actual);
            assertEquals(0f, actual.x, 0f);
            assertEquals(0f, actual.y, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void addAndSubtractApplyOneSharedOffsetToGeometry() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            DynamicEntityRenderState renderState = new DynamicEntityRenderState();
            int slot = renderState.acquireSlotForEntity(entity);
            renderState.offsetX[slot] = 100f;
            renderState.offsetY[slot] = 20f;
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, renderState, new LayerStateSOA(1), new OrthographicCamera());
            float[] corners = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};
            Vector2 pivot = new Vector2(5f, 5f);

            resolver.addTo(entity, corners, 4);
            resolver.addTo(entity, pivot);
            assertEquals(100f, corners[0], 0f);
            assertEquals(20f, corners[1], 0f);
            assertEquals(110f, corners[4], 0f);
            assertEquals(30f, corners[5], 0f);
            assertEquals(105f, pivot.x, 0f);
            assertEquals(25f, pivot.y, 0f);

            resolver.subtractFrom(entity, pivot);
            assertEquals(5f, pivot.x, 0f);
            assertEquals(5f, pivot.y, 0f);
        } finally {
            world.dispose();
        }
    }
}
