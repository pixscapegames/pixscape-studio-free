package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TransformFieldFactoryTest {

    @BeforeClass
    public static void loadVisUiSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUiSkin() {
        VisUiTestBootstrap.unloadSkin();
    }


    @Test
    public void posXCommit_createsHistoryCommandAndSupportsUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(16);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent t = world.getMapper(TransformComponent.class).create(entityId);
        t.x = 3f;
        t.originX = 0f;

        TransformFieldFactory factory = new TransformFieldFactory(world, history);
        FloatField field = factory.posX(entityId);

        field.setText("12");
        field.commit();

        Assert.assertEquals(12f, t.x, 0.0001f);
        Assert.assertTrue(history.canUndo());

        history.undo();
        Assert.assertEquals(3f, t.x, 0.0001f);

        history.redo();
        Assert.assertEquals(12f, t.x, 0.0001f);
    }

    @Test
    public void spriteVisiblePosition_usesXMinusOriginAndCommitWritesBackWithOriginOffset() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(16);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent t = world.getMapper(TransformComponent.class).create(entityId);
        t.x = 20f;
        t.originX = 5f;
        world.getMapper(AssetRefComponent.class).create(entityId);

        TransformFieldFactory factory = new TransformFieldFactory(world, history);
        FloatField field = factory.posX(entityId);

        Assert.assertEquals("15", field.getText());

        field.setText("8");
        field.commit();

        Assert.assertEquals(13f, t.x, 0.0001f);
        Assert.assertEquals("8", field.getText());
    }

    @Test
    public void gameObjectPositionFieldKeepsAuthoredBottomLeftWhenOriginIsCentered() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(16);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);
        TransformComponent transform = world.getMapper(TransformComponent.class)
                .create(entityId);
        transform.x = 20f;
        transform.originX = 5f;
        transform.scaleX = transform.scaleY = 1f;
        world.getMapper(GameObjectComponent.class).create(entityId);

        FloatField field = new TransformFieldFactory(world, history).posX(entityId);

        Assert.assertEquals("20", field.getText());
        field.setText("8");
        field.commit();
        Assert.assertEquals(8f, transform.x, 0.0001f);
        Assert.assertEquals(5f, transform.originX, 0f);
    }

    @Test
    public void originAndScaleAndRotation_commitsThroughHistoryAndSkipsNoop() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(32);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent t = world.getMapper(TransformComponent.class).create(entityId);
        t.rotationRad = 0f;
        t.scaleX = 1f;
        t.scaleY = 1f;
        t.originX = 2f;
        t.originY = 3f;

        TransformFieldFactory factory = new TransformFieldFactory(world, history);
        int cursorBefore = history.getCursor();

        FloatField rotationField = factory.rotation(entityId);
        rotationField.setText("90");
        rotationField.commit();

        FloatField scaleXField = factory.scaleX(entityId);
        scaleXField.setText("-2");
        scaleXField.commit();

        FloatField originYField = factory.originY(entityId);
        originYField.setText("7");
        originYField.commit();

        FloatField noop = factory.scaleY(entityId);
        noop.setText("1");
        noop.commit();

        Assert.assertTrue(history.getCursor() >= cursorBefore + 3);
        Assert.assertEquals(-2f, t.scaleX, 0.0001f);
        Assert.assertEquals(7f, t.originY, 0.0001f);
        Assert.assertEquals(1f, t.scaleY, 0.0001f);

        int cursorAfterChanges = history.getCursor();
        noop.setText("1.0");
        noop.commit();
        Assert.assertEquals(cursorAfterChanges, history.getCursor());
    }
}
