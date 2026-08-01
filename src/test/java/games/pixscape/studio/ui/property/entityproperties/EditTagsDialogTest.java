package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class EditTagsDialogTest {
    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void existingTagsLoadIntoEditableRowsWithImageControls() throws Exception {
        EditTagsDialog dialog = new EditTagsDialog("Edit Tags", List.of("alpha", "beta"), null);
        Array<?> rows = rows(dialog);
        Button addButton = field(dialog, "addButton", Button.class);
        VisTable tagsTable = field(dialog, "tagsTable", VisTable.class);

        Assert.assertEquals(2, rows.size);
        Assert.assertEquals("alpha", tagField(rows.get(0)).getText());
        Assert.assertEquals("beta", tagField(rows.get(1)).getText());
        Assert.assertSame(VisUI.getSkin().get("add", Button.ButtonStyle.class), addButton.getStyle());
        Assert.assertSame(addButton, tagsTable.getChildren().peek());
        for (Object row : rows) {
            Button remove = field(row, "removeButton", Button.class);
            Assert.assertSame(VisUI.getSkin().get("delete", Button.ButtonStyle.class), remove.getStyle());
        }
        Assert.assertEquals(0, countTextButtons(dialog, "Add"));
        Assert.assertEquals(0, countTextButtons(dialog, "X"));
        Assert.assertFalse(hasDeclaredField(EditTagsDialog.class, "addField"));
    }

    @Test
    public void addCreatesAnEmptyRowUsesTheFocusContractAndDeleteAllowsZeroRows() throws Exception {
        EditTagsDialog dialog = new EditTagsDialog("Edit Tags", List.of(), null);
        Button addButton = field(dialog, "addButton", Button.class);
        addButton.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());

        Array<?> rows = rows(dialog);
        Assert.assertEquals(1, rows.size);
        Object row = rows.first();
        Assert.assertEquals("", tagField(row).getText());

        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/entityproperties/EditTagsDialog.java"
        ), StandardCharsets.UTF_8);
        Assert.assertTrue(source.contains("Gdx.app.postRunnable(reveal);"));
        Assert.assertTrue(source.contains("setKeyboardFocus(row.tagField)"));
        Assert.assertTrue(source.contains(".minWidth(300)"));

        field(row, "removeButton", Button.class).fire(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        Assert.assertEquals(0, rows(dialog).size);
        Assert.assertFalse(addButton.isDisabled());
    }

    @Test
    public void addDisablesAtSixteenAndReenablesAfterDeletingOneRow() throws Exception {
        java.util.ArrayList<String> sixteen = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) sixteen.add("tag" + i);
        EditTagsDialog dialog = new EditTagsDialog("Edit Tags", sixteen, null);
        Button addButton = field(dialog, "addButton", Button.class);

        Assert.assertEquals(16, rows(dialog).size);
        Assert.assertTrue(addButton.isDisabled());
        addButton.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        Assert.assertEquals(16, rows(dialog).size);

        Object target = rows(dialog).get(7);
        field(target, "removeButton", Button.class).fire(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        Assert.assertEquals(15, rows(dialog).size);
        Assert.assertFalse(addButton.isDisabled());
        Assert.assertEquals("tag8", tagField(rows(dialog).get(7)).getText());
    }

    @Test
    public void emptyAndDuplicateRowsAreInvalidAndDoNotApply() throws Exception {
        AtomicReference<Array<String>> applied = new AtomicReference<>();
        EditTagsDialog dialog = new EditTagsDialog("Edit Tags", List.of("same tag", "sametag"), applied::set);

        Assert.assertNull(invokeValidatedTags(dialog));
        Assert.assertFalse(tagField(rows(dialog).get(0)).isInputValid());
        Assert.assertFalse(tagField(rows(dialog).get(1)).isInputValid());
        invokeResult(dialog, true);
        Assert.assertNull(applied.get());

        EditTagsDialog empty = new EditTagsDialog("Edit Tags", List.of(""), applied::set);
        Assert.assertNull(invokeValidatedTags(empty));
        Assert.assertFalse(tagField(rows(empty).first()).isInputValid());
    }

    @Test
    public void okAppliesNormalizedRowsAndCancelAppliesNothing() throws Exception {
        AtomicReference<Array<String>> applied = new AtomicReference<>();
        EditTagsDialog accepted = new EditTagsDialog(
                "Edit Tags", List.of("  First Tag ", "SECOND"), applied::set);
        invokeResult(accepted, true);

        Assert.assertNotNull(applied.get());
        Assert.assertEquals(List.of("firsttag", "second"), asList(applied.get()));

        AtomicReference<Array<String>> cancelled = new AtomicReference<>();
        EditTagsDialog cancel = new EditTagsDialog("Edit Tags", List.of("kept"), cancelled::set);
        invokeResult(cancel, false);
        Assert.assertNull(cancelled.get());
    }

    private static Array<?> rows(EditTagsDialog dialog) throws Exception {
        return field(dialog, "rows", Array.class);
    }

    private static VisValidatableTextField tagField(Object row) throws Exception {
        return field(row, "tagField", VisValidatableTextField.class);
    }

    private static Array<String> invokeValidatedTags(EditTagsDialog dialog) throws Exception {
        Method method = EditTagsDialog.class.getDeclaredMethod("validatedTags");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Array<String> result = (Array<String>) method.invoke(dialog);
        return result;
    }

    private static void invokeResult(EditTagsDialog dialog, boolean accepted) throws Exception {
        Method method = EditTagsDialog.class.getDeclaredMethod("result", Object.class);
        method.setAccessible(true);
        method.invoke(dialog, accepted);
    }

    private static List<String> asList(Array<String> values) {
        return java.util.stream.IntStream.range(0, values.size)
                .mapToObj(values::get)
                .toList();
    }

    private static boolean hasDeclaredField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private static int countTextButtons(Group root, String text) {
        int count = 0;
        for (Actor child : root.getChildren()) {
            if (child instanceof VisTextButton button && text.contentEquals(button.getText())) count++;
            if (child instanceof Group group) count += countTextButtons(group, text);
        }
        return count;
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
