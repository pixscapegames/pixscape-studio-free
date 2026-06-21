package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTree;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityCategory;

import java.util.function.Consumer;

public final class RuntimeAvailabilityTreeView extends VisTable {

    private final VisTree tree = new VisTree();
    private Consumer<RuntimeAvailabilityCategory> selectionListener;
    private boolean internalSelectionChange = false;

    public RuntimeAvailabilityTreeView() {
        top().left();

        tree.setIndentSpacing(22f);
        tree.getSelection().setMultiple(false);

        VisScrollPane scroller = new VisScrollPane(tree);
        scroller.setFadeScrollBars(false);
        scroller.setScrollingDisabled(true, false);
        add(scroller).padLeft(10).grow();

        buildTree();
        hookSelection();
        selectCategory(RuntimeAvailabilityCategory.SPRITES);
    }

    public void setSelectionListener(Consumer<RuntimeAvailabilityCategory> selectionListener) {
        this.selectionListener = selectionListener;
    }

    public RuntimeAvailabilityCategory getSelectedCategory() {
        VisTree.Node node = (VisTree.Node) tree.getSelection().first();
        if (node == null || node.getActor() == null) return RuntimeAvailabilityCategory.SPRITES;

        Object userObject = node.getActor().getUserObject();
        return userObject instanceof RuntimeAvailabilityCategory category
                ? category
                : RuntimeAvailabilityCategory.SPRITES;
    }

    private void buildTree() {
        tree.clearChildren();

        tree.add(folderNode("Sprites", RuntimeAvailabilityCategory.SPRITES));
        tree.add(folderNode("Animations", RuntimeAvailabilityCategory.ANIMATIONS));
        tree.add(folderNode("Particles", RuntimeAvailabilityCategory.PARTICLES));
        tree.add(folderNode("Prefabs", RuntimeAvailabilityCategory.PREFABS));

        VisTree.Node tiled = folderNode("Tiled", null);
        tiled.setSelectable(false);
        tiled.add(folderNode("Tiles", RuntimeAvailabilityCategory.TILED_TILES));
        tiled.add(folderNode("Animations", RuntimeAvailabilityCategory.TILED_ANIMATIONS));
        tree.add(tiled);

        tiled.expandAll();
    }

    private VisTree.Node folderNode(String label, RuntimeAvailabilityCategory category) {
        VisLabel actor = new VisLabel(label);
        actor.setUserObject(category);
        return new VisTree.Node(actor) {
        };
    }

    private void hookSelection() {
        tree.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalSelectionChange || selectionListener == null) {
                    return;
                }
                selectionListener.accept(getSelectedCategory());
            }
        });
    }

    public boolean selectCategory(RuntimeAvailabilityCategory category) {
        if (category == null) return false;
        return selectRecursive(tree.getRootNodes(), category);
    }

    private boolean selectRecursive(Array<VisTree.Node> nodes, RuntimeAvailabilityCategory category) {
        if (category == null) return false;
        for (VisTree.Node node : nodes) {
            if (node.getActor() != null && node.getActor().getUserObject() == category) {
                internalSelectionChange = true;
                try {
                    tree.getSelection().set(node);
                } finally {
                    internalSelectionChange = false;
                }
                if (selectionListener != null) {
                    selectionListener.accept(category);
                }
                return true;
            }

            if (selectRecursive(node.getChildren(), category)) {
                return true;
            }
        }
        return false;
    }
}
