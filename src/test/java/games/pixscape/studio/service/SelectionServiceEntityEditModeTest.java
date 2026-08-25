package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectionServiceEntityEditModeTest {
    private World world;
    private StudioEditingModeService editingModes;
    private SelectionService selection;

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        editingModes = new StudioEditingModeService();
        selection = new SelectionService(world, null, editingModes);
    }

    @After
    public void tearDown() {
        world.dispose();
    }

    @Test
    public void firstSelectionStaysInTransformAndExplicitToggleEntersAndExitsQuad() {
        int entity = createEligibleSprite();

        selection.selectOnly(entity);

        assertFalse(selection.isQuadEditMode());
        assertTrue(selection.toggleQuadEdit(entity));
        assertTrue(selection.isQuadEditModeFor(entity));
        assertTrue(selection.toggleQuadEdit(entity));
        assertFalse(selection.isQuadEditMode());
        assertTrue(selection.isOnlySelected(entity));
    }

    @Test
    public void changingClearingOrTreeReselectingSelectionExitsQuad() {
        int first = createEligibleSprite();
        int second = createEligibleSprite();
        selection.selectOnly(first);
        assertTrue(selection.enterQuadEdit(first));

        selection.selectOnly(second);
        assertFalse(selection.isQuadEditMode());

        selection.selectOnly(first);
        assertTrue(selection.enterQuadEdit(first));
        selection.selectAdd(first, SelectionService.SelectionSource.TREE);
        assertFalse(selection.isQuadEditMode());

        assertTrue(selection.enterQuadEdit(first));
        selection.clearSelection();
        assertFalse(selection.isQuadEditMode());
    }

    @Test
    public void multipleSelectionCannotEnterQuad() {
        int first = createEligibleSprite();
        int second = createEligibleSprite();
        selection.selectOnly(first);
        selection.selectAdd(second);

        assertFalse(selection.enterQuadEdit(first));
        assertFalse(selection.isQuadEditMode());
    }

    @Test
    public void ordinaryAndAnimatedSpritesAreEligible() {
        int ordinary = createEligibleSprite();
        int animated = createEligibleSprite();
        world.edit(animated).create(AnimationComponent.class);

        assertTrue(selection.isQuadEditEligible(ordinary));
        assertTrue(selection.isQuadEditEligible(animated));
    }

    @Test
    public void missingNormalSpriteDataAndActiveRepeatAreIneligible() {
        int incomplete = world.create();
        world.edit(incomplete).create(TransformComponent.class);
        int repeated = createEligibleSprite();
        world.edit(repeated).create(RenderRepeatComponent.class).repeatX = true;

        assertFalse(selection.isQuadEditEligible(incomplete));
        assertFalse(selection.isQuadEditEligible(repeated));
    }

    @Test
    public void specialEntityKindsAreIneligible() {
        assertIneligibleWith(PointLightComponent.class);
        assertIneligibleWith(PhysicsJointComponent.class);
        assertIneligibleWith(ParticleEmitterComponent.class);
        assertIneligibleWith(TiledLayerComponent.class);
    }

    @Test
    public void leavingNormalEditingContextInvalidatesQuadWithoutDeselecting() {
        int entity = createEligibleSprite();
        selection.selectOnly(entity);
        assertTrue(selection.enterQuadEdit(entity));

        editingModes.setMode(StudioEditingMode.PHYSICS, 1);

        assertFalse(selection.isQuadEditMode());
        assertTrue(selection.isOnlySelected(entity));
        assertFalse(selection.enterQuadEdit(entity));
    }

    @Test
    public void ordinaryBodyTranslationKeepsQuadModeAndDeformationState() {
        int entity = createEligibleSprite();
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        QuadDeformComponent quad = world.edit(entity).create(QuadDeformComponent.class);
        quad.trX = 4f;
        selection.selectOnly(entity);
        assertTrue(selection.enterQuadEdit(entity));

        TransformComponent.translate(transform, 12f, -7f);

        assertTrue(selection.isQuadEditModeFor(entity));
        assertEquals(12f, transform.x, 0f);
        assertEquals(-7f, transform.y, 0f);
        assertEquals(4f, quad.trX, 0f);
    }

    private <T extends com.artemis.Component> void assertIneligibleWith(Class<T> componentType) {
        int entity = createEligibleSprite();
        world.edit(entity).create(componentType);
        assertFalse(selection.isQuadEditEligible(entity));
    }

    private int createEligibleSprite() {
        int entity = world.create();
        world.edit(entity).create(TransformComponent.class);
        world.edit(entity).create(DimensionsComponent.class);
        world.edit(entity).create(OrientedBoundsComponent.class);
        world.edit(entity).create(TextureRegionComponent.class);
        world.edit(entity).create(RenderMaterialComponent.class);
        return entity;
    }
}
