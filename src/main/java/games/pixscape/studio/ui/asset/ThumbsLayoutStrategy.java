package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;

import java.util.function.Consumer;

public interface ThumbsLayoutStrategy {

    void configureScrollPane(VisScrollPane scroll);

    void rebuildGrid(VisTable grid,
                     VisTable content,
                     Array<AssetNode> assets,
                     float availableWidth,
                     float tileSize,
                     float tilePad,
                     Consumer<AssetNode> thumbAdder);
}