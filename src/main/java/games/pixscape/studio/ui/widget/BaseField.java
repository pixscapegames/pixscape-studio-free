package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.util.InputValidator;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;

import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Rebindable VisUI text field (setEntityId), commit with ENTER, APPLY-ONLY (no history).
 * Contrat :
 * - reader(entityId): reads the source of truth;
 * - applier(entityId, value): applique la nouvelle valeur ;
 * - after commit, the display is always resynchronized from the model;
 * - all programmatic text updates go through setProgrammaticText(...).
 */
public abstract class BaseField<T> extends VisValidatableTextField implements TextInputWidget {

    @FunctionalInterface
    public interface HistoryApplier<U> {
        void apply(int entityId, U value);
    }

    protected final World world;

    private final IntFunction<T> reader;
    private final IntPredicate isApplicable;

    private HistoryApplier<T> applier;

    private int entityId = -1;
    private boolean internalUpdate = false;
    private boolean trimOnCommit = true;

    private Supplier<Actor> nextFocusSupplier;

    protected BaseField(World world,
                        IntFunction<T> currentValueReader,
                        IntPredicate hasRequiredComponent) {
        this.world = Objects.requireNonNull(world, "world");
        this.reader = Objects.requireNonNull(currentValueReader, "reader");
        this.isApplicable = (hasRequiredComponent != null) ? hasRequiredComponent : (eid -> true);

        addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    commit();

                    if (nextFocusSupplier != null) {
                        Actor next = nextFocusSupplier.get();
                        if (next != null && getStage() != null) {
                            Gdx.app.postRunnable(() -> {
                                if (getStage() == null) return;
                                getStage().setKeyboardFocus(next);
                                if (next instanceof VisValidatableTextField tf) {
                                    tf.selectAll();
                                }
                            });
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    commit();
                }
            }
        });
    }

    // --- Config ---

    public final void setApplier(HistoryApplier<T> applier) {
        this.applier = applier;
    }

    public final void setTrimOnCommit(boolean trim) {
        this.trimOnCommit = trim;
    }

    public final void setNextFocusSupplier(Supplier<Actor> supplier) {
        this.nextFocusSupplier = supplier;
    }

    protected final void attachValidator(InputValidator validator) {
        if (validator != null) {
            addValidator(validator);
        }
    }

    // --- Cycle ---

    public final void setEntityId(int newEntityId) {
        this.entityId = newEntityId;
        refreshFromModel();
    }

    public final int getEntityId() {
        return entityId;
    }

    public final void refreshFromModel() {
        if (!canEditCurrentEntity()) {
            setDisabled(true);
            setProgrammaticText("");
            return;
        }

        setDisabled(false);
        T value = reader.apply(entityId);
        setProgrammaticText(formatValue(value));
    }

    public final void commit() {
        if (internalUpdate) return;
        if (!canEditCurrentEntity()) return;

        if (applier == null) {
            refreshFromModel();
            return;
        }

        String text = getCommitText();
        T after = parse(text);

        if (after == null) {
            refreshFromModel();
            return;
        }

        T before = reader.apply(entityId);
        if (Objects.equals(before, after)) {
            refreshFromModel();
            return;
        }

        applier.apply(entityId, after);
        refreshFromModel();
    }

    private boolean canEditCurrentEntity() {
        return entityId >= 0
                && world.getEntityManager().isActive(entityId)
                && isApplicable.test(entityId);
    }

    private String getCommitText() {
        String raw = getText();
        if (!trimOnCommit || raw == null) return raw;
        return raw.trim();
    }

    /**
     * Single entry point for every programmed text update.
     * Subclasses can temporarily bypass their filters here.
     */
    protected final void setProgrammaticText(String text) {
        internalUpdate = true;
        try {
            doSetProgrammaticText(text == null ? "" : text);
            setCursorPosition(getText().length());
        } finally {
            internalUpdate = false;
        }
    }

    /**
     * Specialized hook for subclasses.
     * Default: standard setText.
     * FloatField / IntField can override this to suspend their filter.
     */
    protected void doSetProgrammaticText(String text) {
        setText(text);
    }

    /**
     * Formats a model value for display.
     * Default: toString().
     */
    protected String formatValue(T value) {
        return value == null ? "" : value.toString();
    }

    // --- To implement ---

    protected abstract T parse(String text);
}