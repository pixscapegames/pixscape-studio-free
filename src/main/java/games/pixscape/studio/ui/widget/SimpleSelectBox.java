package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisSelectBox;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Simple SelectBox (non-ECS) with reader/applier binding and refresh.
 */
public final class SimpleSelectBox<T> extends VisSelectBox<T> {

    private Supplier<T> reader;
    private Consumer<T> applier;

    private boolean internalUpdate = false;
    private T lastSyncedValue;

    public SimpleSelectBox() {
        super();
        addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalUpdate) return;

                T uiValue = getSelected();

                if (applier != null) {
                    applier.accept(uiValue);
                }

                syncFromSourceOr(uiValue);
            }
        });
    }

    public SimpleSelectBox<T> bind(Supplier<T> reader, Consumer<T> applier) {
        this.reader = reader;
        this.applier = applier;
        refresh();
        return this;
    }

    public void refresh() {
        T value = readCurrentValue();
        if (value != null || reader != null) {
            lastSyncedValue = value;
            setProgrammaticSelection(value);
            return;
        }

        if (lastSyncedValue != null) {
            setProgrammaticSelection(lastSyncedValue);
        }
    }

    private void syncFromSourceOr(T fallbackValue) {
        if (reader != null) {
            T sourceValue = reader.get();
            lastSyncedValue = sourceValue;
            setProgrammaticSelection(sourceValue);
            return;
        }

        lastSyncedValue = fallbackValue;
        setProgrammaticSelection(fallbackValue);
    }

    private T readCurrentValue() {
        return reader != null ? reader.get() : null;
    }

    private void setProgrammaticSelection(T value) {
        internalUpdate = true;
        try {
            setSelected(value);
        } finally {
            internalUpdate = false;
        }
    }
}