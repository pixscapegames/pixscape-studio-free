package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

/** One explicit Tiled Map child row in the Layers panel. */
final class TiledMapRow extends VisTable {
    interface Listener {
        void onMapSelected(int mapEntityId);
        void onMapDeleteRequested(int mapEntityId);
    }

    private final VisLabel label = new VisLabel("");
    private final VisTextButton deleteButton = new VisTextButton("Delete Tiled Map");
    private int mapEntityId = -1;

    TiledMapRow() {
        super(false);
        pad(2).left();
        add(new VisLabel("↳")).padLeft(22).padRight(6);
        add(label).left().growX();
        add(deleteButton).right();
    }

    void setData(int entityId, String text, boolean selected, Listener listener) {
        mapEntityId = entityId;
        label.setText(text);
        setBackground(VisUI.getSkin().getDrawable(selected ? "list-selection" : "default-pane"));
        clearListeners();
        addListener(new ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                listener.onMapSelected(mapEntityId);
            }
        });
        deleteButton.clearListeners();
        deleteButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                listener.onMapDeleteRequested(mapEntityId);
                event.stop();
            }
        });
    }
}
