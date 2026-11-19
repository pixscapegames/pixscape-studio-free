package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.studio.ui.config.CommonLayout;

/** Read-only grid configuration owned by the map, shared by its tile layers. */
final class TiledGridPanel extends VisTable {
    private final VisLabel projection = value("tiledGridProjection");
    private final VisLabel cellWidth = value("tiledGridCellWidth");
    private final VisLabel cellHeight = value("tiledGridCellHeight");
    private final VisLabel width = value("tiledGridWidth");
    private final VisLabel height = value("tiledGridHeight");

    TiledGridPanel() {
        super(true);
        left().top();
        defaults().left().pad(1);
        row("Projection:", projection);
        row("Cell Width:", cellWidth);
        row("Cell Height:", cellHeight);
        row("Width (cells):", width);
        row("Height (cells):", height);
    }

    void refresh(TiledMapComponent map) {
        projection.setText(map.projection == null ? "Unknown" : switch (map.projection) {
            case ISO -> "Isometric";
            case ORTHO -> "Orthogonal";
        });
        cellWidth.setText(Integer.toString(map.tileWidth));
        cellHeight.setText(Integer.toString(map.tileHeight));
        width.setText(Integer.toString(map.width));
        height.setText(Integer.toString(map.height));
    }

    private static VisLabel value(String name) {
        VisLabel label = new VisLabel();
        label.setName(name);
        return label;
    }

    private void row(String title, VisLabel value) {
        add(new VisLabel(title)).width(CommonLayout.LABEL_WIDTH);
        add(value).growX().row();
    }
}
