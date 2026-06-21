package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;

import java.util.function.Consumer;

public record FixedTilesetLayoutStrategy(int columns) implements ThumbsLayoutStrategy {

    public FixedTilesetLayoutStrategy(int columns) {
        this.columns = Math.max(1, columns);
    }

    @Override
    public void configureScrollPane(VisScrollPane scroll) {
        if (scroll == null) return;

        scroll.setScrollingDisabled(false, false);
        scroll.setFadeScrollBars(true);
//        scroll.setForceScroll(false, true);
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

        float cellWidth = tileSize + tilePad * 2f;
        float contentWidth = columns * cellWidth;

        grid.defaults().pad(tilePad).top().left();
        grid.setWidth(contentWidth);

        int count = 0;
        for (AssetNode asset : assets) {
            thumbAdder.accept(asset);
            count++;
            if (count % columns == 0) {
                grid.row();
            }
        }

        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }
}