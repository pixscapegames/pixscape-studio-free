package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.kotcrab.vis.ui.util.InputValidator;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FieldModelSyncTest {

    @BeforeClass
    public static void loadVisUiSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUiSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void refreshFromModel_alwaysDisplaysCurrentModelValue() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<Float> model = new AtomicReference<>(-12.5f);

        FloatField field = new FloatField(world, e -> model.get(), e -> true).setDisplayDecimals(2);
        field.setEntityId(entityId);

        Assert.assertEquals("-12.50", field.getText());

        model.set(7.25f);
        field.refreshFromModel();

        Assert.assertEquals("7.25", field.getText());
    }

    @Test
    public void floatCommit_resyncsFromModelAfterApplierNormalization() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<Float> model = new AtomicReference<>(0f);

        FloatField field = new FloatField(world, e -> model.get(), e -> true).setDisplayDecimals(2);
        field.setApplier((eid, value) -> model.set(Math.abs(value)));
        field.setEntityId(entityId);

        field.setText("-4.5");
        field.commit();

        Assert.assertEquals(4.5f, model.get(), 0.0001f);
        Assert.assertEquals("4.50", field.getText());
    }

    @Test
    public void floatField_keepsNegativeSignOnRefreshAndCommitAndFocusLost() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<Float> model = new AtomicReference<>(-2f);

        FloatField field = new FloatField(world, e -> model.get(), e -> true).setDisplayDecimals(2);
        field.setApplier((eid, value) -> model.set(value));
        field.setEntityId(entityId);

        Assert.assertEquals("-2.00", field.getText());

        field.setText("-3.5");
        field.commit();
        Assert.assertEquals(-3.5f, model.get(), 0.0001f);
        Assert.assertEquals("-3.50", field.getText());

        field.setText("-7");
        field.commit();
        Assert.assertEquals("-7.00", field.getText());
    }

    @Test
    public void intField_keepsNegativeSignOnRefreshAndCommit() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<Integer> model = new AtomicReference<>(-9);

        IntField field = new IntField(world, e -> model.get(), e -> true);
        field.setApplier((eid, value) -> model.set(value));
        field.setEntityId(entityId);

        Assert.assertEquals("-9", field.getText());

        field.setText("-12");
        field.commit();

        Assert.assertEquals(Integer.valueOf(-12), model.get());
        Assert.assertEquals("-12", field.getText());
    }

    @Test
    public void parseFailure_rollsBackVisualToModelValue() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<Integer> model = new AtomicReference<>(42);

        IntField field = new IntField(world, e -> model.get(), e -> true);
        field.setApplier((eid, value) -> model.set(value));
        field.setEntityId(entityId);

        field.setText("--nope");
        field.commit();

        Assert.assertEquals("42", field.getText());
        Assert.assertEquals(Integer.valueOf(42), model.get());
    }

    @Test
    public void sameValueCommit_reformatsFromModel() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<Float> model = new AtomicReference<>(1.5f);

        FloatField field = new FloatField(world, e -> model.get(), e -> true).setDisplayDecimals(2);
        AtomicInteger applyCalls = new AtomicInteger();
        field.setApplier((eid, value) -> applyCalls.incrementAndGet());
        field.setEntityId(entityId);

        field.setText("1.5000");
        field.commit();

        Assert.assertEquals(0, applyCalls.get());
        Assert.assertEquals("1.50", field.getText());
    }

    @Test
    public void textField_programmaticUpdatesRemainConsistentWithActiveFilter() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AtomicReference<String> model = new AtomicReference<>("sprite-01");

        InputValidator acceptAll = input -> true;
        TextField field = new TextField(
                world,
                e -> model.get(),
                e -> true,
                true,
                acceptAll,
                (tf, c) -> Character.isLetterOrDigit(c)
        );

        field.setApplier((eid, value) -> model.set("['" + value + "']"));
        field.setEntityId(entityId);
        Assert.assertEquals("sprite-01", field.getText());

        field.setText("abc");
        field.commit();
        Assert.assertEquals("['abc']", field.getText());
    }
}
