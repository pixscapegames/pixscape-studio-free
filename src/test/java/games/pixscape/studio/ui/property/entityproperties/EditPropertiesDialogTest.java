package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.Color;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.ColorPickerField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class EditPropertiesDialogTest {

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void appliesADeepWorkingCopyAndPreservesMultilineStringAndClassValues() throws Exception {
        PropertySet source = new PropertySet()
                .putString("title", "  first\nsecond  ")
                .putClass("attack", "Attack", new PropertySet().putInt("damage", 20));
        AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
        EditPropertiesDialog dialog = new EditPropertiesDialog("Edit Properties", source, applied::set);

        Object titleRow = rowNamed(dialog, "title");
        field(titleRow, "stringField", VisTextArea.class).setText("  changed\nvalue  ");
        invokeResult(dialog, true);

        Assert.assertNotNull(applied.get());
        Assert.assertEquals("  first\nsecond  ", source.getString("title", ""));
        Assert.assertEquals("  changed\nvalue  ", applied.get().getString("title", ""));
        Assert.assertEquals(20, applied.get().getClassValue("attack").properties()
                .getInt("damage", 0));
    }

    @Test
    public void invalidAddedRowDoesNotApplyOrMutateTheSource() throws Exception {
        PropertySet source = new PropertySet().putInt("health", 100);
        AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
        EditPropertiesDialog dialog = new EditPropertiesDialog("Edit Properties", source, applied::set);

        field(dialog, "addButton", VisTextButton.class).fire(
                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        invokeResult(dialog, true);

        Assert.assertNull(applied.get());
        Assert.assertEquals(100, source.getInt("health", 0));
        Assert.assertEquals(2, rows(dialog).size);
        Assert.assertTrue(inheritedBooleanField(dialog, "cancelHide"));
    }

    @Test
    public void changingTypeResetsToItsDeterministicDefaultWithoutCoercion() throws Exception {
        AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
        EditPropertiesDialog dialog = new EditPropertiesDialog(
                "Edit Properties", new PropertySet().putString("health", "20"), applied::set);
        Object row = rowNamed(dialog, "health");
        @SuppressWarnings("unchecked")
        VisSelectBox<PropertyType> typeBox = field(row, "typeBox", VisSelectBox.class);
        typeBox.setSelected(PropertyType.INTEGER);
        typeBox.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());

        Assert.assertEquals("0", field(row, "numberField", VisTextField.class).getText());
        invokeResult(dialog, true);
        Assert.assertEquals(0, applied.get().getInt("health", -1));
    }

    @Test
    public void colorRowsKeepTheExactPackedRgba8888ValueInTheWorkingCopy() throws Exception {
        int packed = 0x12345678;
        AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
        EditPropertiesDialog dialog = new EditPropertiesDialog(
                "Edit Properties", new PropertySet().putColorRgba8888("tint", packed), applied::set);

        Object row = rowNamed(dialog, "tint");
        Color localColor = field(row, "colorValue", Color.class);
        Assert.assertEquals(packed, Color.rgba8888(localColor));
        invokeResult(dialog, true);

        Assert.assertEquals(packed, applied.get().getColorRgba8888("tint", 0));
    }

    @Test
    public void changingToColorUsesTransparentBlackByDefault() throws Exception {
        AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
        EditPropertiesDialog dialog = new EditPropertiesDialog(
                "Edit Properties", new PropertySet().putString("tint", "red"), applied::set);
        Object row = rowNamed(dialog, "tint");
        @SuppressWarnings("unchecked")
        VisSelectBox<PropertyType> typeBox = field(row, "typeBox", VisSelectBox.class);
        typeBox.setSelected(PropertyType.COLOR);
        typeBox.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());

        Assert.assertEquals(0, Color.rgba8888(field(row, "colorValue", Color.class)));
        invokeResult(dialog, true);
        Assert.assertEquals(0, applied.get().getColorRgba8888("tint", -1));
    }

    @Test
    public void applyingUnchangedMissingObjectReferencePreservesItsStableId() throws Exception {
        try (ObjectChoiceContext context = new ObjectChoiceContext()) {
            AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
            EditPropertiesDialog dialog = new EditPropertiesDialog(
                    "Edit Properties", new PropertySet().putObjectStableId("target", 123),
                    context.context, applied::set);

            Object choice = selectedObjectChoice(rowNamed(dialog, "target"));
            Assert.assertEquals(123, objectChoiceStableId(choice));
            Assert.assertEquals("Missing entity (#123)", objectChoiceLabel(choice));

            invokeResult(dialog, true);
            Assert.assertEquals(123, applied.get().getObjectStableId("target", -1));
        }
    }

    @Test
    public void applyingMultipleUnchangedMissingObjectReferencesPreservesEachStableId()
            throws Exception {
        try (ObjectChoiceContext context = new ObjectChoiceContext()) {
            AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
            EditPropertiesDialog dialog = new EditPropertiesDialog("Edit Properties",
                    new PropertySet().putObjectStableId("first", 123)
                            .putObjectStableId("second", 456), context.context, applied::set);

            Assert.assertEquals(123, objectChoiceStableId(selectedObjectChoice(rowNamed(dialog, "first"))));
            Assert.assertEquals(456, objectChoiceStableId(selectedObjectChoice(rowNamed(dialog, "second"))));

            invokeResult(dialog, true);
            Assert.assertEquals(123, applied.get().getObjectStableId("first", -1));
            Assert.assertEquals(456, applied.get().getObjectStableId("second", -1));
        }
    }

    @Test
    public void cancelingColorPickerRestoresTheDialogWorkingValueBeforeParentApply()
            throws Exception {
        int original = 0x10203040;
        int changed = 0xA0B0C0D0;
        AtomicReference<PropertySet> applied = new AtomicReference<PropertySet>();
        EditPropertiesDialog dialog = new EditPropertiesDialog(
                "Edit Properties", new PropertySet().putColorRgba8888("tint", original), applied::set);
        Object row = rowNamed(dialog, "tint");
        ColorPickerField colorField = field(row, "colorField", ColorPickerField.class);
        beginPickerSession(colorField);
        ColorPickerListener listener = pickerListener(colorField);

        listener.changed(rgba(changed));
        Assert.assertEquals(changed, Color.rgba8888(field(row, "colorValue", Color.class)));
        listener.canceled(rgba(original));
        Assert.assertEquals(original, Color.rgba8888(field(row, "colorValue", Color.class)));

        invokeResult(dialog, true);
        Assert.assertEquals(original, applied.get().getColorRgba8888("tint", 0));
    }

    private static Object rowNamed(EditPropertiesDialog dialog, String name) throws Exception {
        Array<?> rows = rows(dialog);
        for (int i = 0; i < rows.size; i++) {
            Object row = rows.get(i);
            if (name.equals(field(row, "nameField", com.kotcrab.vis.ui.widget.VisTextField.class).getText())) {
                return row;
            }
        }
        Assert.fail("Missing row " + name);
        return null;
    }

    private static Array<?> rows(EditPropertiesDialog dialog) throws Exception {
        return field(dialog, "rows", Array.class);
    }

    private static void invokeResult(EditPropertiesDialog dialog, boolean accepted) throws Exception {
        Method method = EditPropertiesDialog.class.getDeclaredMethod("result", Object.class);
        method.setAccessible(true);
        method.invoke(dialog, accepted);
    }

    private static ColorPickerListener pickerListener(ColorPickerField field) throws Exception {
        Method method = ColorPickerField.class.getDeclaredMethod("createPickerListener");
        method.setAccessible(true);
        return (ColorPickerListener) method.invoke(field);
    }

    private static void beginPickerSession(ColorPickerField field) throws Exception {
        Method method = ColorPickerField.class.getDeclaredMethod("captureColorBeforePicker");
        method.setAccessible(true);
        method.invoke(field);
    }

    private static Color rgba(int packed) {
        Color color = new Color();
        Color.rgba8888ToColor(color, packed);
        return color;
    }

    @SuppressWarnings("unchecked")
    private static Object selectedObjectChoice(Object row) throws Exception {
        return field(row, "objectBox", VisSelectBox.class).getSelected();
    }

    private static int objectChoiceStableId(Object choice) throws Exception {
        Field field = choice.getClass().getDeclaredField("stableId");
        field.setAccessible(true);
        return field.getInt(choice);
    }

    private static String objectChoiceLabel(Object choice) throws Exception {
        return field(choice, "label", String.class);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static boolean inheritedBooleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getSuperclass().getSuperclass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static final class ObjectChoiceContext implements AutoCloseable {
        private final World world = new World(new WorldConfiguration());
        private final IdentityRegistry identities = new IdentityRegistry();
        private final EntityPropertiesContext context;

        private ObjectChoiceContext() {
            SceneMeta sceneMeta = new SceneMeta();
            identities.bind(world, sceneMeta);
            HistoryManager history = new HistoryManager(8);
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            context = new EntityPropertiesContext(
                    world,
                    history,
                    new PhysicsSelectionService(),
                    new PhysicsService(world, null, sceneMeta),
                    layers,
                    new AtlasStudioService(null),
                    new SelectionService(world, layers),
                    identities,
                    new IconResolver(world),
                    () -> { },
                    id -> null,
                    id -> { },
                    Array::new,
                    new AnimationAssetAuthoringService(
                            AssetMetaDatabase::new,
                            () -> new FileHandle("unused-assets.json"),
                            ignored -> { }),
                    0);
        }

        @Override
        public void close() {
            identities.bind(null, null);
            world.dispose();
        }
    }
}
