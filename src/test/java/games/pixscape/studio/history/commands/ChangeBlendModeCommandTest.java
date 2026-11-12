package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneLoadingInvocationStrategy;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import org.junit.After;
import org.junit.Test;

import static games.pixscape.runtime.render.BlendMode.*;
import static org.junit.Assert.*;

public class ChangeBlendModeCommandTest {
    private final World world = new World(new WorldConfiguration().setInvocationStrategy(new SceneLoadingInvocationStrategy()));
    private final HistoryManager history = new HistoryManager(16);
    private final SceneMeta scene = new SceneMeta("castle", "castle.json");
    private final int actor = world.create();
    private final RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).create(actor);

    @After
    public void dispose() {
        world.dispose();
    }

    private void choose(BlendMode blend) {
        history.execute(new ChangeBlendModeCommand(world, scene, history.historyIds(), actor, blend));
    }

    @Test
    public void referenceRejectsBothMultiplyModesWithoutChangingSavedStateOrDiscardingRedo() {
        scene.lightingEnabled = true;
        material.blendModeId = ALPHA.id;
        choose(ADDITIVE);
        history.undo();
        history.markSaved();
        long revision = history.getMutationRevision();
        choose(MULTIPLY);
        choose(MULTIPLY_ALPHA);
        assertEquals(revision, history.getMutationRevision());
        assertFalse(history.isDirty());
        assertEquals(ALPHA.id, material.blendModeId);
        history.redo();
        assertEquals(ADDITIVE.id, material.blendModeId);
    }

    @Test
    public void activationChecksPendingMaterialsBeforePreparationOrMarkingDirty() {
        material.blendModeId = ALPHA.id;
        choose(MULTIPLY);
        history.markSaved();
        long revision = history.getMutationRevision();
        history.execute(new SetSceneLightingCommand(world, scene, true,
                enabled -> fail("Prepared rejected lighting"), () -> fail("Marked scene for saving")));
        assertFalse(scene.lightingEnabled);
        assertEquals(MULTIPLY.id, material.blendModeId);
        assertEquals(revision, history.getMutationRevision());
        assertFalse(history.isDirty());
    }

    @Test
    public void undoAndRedoRecheckActivationInTheOwningScene() {
        material.blendModeId = ALPHA.id;
        history.execute(new SetSceneLightingCommand(world, scene, true, enabled -> {
        }, null));
        history.undo();
        material.blendModeId = MULTIPLY.id;
        history.redo();
        assertFalse(scene.lightingEnabled);
        assertTrue(history.canRedo());
        material.blendModeId = ALPHA.id;
        history.redo();
        assertTrue(scene.lightingEnabled);
        history.execute(new SetSceneLightingCommand(world, scene, false, enabled -> {
        }, null));
        material.blendModeId = MULTIPLY.id;
        history.undo();
        assertFalse(scene.lightingEnabled);
        assertEquals(2, history.getCursor());
    }

    @Test
    public void preferredForwardWithoutLightsUsesReferenceUntilALightIsActive() {
        scene.lightingEnabled = scene.forwardLighting = true;
        material.blendModeId = ALPHA.id;
        choose(ADDITIVE_ALPHA);
        assertEquals(ADDITIVE_ALPHA.id, material.blendModeId);
        world.getMapper(PointLightComponent.class).create(world.create());
        choose(PREMULT_ALPHA);
        long revision = history.getMutationRevision();
        history.undo();
        choose(ADDITIVE);
        assertEquals(revision, history.getMutationRevision());
        assertEquals(PREMULT_ALPHA.id, material.blendModeId);
    }

    @Test
    public void blendHistoryUsesItsOwnDocumentEvenWhenAnotherSceneIsLit() {
        ProjectConfig previous = ProjectConfig.getInstance();
        try {
            ProjectConfig other = new ProjectConfig();
            other.createSceneMeta("Other");
            other.getCurrentSceneMeta().lightingEnabled = true;
            ProjectConfig.setInstance(other);

            material.blendModeId = ALPHA.id;
            choose(MULTIPLY);
            history.undo();
            assertEquals(ALPHA.id, material.blendModeId);
            history.redo();
            assertEquals(MULTIPLY.id, material.blendModeId);
            assertTrue(other.getCurrentSceneMeta().lightingEnabled);
        } finally {
            ProjectConfig.setInstance(previous);
        }
    }

    @Test
    public void aLightUsesTheAdditiveModesInBothRenderers() {
        scene.lightingEnabled = true;
        world.getMapper(PointLightComponent.class).create(actor);
        material.blendModeId = ADDITIVE.id;
        for (boolean forward : new boolean[]{false, true}) {
            scene.forwardLighting = forward;
            choose(ADDITIVE_ALPHA);
            choose(ALPHA);
            assertEquals(ADDITIVE_ALPHA.id, material.blendModeId);
            choose(ADDITIVE);
            assertEquals(ADDITIVE.id, material.blendModeId);
        }
    }
}
