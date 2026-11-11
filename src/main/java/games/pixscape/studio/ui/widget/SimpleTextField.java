package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.kotcrab.vis.ui.widget.VisTextField;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Simple text field (non-ECS) with reader/applier binding and ENTER commit.
 */
public final class SimpleTextField extends VisTextField implements TextInputWidget {

    private Supplier<String> reader;
    private Consumer<String> applier;

    private String lastSyncedValue = "";
    private boolean internalUpdate = false;
    private boolean committing;
    private BooleanSupplier commitAllowed = () -> true;
    private boolean focusCommitInstalled;

    private VisTextField.TextFieldFilter activeFilter;

    public SimpleTextField() {
        super("");
        addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    if (applier == null) return false;
                    commit();
                    if (getStage() != null) getStage().setKeyboardFocus(null);
                    return true;
                }
                return false;
            }
        });
    }

    public void setTextFieldFilter(VisTextField.TextFieldFilter filter) {
        this.activeFilter = filter;
        super.setTextFieldFilter(filter);
    }

    public SimpleTextField bind(Supplier<String> reader, Consumer<String> applier) {
        this.reader = reader;
        this.applier = applier;
        refresh();
        return this;
    }

    /** Opt-in non-ECS binding: Tab uses VisUI's normal focus traversal. */
    public SimpleTextField onFocusLostCommit(BooleanSupplier commitAllowed) {
        this.commitAllowed = Objects.requireNonNull(commitAllowed);
        if (!focusCommitInstalled) {
            ValidationHooks.installFocusLostValidation(this, this::commit);
            focusCommitInstalled = true;
        }
        return this;
    }

    /** Call before removing/replacing authored controls, so teardown is not a user commit. */
    public void unbind() {
        reader = null;
        applier = null;
        commitAllowed = () -> false;
    }

    public void refresh() {
        String value = readCurrentValue();
        if (value == null) value = lastSyncedValue != null ? lastSyncedValue : "";
        lastSyncedValue = value;
        setProgrammaticText(value);
    }

    public void commit() {
        if (internalUpdate || committing || !commitAllowed.getAsBoolean()) return;

        String value = currentText();

        if (applier == null) {
            rollback();
            return;
        }

        String before = readCurrentValueOrLastSynced();
        if (Objects.equals(before, value)) {
            syncFromSourceOr(value);
            return;
        }

        committing = true;
        try {
            applier.accept(value);
            syncFromSourceOr(value);
        } finally {
            committing = false;
        }
    }

    public void rollback() {
        String value = readCurrentValueOrLastSynced();
        setProgrammaticText(value == null ? "" : value);
    }

    private void syncFromSourceOr(String fallbackValue) {
        String current = readCurrentValue();
        if (current != null) {
            lastSyncedValue = current;
            setProgrammaticText(current);
            return;
        }

        lastSyncedValue = fallbackValue != null ? fallbackValue : "";
        setProgrammaticText(lastSyncedValue);
    }

    private String readCurrentValue() {
        return reader != null ? reader.get() : null;
    }

    private String readCurrentValueOrLastSynced() {
        String current = readCurrentValue();
        return current != null ? current : lastSyncedValue;
    }

    private String currentText() {
        String value = getText();
        return value != null ? value : "";
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
}
