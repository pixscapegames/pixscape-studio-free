package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisSlider;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Simple float slider (non-ECS) with reader/applier binding and refresh.
 * Contrat :
 * - l'utilisateur manipule la valeur UI ;
 * - the applier receives this value ;
 * - then the display is resynchronized from the source if it exists.
 */
public final class SimpleFloatSlider extends VisSlider {

    private Supplier<Float> reader;
    private Consumer<Float> applier;

    private boolean internalUpdate = false;
    private Float lastSyncedValue;

    public SimpleFloatSlider(float min, float max, float stepSize) {
        super(min, max, stepSize, false);

        addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalUpdate) return;

                float uiValue = getValue();

                if (applier != null) {
                    applier.accept(uiValue);
                }

                syncFromSourceOr(uiValue);
            }
        });
    }

    public SimpleFloatSlider bind(Supplier<Float> reader, Consumer<Float> applier) {
        this.reader = reader;
        this.applier = applier;
        refresh();
        return this;
    }

    public void refresh() {
        Float value = readCurrentValue();
        if (value == null) {
            if (lastSyncedValue != null) {
                setProgrammaticValue(lastSyncedValue);
            }
            return;
        }

        lastSyncedValue = value;
        setProgrammaticValue(value);
    }

    private void syncFromSourceOr(float fallbackValue) {
        Float sourceValue = readCurrentValue();
        if (sourceValue != null) {
            lastSyncedValue = sourceValue;
            setProgrammaticValue(sourceValue);
            return;
        }

        lastSyncedValue = fallbackValue;
        setProgrammaticValue(fallbackValue);
    }

    private Float readCurrentValue() {
        return reader != null ? reader.get() : null;
    }

    private void setProgrammaticValue(float value) {
        internalUpdate = true;
        try {
            setValue(value);
        } finally {
            internalUpdate = false;
        }
    }
}