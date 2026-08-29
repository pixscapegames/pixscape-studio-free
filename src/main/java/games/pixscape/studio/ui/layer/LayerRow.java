package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;

/**
 * Ligne d’un layer : [visible] [locked]  Nom
 */
public class LayerRow extends VisTable {

    public interface Listener {
        void onVisibleChanged(LayerRow row, boolean visible);

        void onLockedChanged(LayerRow row, boolean locked);

        void onRowClicked(LayerRow row);
    }

    private final CheckBox cbVisible;
    private final CheckBox cbLocked;
    private final VisLabel label;

    private Listener listener;
    private int entityId = -1;
    private int layerIndex = -1;

    public LayerRow() {
        super(false);
        pad(2).left();

        cbVisible = new CheckBox("", VisUI.getSkin(), "eye");
        cbLocked = new CheckBox("", VisUI.getSkin(), "padlock");
        label = new VisLabel("");
        label.setColor(Color.WHITE);

        add(cbVisible).padLeft(4).padRight(4);
        add(cbLocked).padRight(15);
        add(label).left().growX();

        cbVisible.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (listener != null) {
                    listener.onVisibleChanged(LayerRow.this, cbVisible.isChecked());
                }
                event.stop();
            }
        });

        cbLocked.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (listener != null) {
                    listener.onLockedChanged(LayerRow.this, cbLocked.isChecked());
                }
                event.stop();
            }
        });

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (listener != null) {
                    listener.onRowClicked(LayerRow.this);
                }
                super.clicked(event, x, y);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setData(int entityId,
                        int layerIndex,
                        String labelText,
                        boolean visible,
                        boolean locked) {
        this.entityId = entityId;
        this.layerIndex = layerIndex;

        label.setText(labelText != null ? labelText : "");
        cbVisible.setChecked(visible);
        cbLocked.setChecked(locked);

        invalidateHierarchy();
    }

    public int getEntityId() {
        return entityId;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    public void setSelected(boolean selected) {
        if (selected) {
            setBackground(VisUI.getSkin().getDrawable("list-selection"));
            label.setColor(Color.CYAN);
        } else {
            setBackground(VisUI.getSkin().getDrawable("default-pane"));
            label.setColor(Color.WHITE);
        }
    }
}
