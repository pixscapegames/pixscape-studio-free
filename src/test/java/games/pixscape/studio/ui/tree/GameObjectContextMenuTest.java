package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GameObjectContextMenuTest {
    private static final ItemTreePanel.GameObjectChildMenuActions NO_OP_ACTIONS =
            new ItemTreePanel.GameObjectChildMenuActions() {
                @Override public void addSprite() { }
                @Override public void addAnimation() { }
                @Override public void addPointLight() { }
                @Override public void addConeLight() { }
                @Override public void addGameObject() { }
            };

    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void noSelectedAssetDisablesSpriteAndAnimationOnly() {
        PopupMenu menu = ItemTreePanel.buildGameObjectAddMenu(null, NO_OP_ACTIONS);

        assertTrue(item(menu, "Sprite").isDisabled());
        assertTrue(item(menu, "Animation").isDisabled());
        assertFalse(item(menu, "Light").isDisabled());
        assertFalse(item(menu, "Game Object").isDisabled());
    }

    @Test
    public void reusesLayerAssetRulesAndExposesOnlySupportedChildTypes() {
        AssetNode image = new AssetNode(
                AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES, "sprite.png", "Sprite", null);
        PopupMenu imageMenu = ItemTreePanel.buildGameObjectAddMenu(image, NO_OP_ACTIONS);
        assertFalse(item(imageMenu, "Sprite").isDisabled());
        assertTrue(item(imageMenu, "Animation").isDisabled());

        AssetNode animation = new AssetNode(
                AssetNode.Kind.ANIMATION, AssetNode.Root.ANIMATIONS, "walk", "Walk", null);
        PopupMenu animationMenu = ItemTreePanel.buildGameObjectAddMenu(animation, NO_OP_ACTIONS);
        assertTrue(item(animationMenu, "Sprite").isDisabled());
        assertFalse(item(animationMenu, "Animation").isDisabled());

        assertEquals(4, animationMenu.getChildren().size);
        MenuItem light = item(animationMenu, "Light");
        assertNotNull(light.getSubMenu());
        assertEquals(2, light.getSubMenu().getChildren().size);
        assertNotNull(item(light.getSubMenu(), "Point Light"));
        assertNotNull(item(light.getSubMenu(), "Cone Light"));
        assertNull(find(animationMenu, "Tiled Map"));
        assertNull(find(animationMenu, "Particle"));
    }

    @Test
    public void supportedMenuItemsDispatchTheirExactChildCreationAction() {
        int[] calls = new int[5];
        ItemTreePanel.GameObjectChildMenuActions actions =
                new ItemTreePanel.GameObjectChildMenuActions() {
                    @Override public void addSprite() { calls[0]++; }
                    @Override public void addAnimation() { calls[1]++; }
                    @Override public void addPointLight() { calls[2]++; }
                    @Override public void addConeLight() { calls[3]++; }
                    @Override public void addGameObject() { calls[4]++; }
                };

        PopupMenu imageMenu = ItemTreePanel.buildGameObjectAddMenu(
                new AssetNode(AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES,
                        "sprite.png", "Sprite", null),
                actions);
        fireClick(item(imageMenu, "Sprite"));

        PopupMenu animationMenu = ItemTreePanel.buildGameObjectAddMenu(
                new AssetNode(AssetNode.Kind.ANIMATION, AssetNode.Root.ANIMATIONS,
                        "walk", "Walk", null),
                actions);
        fireClick(item(animationMenu, "Animation"));
        MenuItem light = item(animationMenu, "Light");
        fireClick(item(light.getSubMenu(), "Point Light"));
        fireClick(item(light.getSubMenu(), "Cone Light"));
        fireClick(item(animationMenu, "Game Object"));

        for (int call : calls) assertEquals(1, call);
    }

    private static MenuItem item(PopupMenu menu, String text) {
        MenuItem item = find(menu, text);
        assertNotNull("Missing menu item: " + text, item);
        return item;
    }

    private static MenuItem find(PopupMenu menu, String text) {
        for (Actor child : menu.getChildren()) {
            if (child instanceof MenuItem item && text.contentEquals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static void fireClick(MenuItem item) {
        InputEvent event = new InputEvent();
        for (EventListener listener : item.getListeners()) {
            if (listener instanceof ClickListener click) {
                click.clicked(event, 0f, 0f);
            }
        }
    }
}
