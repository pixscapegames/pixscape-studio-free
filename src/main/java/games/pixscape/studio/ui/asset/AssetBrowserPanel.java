package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;

public final class AssetBrowserPanel extends VisTable {

    private static final float DEFAULT_TREE_SPLIT = 0.25f;

    private final String title;
    private final Actor treeView;
    private final Actor thumbsView;

    public AssetBrowserPanel(Actor treeView, Actor thumbsView) {
        this(null, treeView, thumbsView);
    }

    public AssetBrowserPanel(String title, Actor treeView, Actor thumbsView) {
        this.title = title;
        this.treeView = treeView;
        this.thumbsView = thumbsView;

        build();
    }

    private void build() {
        clear();

        VisTable treeColumn = new VisTable();
        treeColumn.top().left();
        if (title != null && !title.isBlank()) {
            VisTable titleBar = new VisTable();
            titleBar.setBackground(
                    VisUI.getSkin().getDrawable("panel-header")
            );

            titleBar.add(new VisLabel(title, "title"))
                    .center()
                    .padLeft(4f)
                    .expandX();

            treeColumn.add(titleBar)
                    .growX()
                    .row();
        }
        treeColumn.add(treeView).grow();

        VisSplitPane mainSplit = new VisSplitPane(
                treeColumn,
                thumbsView,
                false // horizontal
        );
        mainSplit.setSplitAmount(DEFAULT_TREE_SPLIT);

        add(mainSplit).grow();
    }

    public Actor getTreeView() {
        return treeView;
    }

    public Actor getThumbsView() {
        return thumbsView;
    }
}
