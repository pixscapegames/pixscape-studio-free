package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;

import java.util.function.Consumer;

public final class ResponsiveGalleryLayoutStrategy implements ThumbsLayoutStrategy {

    @Override
    public void configureScrollPane(VisScrollPane scroll) {
        if (scroll == null) return;

        scroll.setScrollingDisabled(true, false);
        scroll.setForceScroll(false, false);
        scroll.setClamp(true);
        scroll.setFlickScroll(false);
    }

    @Override
    public void rebuildGrid(VisTable grid,
                            VisTable content,
                            Array<AssetNode> assets,
                            float availableWidth,
                            float tileSize,
                            float tilePad,
                            Consumer<AssetNode> thumbAdder) {
        if (grid == null || content == null || assets == null || thumbAdder == null) return;
        if (availableWidth <= 0f) return;

        grid.clear();

        int perRow = Math.max(1,
                (int) (availableWidth / (tileSize + tilePad * 2f)) - 1
        );

        int count = 0;
        for (AssetNode asset : assets) {
            thumbAdder.accept(asset);
            count++;
            if (count % perRow == 0) {
                grid.row();
            }
        }

        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }
}