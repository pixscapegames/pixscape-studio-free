package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

public class ListActionControlsTest {

    @BeforeClass
    public static void loadStudioSkin() {
        VisUiTestBootstrap.loadSkin();
        VisUI.dispose();
        VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
    }

    @AfterClass
    public static void unloadStudioSkin() {
        VisUI.dispose();
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void animationClipControlsUseImageStylesAndDeleteTheTargetRow() throws Exception {
        AnimationAssetMeta animation = new AnimationAssetMeta(
                7, "animations/test", "orig/animations/test", AssetMeta.AssetScope.USER);
        animation.currentClip = "default";
        animation.clips.put("default", new AnimationClipMeta(0, 12));
        AnimationClipsDialog dialog = new AnimationClipsDialog(animation, null, 12);
        Button addButton = field(dialog, "addButton", Button.class);
        Array<?> rows = field(dialog, "rows", Array.class);

        assertStyle(addButton, "add");
        Assert.assertTrue(addButton.isDescendantOf(field(dialog, "listTable", Group.class)));
        Assert.assertEquals(0, countTextButtons(dialog, "Add clip"));
        Assert.assertEquals(1, rows.size);

        addButton.fire(new ChangeEvent());
        Assert.assertEquals(2, rows.size);
        Object first = rows.get(0);
        Object second = rows.get(1);
        Button firstDelete = field(first, "removeButton", Button.class);
        assertStyle(firstDelete, "delete");
        Assert.assertFalse(firstDelete instanceof VisTextButton);

        firstDelete.fire(new ChangeEvent());
        Assert.assertEquals(1, rows.size);
        Assert.assertSame(second, rows.first());

        Button secondDelete = field(second, "removeButton", Button.class);
        secondDelete.fire(new ChangeEvent());
        Assert.assertEquals(1, rows.size);
        Object fallback = rows.first();
        Assert.assertNotSame(second, fallback);
        Assert.assertEquals("default", field(fallback, "nameField", VisTextField.class).getText());
        Assert.assertEquals(0, countTextButtons(dialog, "X"));
    }

    @Test
    public void shaderParameterControlsAllowAddingAndDeletingToZeroRows() throws Exception {
        World world = new World(new WorldConfiguration());
        try {
            int entityId = world.create();
            ShaderParamsDialog dialog = new ShaderParamsDialog(world, entityId, "missing-test-shader");
            Button addButton = field(dialog, "addButton", Button.class);
            Array<?> rows = field(dialog, "rows", Array.class);

            assertStyle(addButton, "add");
            Assert.assertTrue(addButton.isDescendantOf(field(dialog, "paramsTable", Group.class)));
            Assert.assertEquals(0, countTextButtons(dialog, "Add parameter"));
            Assert.assertEquals(0, rows.size);

            addButton.fire(new ChangeEvent());
            Assert.assertEquals(1, rows.size);
            Object row = rows.first();
            Assert.assertEquals("", field(row, "nameField", VisTextField.class).getText());
            Assert.assertEquals("0.0", field(row, "valueField", VisTextField.class).getText());

            Button deleteButton = field(row, "removeButton", Button.class);
            assertStyle(deleteButton, "delete");
            Assert.assertFalse(deleteButton instanceof VisTextButton);
            deleteButton.fire(new ChangeEvent());

            Assert.assertEquals(0, rows.size);
            Assert.assertEquals(0, countTextButtons(dialog, "X"));
        } finally {
            world.dispose();
        }
    }

    private static void assertStyle(Button button, String styleName) {
        Assert.assertSame(VisUI.getSkin().get(styleName, ButtonStyle.class), button.getStyle());
    }

    private static int countTextButtons(Actor actor, String text) {
        int count = actor instanceof VisTextButton
                && text.contentEquals(((VisTextButton) actor).getText()) ? 1 : 0;
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                count += countTextButtons(child, text);
            }
        }
        return count;
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }
}
