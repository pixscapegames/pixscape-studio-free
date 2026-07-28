package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.utils.Array;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleWidgetsSyncTest {

    @BeforeClass
    public static void loadVisUiSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUiSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void simpleFloatField_commitResyncsFromReaderAndRollsBackInvalidInput() {
        AtomicReference<Float> model = new AtomicReference<>(-2.5f);
        SimpleFloatField field = new SimpleFloatField().bind(model::get, value -> model.set(Math.abs(value)));

        Assert.assertEquals("-2.50", field.getText());

        field.setText("-6.25");
        field.commit();
        Assert.assertEquals(6.25f, model.get(), 0.0001f);
        Assert.assertEquals("6.25", field.getText());

        field.setText("-");
        field.commit();
        Assert.assertEquals("6.25", field.getText());
    }

    @Test
    public void simpleFloatField_withoutReaderFallsBackToAppliedValue() {
        AtomicReference<Float> applied = new AtomicReference<>(null);
        SimpleFloatField field = new SimpleFloatField().bind(null, applied::set);

        field.setText("-3");
        field.commit();

        Assert.assertEquals(-3f, applied.get(), 0.0001f);
        Assert.assertEquals("-3.00", field.getText());
    }

    @Test
    public void simpleFloatField_commitValidatorRejectsWithoutApplying() {
        AtomicReference<Float> model = new AtomicReference<>(100f);
        AtomicInteger applyCalls = new AtomicInteger();
        SimpleFloatField field = new SimpleFloatField()
                .validateCommitWith(value -> value != null
                        && Float.isFinite(value)
                        && value > 0f)
                .bind(model::get, value -> {
                    applyCalls.incrementAndGet();
                    model.set(value);
                });

        String[] invalidValues = {"0", "-1", "NaN", "Infinity"};
        for (String invalid : invalidValues) {
            field.setText(invalid);
            field.commit();
            Assert.assertEquals(100f, model.get(), 0f);
            Assert.assertEquals("100.00", field.getText());
        }
        Assert.assertEquals(0, applyCalls.get());

        field.setText("64");
        field.commit();

        Assert.assertEquals(64f, model.get(), 0f);
        Assert.assertEquals(1, applyCalls.get());
    }

    @Test
    public void simpleFloatSlider_resyncsFromReaderAndHasNoRefreshCommitLoop() {
        AtomicReference<Float> model = new AtomicReference<>(1f);
        AtomicInteger applyCalls = new AtomicInteger();
        SimpleFloatSlider slider = new SimpleFloatSlider(-10f, 10f, 0.5f).bind(model::get, value -> {
            applyCalls.incrementAndGet();
            model.set(value + 1f);
        });

        slider.setValue(2f);

        Assert.assertEquals(1, applyCalls.get());
        Assert.assertEquals(3f, model.get(), 0.0001f);
        Assert.assertEquals(3f, slider.getValue(), 0.0001f);

        slider.refresh();
        Assert.assertEquals(1, applyCalls.get());
    }

    @Test
    public void simpleSelectBox_resyncsFromReaderAndSupportsReaderNull() {
        AtomicReference<String> model = new AtomicReference<>("A");
        AtomicInteger applyCalls = new AtomicInteger();

        SimpleSelectBox<String> box = new SimpleSelectBox<>();
        box.setItems(Array.with("A", "B", "C"));
        box.bind(model::get, value -> {
            applyCalls.incrementAndGet();
            model.set("C");
        });

        box.setSelected("B");
        Assert.assertEquals(1, applyCalls.get());
        Assert.assertEquals("C", box.getSelected());

        SimpleSelectBox<String> noReader = new SimpleSelectBox<>();
        noReader.setItems(Array.with("A", "B"));
        noReader.bind(null, value -> {
        });
        noReader.setSelected("B");
        Assert.assertEquals("B", noReader.getSelected());
    }

    @Test
    public void simpleTextWidgets_programmaticUpdatesBypassDestructiveFiltersAndNoReaderFallback() {
        AtomicReference<String> textModel = new AtomicReference<>("tag-1");
        SimpleTextField field = new SimpleTextField().bind(textModel::get, value -> textModel.set(value.toUpperCase()));
        field.setTextFieldFilter((tf, c) -> Character.isLetter(c));

        field.refresh();
        Assert.assertEquals("tag-1", field.getText());

        field.setText("abc");
        field.commit();
        Assert.assertEquals("ABC", field.getText());

        SimpleTextArea area = new SimpleTextArea().bind(null, value -> {
        });
        area.setTextFieldFilter((tf, c) -> Character.isLetter(c));
        area.setText("hello");
        area.commit();
        Assert.assertEquals("hello", area.getText());
    }
}
