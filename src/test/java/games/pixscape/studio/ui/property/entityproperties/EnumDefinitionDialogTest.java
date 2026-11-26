package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.property.EnumDefinition;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class EnumDefinitionDialogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void invalidDefinitionsStayOpenUntilCorrected() throws Exception {
        String[][] invalid = {{"", "Idle", "name"}, {"Movement", "", "at least one"},
                {"Movement", "Idle\n Idle ", "Duplicate"}, {"Movement", "Idle\n\nRunning", "line 2"}};
        var stage = stage();
        try {
            for (String[] example : invalid) {
                AtomicReference<EnumDefinition> selected = new AtomicReference<>();
                var dialog = new EnumDefinitionDialog(selected::set);
                stage.addActor(dialog);
                field(dialog, "nameField", VisTextField.class).setText(example[0]);
                field(dialog, "valuesField", VisTextArea.class).setText(example[1]);
                click(dialog, "Create");
                settle(stage);
                assertNull(selected.get());
                assertSame(stage, dialog.getStage());
                assertTrue(field(dialog, "validationLabel", VisLabel.class).getText().toString().contains(example[2]));
                field(dialog, "nameField", VisTextField.class).setText(" Movement ");
                field(dialog, "valuesField", VisTextArea.class).setText(" Idle\r\nWalking slowly\r\nRunning\r\n");
                click(dialog, "Create");
                settle(stage);
                assertNull(dialog.getStage());
                assertEquals("Movement", selected.get().name());
                assertEquals(PropertyType.STRING, selected.get().storageType());
                assertFalse(selected.get().isFlags());
                assertEquals("Walking slowly", selected.get().choiceAt(1));
            }
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void optionalTiledImportPreservesStorageFlagsAndChoiceOrder() throws Exception {
        var stage = stage();
        try {
            for (String storage : new String[]{"string", "int"}) {
                FileHandle file = new FileHandle(temporary.newFile(storage + ".tiled-project"));
                file.writeString("""
                        {"propertyTypes":[
                          {"id":1,"name":"Other","type":"enum","storageType":"string","values":["Only"]},
                          {"id":2,"name":"Flags","type":"enum","storageType":"%s","valuesAsFlags":true,
                           "values":["Z","A"]}]}
                        """.formatted(storage), false, "UTF-8");
                AtomicReference<EnumDefinition> selected = new AtomicReference<>();
                var dialog = new EnumDefinitionDialog(selected::set);
                stage.addActor(dialog);
                importFile(dialog, file);
                var choices = (VisDialog) stage.getActors().peek();
                ((VisSelectBox<?>) choices.getContentTable().getChildren().first()).setSelectedIndex(1);
                click(choices, "OK");
                settle(stage);
                assertNull(dialog.getStage());
                assertEquals("Flags", selected.get().name());
                assertEquals(storage.equals("int") ? PropertyType.INTEGER : PropertyType.STRING, selected.get().storageType());
                assertTrue(selected.get().isFlags());
                assertEquals("Z", selected.get().choiceAt(0));
                assertEquals("A", selected.get().choiceAt(1));
            }
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void invalidImportLeavesTheDraftAvailableForLocalCreation() throws Exception {
        var stage = stage();
        try {
            AtomicReference<EnumDefinition> selected = new AtomicReference<>();
            var dialog = new EnumDefinitionDialog(selected::set);
            stage.addActor(dialog);
            field(dialog, "nameField", VisTextField.class).setText("Movement");
            field(dialog, "valuesField", VisTextArea.class).setText("Idle\nRunning");
            FileHandle file = new FileHandle(temporary.newFile("empty.tiled-project"));
            file.writeString("{\"propertyTypes\":[]}", false, "UTF-8");
            importFile(dialog, file);
            assertNull(selected.get());
            assertTrue(field(dialog, "validationLabel", VisLabel.class).getText().toString().contains("no enums"));
            click(dialog, "Create");
            assertEquals("Movement", selected.get().name());
        } finally {
            stage.dispose();
        }
    }

    private static void importFile(EnumDefinitionDialog dialog, FileHandle file) throws Exception {
        var method = EnumDefinitionDialog.class.getDeclaredMethod("importDefinitions", FileHandle.class);
        method.setAccessible(true);
        method.invoke(dialog, file);
    }

    private static Stage stage() {
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class[]{Batch.class},
                (proxy, method, args) -> null);
        return new Stage(new ScreenViewport(), batch);
    }

    private static void settle(Stage stage) {
        // VisDialog.hide sequences the fade, listener removal and actor removal.
        for (int i = 0; i < 6; i++) {
            stage.act(1f);
        }
    }

    private static void click(VisDialog dialog, String label) {
        for (var actor : dialog.getButtonsTable().getChildren()) {
            if (actor instanceof VisTextButton button && label.contentEquals(button.getText())) {
                button.fire(new ChangeListener.ChangeEvent());
                return;
            }
        }
        throw new AssertionError("Missing button " + label);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
