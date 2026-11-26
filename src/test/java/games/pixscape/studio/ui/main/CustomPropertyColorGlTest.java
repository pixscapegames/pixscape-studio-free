package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.studio.ui.property.entityproperties.EditPropertiesDialog;
import games.pixscape.studio.ui.widget.ColorPickerField;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class CustomPropertyColorGlTest {
    @Test
    public void realPickerAppliesRgbaToSimpleAndListProperties() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(800, (studio, root) -> {
            for (boolean listMode : new boolean[]{false, true}) {
                int original = 0x10203040;
                int selected = 0x50607080;
                PropertySet source = listMode
                        ? new PropertySet().putList("colors", Array.with(PropertyValue.ofColorRgba8888(original)))
                        : new PropertySet().putColorRgba8888("tint", original);
                AtomicReference<PropertySet> applied = new AtomicReference<>();
                var parent = new EditPropertiesDialog("Properties", source, applied::set);
                parent.show(studio.getUiStage());
                settle(studio);
                var editor = parent;
                if (listMode) {
                    click(studio, (Actor) field(row(parent), "editListButton"));
                    editor = (EditPropertiesDialog) studio.getUiStage().getActors().peek();
                }
                var colorField = (ColorPickerField) field(row(editor), "colorField");
                click(studio, (Actor) field(colorField, "button"));
                var picker = (ColorPicker) studio.getUiStage().getActors().peek();
                var hex = (VisTextField) field(picker.getPicker(), "hexField");
                assertEquals("Opening the picker must preserve alpha", "10203040", hex.getText().toLowerCase());
                studio.getUiStage().setKeyboardFocus(hex);
                hex.selectAll();
                for (char character : "50607080".toCharArray()) {
                    studio.getUiStage().keyTyped(character);
                }
                click(studio, (Actor) field(picker, "okButton"));
                assertEquals(selected, com.badlogic.gdx.graphics.Color.rgba8888(colorField.getCurrentColor()));
                assertEquals(original, listMode ? source.getListValue("colors").getColorRgba8888(0)
                        : source.getColorRgba8888("tint", 0));
                click(studio, button(editor, "Apply"));
                if (listMode) {
                    assertNull(applied.get());
                    click(studio, button(parent, "Apply"));
                }
                assertNotNull(applied.get());
                assertEquals(selected, listMode ? applied.get().getListValue("colors").getColorRgba8888(0)
                        : applied.get().getColorRgba8888("tint", 0));
                assertTrue(picker.isDisposed());
            }
        });
    }

    private static Object row(EditPropertiesDialog dialog) throws Exception {
        return ((Array<?>) field(dialog, "rows")).first();
    }

    private static Object field(Object target, String name) throws Exception {
        return ResourceAssetInspectorGlTest.field(target, name);
    }

    private static Actor button(Group group, String text) {
        for (Actor actor : group.getChildren()) {
            if (actor instanceof TextButton button && text.equals(button.getText().toString())) {
                return button;
            }
            if (actor instanceof Group child) {
                Actor found = button(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void click(StudioApplicationAdapter studio, Actor actor) {
        assertNotNull(actor);
        var stage = studio.getUiStage();
        var point = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2, actor.getHeight() / 2));
        stage.stageToScreenCoordinates(point);
        stage.touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        stage.touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        settle(studio);
    }

    private static void settle(StudioApplicationAdapter studio) {
        studio.getUiStage().act(1f);
        studio.getUiStage().act(0f);
        studio.render();
    }
}
