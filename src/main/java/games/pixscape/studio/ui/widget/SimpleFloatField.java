package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.kotcrab.vis.ui.widget.VisTextField;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Float field not bound to ECS (useful for SceneMeta, prefs, etc.).
 * - Commit with ENTER
 * - Float filter
 * - bind(reader, applier)
 * - after commit, the display is resynchronized from the source if it exists
 */
public final class SimpleFloatField extends VisTextField {

    private Supplier<Float> reader;
    private Consumer<Float> applier;
    private Predicate<Float> commitValidator;

    private VisTextField.TextFieldFilter activeFilter;
    private boolean internalUpdate = false;

    private Float lastValidValue;
    private boolean exactText;

    public SimpleFloatField() {
        super("");

        setTextFieldFilter((tf, c) ->
                (c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-'
        );

        addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    commit();
                    if (getStage() != null) {
                        getStage().setKeyboardFocus(null);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void setTextFieldFilter(VisTextField.TextFieldFilter filter) {
        this.activeFilter = filter;
        super.setTextFieldFilter(filter);
    }

    public SimpleFloatField bind(Supplier<Float> reader, Consumer<Float> applier) {
        this.reader = reader;
        this.applier = applier;
        refresh();
        return this;
    }

    public SimpleFloatField validateCommitWith(Predicate<Float> validator) {
        this.commitValidator = validator;
        return this;
    }

    public SimpleFloatField useExactText() {
        exactText = true;
        refresh();
        return this;
    }

    public void refresh() {
        Float value = readCurrentValue();
        if (value == null) {
            if (lastValidValue == null) {
                setProgrammaticText("");
            } else {
                setProgrammaticText(floatToText(lastValidValue));
            }
            return;
        }

        lastValidValue = value;
        setProgrammaticText(floatToText(value));
    }

    public void commit() {
        if (internalUpdate) return;

        Float parsed = parse(getText());
        if (parsed == null
                || (commitValidator != null && !commitValidator.test(parsed))) {
            rollbackToKnownValue();
            return;
        }

        Float before = readCurrentValueOrLastValid();
        if (before != null && Float.compare(before, parsed) == 0) {
            syncFromSourceOrParsed(parsed);
            return;
        }

        if (applier != null) {
            applier.accept(parsed);
        }

        syncFromSourceOrParsed(parsed);
    }

    private void rollbackToKnownValue() {
        Float value = readCurrentValueOrLastValid();
        if (value == null) {
            setProgrammaticText("");
            return;
        }

        lastValidValue = value;
        setProgrammaticText(floatToText(value));
    }

    private void syncFromSourceOrParsed(Float fallbackValue) {
        Float current = readCurrentValue();
        if (current != null) {
            lastValidValue = current;
            setProgrammaticText(floatToText(current));
            return;
        }

        if (fallbackValue != null) {
            lastValidValue = fallbackValue;
            setProgrammaticText(floatToText(fallbackValue));
            return;
        }

        if (lastValidValue != null) {
            setProgrammaticText(floatToText(lastValidValue));
        } else {
            setProgrammaticText("");
        }
    }

    private Float readCurrentValue() {
        if (reader == null) return null;
        return reader.get();
    }

    private Float readCurrentValueOrLastValid() {
        Float current = readCurrentValue();
        return current != null ? current : lastValidValue;
    }

    private void setProgrammaticText(String value) {
        internalUpdate = true;
        try {
            setTextSafely(value == null ? "" : value);
            setCursorPosition(getText().length());
        } finally {
            internalUpdate = false;
        }
    }

    private void setTextSafely(String value) {
        VisTextField.TextFieldFilter filterToRestore = activeFilter;
        super.setTextFieldFilter(null);
        try {
            setText(value);
        } finally {
            super.setTextFieldFilter(filterToRestore);
        }
    }

    private static Float parse(String t) {
        if (t == null) return null;

        String s = t.trim();
        if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "-.".equals(s)) {
            return null;
        }

        s = s.replace(',', '.');

        try {
            float v = Float.parseFloat(s);
            return Float.isFinite(v) ? v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String floatToText(float v) {
        return exactText ? Float.toString(v) : String.format(Locale.ROOT, "%.2f", v);
    }
}
