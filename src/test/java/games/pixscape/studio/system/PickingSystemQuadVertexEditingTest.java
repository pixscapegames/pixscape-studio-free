package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.studio.service.StudioDisplayOffsetResolver;
import org.junit.Test;

import static org.junit.Assert.*;

public class PickingSystemQuadVertexEditingTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void hoverDetectsAllFourRoundHandles() {
        float[] corners = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};

        for (int vertex = 0; vertex < 4; vertex++) {
            int index = vertex * 2;
            assertEquals(vertex, PickingSystem.detectQuadVertexHover(
                    corners, corners[index], corners[index + 1], 1f));
        }
        assertEquals(-1, PickingSystem.detectQuadVertexHover(corners, 30f, 30f, 1f));
    }

    @Test
    public void quadHandlePressIsConsumedOnlyForValidSingleQuadSelection() {
        assertTrue(PickingSystem.shouldStartQuadVertexMove(1, true, true, 2));
        assertFalse(PickingSystem.shouldStartQuadVertexMove(2, true, true, 2));
        assertFalse(PickingSystem.shouldStartQuadVertexMove(1, false, true, 2));
        assertFalse(PickingSystem.shouldStartQuadVertexMove(1, true, false, 2));
        assertFalse(PickingSystem.shouldStartQuadVertexMove(1, true, true, -1));
    }

    @Test
    public void clickWithoutMeaningfulMovementRemainsAComponentAndHistoryNoOp() {
        assertFalse(PickingSystem.hasMeaningfulQuadVertexChange(
                0f, 0f, 0.00005f, -0.00005f));
        assertTrue(PickingSystem.hasMeaningfulQuadVertexChange(
                0f, 0f, 0.001f, 0f));
    }

    @Test
    public void componentIsCreatedOnlyByFirstEffectiveVertexMovement() {
        World world = new World(new WorldConfiguration());
        try {
            int entityId = world.create();
            assertFalse(PickingSystem.applyQuadVertexChange(
                    world.getMapper(QuadDeformComponent.class),
                    entityId,
                    0,
                    0.00005f,
                    0f));
            assertFalse(world.getMapper(QuadDeformComponent.class).has(entityId));

            assertTrue(PickingSystem.applyQuadVertexChange(
                    world.getMapper(QuadDeformComponent.class),
                    entityId,
                    0,
                    2f,
                    3f));
            QuadDeformComponent component = world.getMapper(QuadDeformComponent.class).get(entityId);
            assertEquals(2f, component.blX, 0f);
            assertEquals(3f, component.blY, 0f);
            assertArrayEquals(
                    new float[]{0f, 0f, 0f, 0f, 0f, 0f},
                    new float[]{
                            component.brX, component.brY,
                            component.trX, component.trY,
                            component.tlX, component.tlY},
                    0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void eachVertexWriteChangesOnlyItsOwnPair() {
        for (int vertex = 0; vertex < 4; vertex++) {
            QuadDeformComponent component = filledQuad();
            float[] before = values(component);

            PickingSystem.setQuadVertex(component, vertex, 20f, 21f);

            float[] after = values(component);
            for (int i = 0; i < 8; i++) {
                float expected = i == vertex * 2 ? 20f
                        : i == vertex * 2 + 1 ? 21f
                        : before[i];
                assertEquals(expected, after[i], 0f);
            }
        }
    }

    @Test
    public void inverseRoundTripsRotationPositiveAndNegativeScales() {
        assertInverseRoundTrip(35f, 2f, 3f);
        assertInverseRoundTrip(35f, -2f, 3f);
        assertInverseRoundTrip(35f, 2f, -3f);
    }

    @Test
    public void translationDoesNotChangeStoredLocalOffset() {
        float[] first = inverse(20f, -10f, 1.5f, -2f, 25f, 7f);
        float[] translated = inverse(120f, -50f, 1.5f, -2f, 125f, -33f);

        assertArrayEquals(first, translated, EPSILON);
    }

    @Test
    public void displayOffsetIsRemovedBeforeLocalDeformationIsStored() {
        World world = new World(new WorldConfiguration());
        try {
            int entityId = world.create();
            DynamicEntityRenderState renderState = new DynamicEntityRenderState();
            int slot = renderState.acquireSlotForEntity(entityId);
            renderState.offsetX[slot] = 100f;
            renderState.offsetY[slot] = 20f;
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, renderState, new LayerStateSOA(1), new OrthographicCamera());
            Vector2 displayedTarget = new Vector2(115f, 47f);

            resolver.subtractFrom(entityId, displayedTarget);
            float[] actual = new float[2];
            assertTrue(PickingSystem.worldPointToLocalQuadOffset(
                    axes(0f),
                    transform(1f, 1f),
                    10f,
                    20f,
                    displayedTarget.x,
                    displayedTarget.y,
                    actual));

            assertArrayEquals(new float[]{5f, 7f}, actual, EPSILON);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void nearZeroScaleIsRejectedInsteadOfProducingInvalidOffsets() {
        OrientedBoundsComponent bounds = axes(0f);
        TransformComponent transform = transform(0.001f, 1f);

        assertFalse(PickingSystem.worldPointToLocalQuadOffset(
                bounds, transform, 0f, 0f, 1f, 1f, new float[2]));
    }

    @Test
    public void renderedTrianglePickingFollowsDeformationOutsideAndInsideOldObb() {
        float[] expanded = {0f, 0f, 20f, 0f, 10f, 10f, 0f, 10f};
        assertTrue(PickingSystem.isRenderedQuadHit(expanded, 15f, 2f, 0f));

        float[] retracted = {0f, 0f, 4f, 0f, 4f, 10f, 0f, 10f};
        assertFalse(PickingSystem.isRenderedQuadHit(retracted, 8f, 5f, 0f));

        float[] normal = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};
        assertTrue(PickingSystem.isRenderedQuadHit(normal, 5f, 5f, 0f));
    }

    private static void assertInverseRoundTrip(float rotationDeg, float scaleX, float scaleY) {
        float radians = (float) Math.toRadians(rotationDeg);
        OrientedBoundsComponent bounds = axes(radians);
        TransformComponent transform = transform(scaleX, scaleY);
        float localX = 4f;
        float localY = -5f;
        float baseX = 13f;
        float baseY = -8f;
        float targetX = baseX
                + bounds.ux * localX * scaleX
                + bounds.vx * localY * scaleY;
        float targetY = baseY
                + bounds.uy * localX * scaleX
                + bounds.vy * localY * scaleY;
        float[] actual = new float[2];

        assertTrue(PickingSystem.worldPointToLocalQuadOffset(
                bounds, transform, baseX, baseY, targetX, targetY, actual));
        assertArrayEquals(new float[]{localX, localY}, actual, EPSILON);
    }

    private static float[] inverse(float targetBaseX,
                                   float targetBaseY,
                                   float scaleX,
                                   float scaleY,
                                   float targetX,
                                   float targetY) {
        OrientedBoundsComponent bounds = axes(0f);
        TransformComponent transform = transform(scaleX, scaleY);
        float[] out = new float[2];
        assertTrue(PickingSystem.worldPointToLocalQuadOffset(
                bounds,
                transform,
                targetBaseX,
                targetBaseY,
                targetX,
                targetY,
                out));
        return out;
    }

    private static OrientedBoundsComponent axes(float rotation) {
        OrientedBoundsComponent bounds = new OrientedBoundsComponent();
        bounds.ux = (float) Math.cos(rotation);
        bounds.uy = (float) Math.sin(rotation);
        bounds.vx = -bounds.uy;
        bounds.vy = bounds.ux;
        return bounds;
    }

    private static TransformComponent transform(float scaleX, float scaleY) {
        TransformComponent transform = new TransformComponent();
        transform.scaleX = scaleX;
        transform.scaleY = scaleY;
        return transform;
    }

    private static QuadDeformComponent filledQuad() {
        QuadDeformComponent component = new QuadDeformComponent();
        component.blX = 1f;
        component.blY = 2f;
        component.brX = 3f;
        component.brY = 4f;
        component.trX = 5f;
        component.trY = 6f;
        component.tlX = 7f;
        component.tlY = 8f;
        return component;
    }

    private static float[] values(QuadDeformComponent component) {
        return new float[]{
                component.blX, component.blY,
                component.brX, component.brY,
                component.trX, component.trY,
                component.tlX, component.tlY};
    }
}
