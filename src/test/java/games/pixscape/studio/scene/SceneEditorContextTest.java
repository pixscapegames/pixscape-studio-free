package games.pixscape.studio.scene;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SceneEditorContextTest {
    @Test
    public void ownsOneWorldHistoryAndSelectionWithStableIdentityAndCleanHistory() {
        StudioEditingModeService modes = new StudioEditingModeService();
        SceneEditorContext context = initialized("scene/main", modes);

        assertEquals("scene/main", context.sceneIdentity());
        assertSame(context.world(), context.layerService().getWorld());
        assertNotNull(context.historyManager());
        assertNotNull(context.selectionService());
        assertFalse(context.historyManager().isDirty());
        assertEquals(-1, context.selectionService().getFirstSelectedEntityId());
        assertEquals(0L, context.generation());
    }

    @Test
    public void replacementResetsOnlyOwnedStateOnceThenBindsNextStableIdentity() {
        StudioEditingModeService modes = new StudioEditingModeService();
        SceneEditorContext context = initialized("scene/first", modes);
        int entity = context.world().create();
        context.world().process();
        context.selectionService().selectOnly(entity);
        context.historyManager().execute(noopCommand());
        context.rememberSceneSubmode(StudioEditingMode.PHYSICS);

        context.resetForSceneReplacement(17);
        context.bindSceneIdentity("scene/second");

        assertEquals("scene/second", context.sceneIdentity());
        assertEquals(1, context.resetCount());
        assertEquals(1L, context.generation());
        assertFalse(context.historyManager().isDirty());
        assertFalse(context.historyManager().canUndo());
        assertEquals(-1, context.selectionService().getFirstSelectedEntityId());
        assertEquals(StudioEditingMode.NORMAL, context.sceneSubmode());
    }

    @Test
    public void ownedHistoryPreservesUndoRedoAndSavedCursorSemantics() {
        SceneEditorContext context = initialized(
                "scene/main", new StudioEditingModeService());
        AtomicInteger value = new AtomicInteger();
        Command command = new Command() {
            @Override public String label() { return "increment"; }
            @Override public void redo() { value.incrementAndGet(); }
            @Override public void undo() { value.decrementAndGet(); }
        };

        context.historyManager().execute(command);
        assertEquals(1, value.get());
        assertTrue(context.historyManager().isDirty());
        context.historyManager().undo();
        assertEquals(0, value.get());
        context.historyManager().redo();
        assertEquals(1, value.get());
        context.historyManager().markSaved();
        assertFalse(context.historyManager().isDirty());
    }

    @Test
    public void hudCompatibilityProjectionDoesNotOverwriteStoredSceneSubmode() {
        StudioEditingModeService modes = new StudioEditingModeService();
        SceneEditorContext context = initialized("scene/main", modes);
        int selected = context.world().create();
        context.world().process();
        context.selectionService().selectOnly(selected);
        context.rememberSceneSubmode(StudioEditingMode.SPATIAL);

        modes.activateHudDocument(4);

        assertEquals(StudioEditingMode.HUD, modes.getCurrentMode());
        assertEquals(StudioEditingMode.SPATIAL, context.sceneSubmode());
        assertEquals(selected, context.selectionService().getFirstSelectedEntityId());
        modes.activateSceneDocument(context.sceneSubmode(), 5);
        assertEquals(StudioEditingMode.SPATIAL, modes.getCurrentMode());
        assertEquals(selected, context.selectionService().getFirstSelectedEntityId());
    }

    @Test
    public void activeContextRemainsSubmodeAuthorityAcrossTiledHudAndPhysicsProjection() {
        StudioEditingModeService modes = new StudioEditingModeService();
        SceneEditorContext context = initialized("scene/main", modes);
        modes.setActiveSceneSubmodeSink(mode -> {
            if (context.isActive()) context.rememberSceneSubmode(mode);
        });
        context.attached();
        modes.activateSceneDocument(context.sceneSubmode(), 0);

        modes.setMode(StudioEditingMode.TILED, 1);
        assertEquals(StudioEditingMode.TILED, context.sceneSubmode());
        assertEquals(StudioEditingMode.TILED, modes.getCurrentMode());

        context.detached();
        modes.activateHudDocument(2);
        assertEquals(StudioEditingMode.TILED, context.sceneSubmode());

        context.attached();
        modes.activateSceneDocument(context.sceneSubmode(), 3);
        assertEquals(StudioEditingMode.TILED, modes.getCurrentMode());

        modes.setMode(StudioEditingMode.PHYSICS, 4);
        assertEquals(StudioEditingMode.PHYSICS, context.sceneSubmode());
        assertEquals(StudioEditingMode.PHYSICS, modes.getCurrentMode());
    }

    @Test
    public void disposalIsIdempotentAndReleasesOwnedWorldExactlyOnce() {
        SceneEditorContext context = initialized(
                "scene/main", new StudioEditingModeService());

        context.dispose();
        context.dispose();

        assertTrue(context.isDisposed());
        assertEquals(1, context.disposeCount());
        assertFalse(context.isInitialized());
        assertThrows(IllegalStateException.class, context::world);
    }

    @Test
    public void attachDetachPreservesSelectionHistorySubmodeViewAndDirtyState() {
        SceneEditorContext context = initialized(
                "scene/main", new StudioEditingModeService());
        int selected = context.world().create();
        context.world().process();
        context.selectionService().selectOnly(selected);
        context.historyManager().execute(noopCommand());
        context.rememberSceneSubmode(StudioEditingMode.PHYSICS);
        context.captureView(120f, -35f, 2f);
        context.markExplicitSaveRequired();

        context.attached();
        context.recordProcessedFrame();
        context.detached();

        assertFalse(context.isActive());
        assertEquals(1, context.attachCount());
        assertEquals(1, context.detachCount());
        assertEquals(1, context.processedFrameCount());
        assertEquals(selected, context.selectionService().getFirstSelectedEntityId());
        assertTrue(context.historyManager().canUndo());
        assertTrue(context.isDirty());
        assertEquals(StudioEditingMode.PHYSICS, context.sceneSubmode());
        assertEquals(120f, context.cameraX(), 0f);
        assertEquals(-35f, context.cameraY(), 0f);
        assertEquals(2f, context.cameraZoom(), 0f);
        assertThrows(IllegalStateException.class, context::recordProcessedFrame);
    }

    @Test
    public void twoContextsKeepIndependentWorldSelectionHistoryAndViewState() {
        SceneEditorContext first = initialized("scene/first", new StudioEditingModeService());
        SceneEditorContext second = initialized("scene/second", new StudioEditingModeService());
        int firstEntity = first.world().create();
        int secondEntity = second.world().create();
        first.world().process();
        second.world().process();
        first.selectionService().selectOnly(firstEntity);
        second.selectionService().selectOnly(secondEntity);
        first.historyManager().execute(noopCommand());
        first.captureView(10f, 20f, 2f);
        second.captureView(-5f, 4f, .5f);

        assertNotSame(first.world(), second.world());
        assertNotSame(first.historyManager(), second.historyManager());
        assertNotSame(first.selectionService(), second.selectionService());
        assertEquals(firstEntity, first.selectionService().getFirstSelectedEntityId());
        assertEquals(secondEntity, second.selectionService().getFirstSelectedEntityId());
        assertTrue(first.historyManager().canUndo());
        assertFalse(second.historyManager().canUndo());
        assertEquals(2f, first.cameraZoom(), 0f);
        assertEquals(.5f, second.cameraZoom(), 0f);
    }

    private static SceneEditorContext initialized(String identity,
                                                  StudioEditingModeService modes) {
        SceneEditorContext context = new SceneEditorContext(identity, modes);
        context.initializeWorld(
                new World(new WorldConfiguration()),
                new TiledAllocatorService(),
                null);
        return context;
    }

    private static Command noopCommand() {
        return new Command() {
            @Override public String label() { return "test"; }
            @Override public void redo() { }
            @Override public void undo() { }
        };
    }
}
