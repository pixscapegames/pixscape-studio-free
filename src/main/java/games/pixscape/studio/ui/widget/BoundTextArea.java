package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;

import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Rebindable TextArea (setEntityId), APPLY-ONLY (no history here).
 * <p>
 * Default commit:
 * - Ctrl+Enter
 * - focusLost (optionnel via onFocusLostCommit())
 * <p>
 * maxLen applied live (no silent clamp).
 */
public class BoundTextArea extends VisTable implements TextInputWidget {

    @FunctionalInterface
    public interface Applier {
        void apply(int entityId, String value);
    }

    private final World world;
    private final IntFunction<String> reader;
    private final IntPredicate isApplicable;

    private final VisTextArea area = new VisTextArea();
    private final VisLabel counter = new VisLabel("");

    private Applier applier;

    private int entityId = -1;
    private boolean internalRefresh = false;

    private boolean trimOnCommit = false;
    private int maxLen = -1;

    private Supplier<Actor> nextFocusSupplier;

    public BoundTextArea(World world,
                         IntFunction<String> currentValueReader,
                         IntPredicate hasRequiredComponent) {
        this.world = Objects.requireNonNull(world, "world");
        this.reader = Objects.requireNonNull(currentValueReader, "reader");
        this.isApplicable = (hasRequiredComponent != null) ? hasRequiredComponent : (eid -> true);

        area.setPrefRows(4);

        VisScrollPane scroll = new VisScrollPane(area);
        scroll.setFadeScrollBars(false);

        add(scroll).growX().minHeight(90).row();
        add(counter).right().padTop(2).row();
        counter.setText("");

        installMaxLenGuard();
        installCommitKeys();
    }

    // ---------------- config ----------------

    public VisTextArea getTextArea() {
        return area;
    }

    public BoundTextArea setApplier(Applier applier) {
        this.applier = applier;
        return this;
    }

    public BoundTextArea setTrimOnCommit(boolean trim) {
        this.trimOnCommit = trim;
        return this;
    }

    public BoundTextArea setMaxLength(int maxLen) {
        this.maxLen = maxLen;
        refreshCounter();

        // immediate clamp if already too long
        String t = area.getText();
        if (maxLen > 0 && t != null && t.length() > maxLen) {
            area.setText(t.substring(0, maxLen));
            area.setCursorPosition(maxLen);
        }
        return this;
    }

    public BoundTextArea setNextFocusSupplier(Supplier<Actor> supplier) {
        this.nextFocusSupplier = supplier;
        return this;
    }

    /**
     * Commit when keyboard focus is lost.
     */
    public BoundTextArea onFocusLostCommit() {
        area.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) commit();
            }
        });
        return this;
    }

    /**
     * ESC for rollback: refresh from the model.
     */
    public BoundTextArea onEscapeRollback() {
        area.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    refreshFromModel();
                    return true;
                }
                return false;
            }
        });
        return this;
    }

    // ---------------- cycle ----------------

    public void setEntityId(int newEntityId) {
        this.entityId = newEntityId;
        refreshFromModel();
    }

    public int getEntityId() {
        return entityId;
    }

    public void refreshFromModel() {
        internalRefresh = true;
        try {
            if (!canEdit()) {
                area.setDisabled(true);
                area.setText("");
                counter.setText("");
                return;
            }

            area.setDisabled(false);

            String v = reader.apply(entityId);
            area.setText(v == null ? "" : v);
            area.setCursorPosition(area.getText().length());
            refreshCounter();

        } finally {
            internalRefresh = false;
        }
    }

    public void commit() {
        if (internalRefresh) return;
        if (!canEdit()) return;

        String raw = area.getText();
        String after = parse(raw);
        if (after == null) return;

        String before = reader.apply(entityId);
        if (Objects.equals(before, after)) return;

        if (applier != null) applier.apply(entityId, after);

        // normalise l'UI (trim/maxLen/ctrl chars)
        area.setText(after);
        area.setCursorPosition(area.getText().length());
        refreshCounter();

        // focus suivant optionnel
        if (nextFocusSupplier != null && getStage() != null) {
            Actor next = nextFocusSupplier.get();
            if (next != null) {
                Gdx.app.postRunnable(() -> getStage().setKeyboardFocus(next));
            }
        }
    }

    // ---------------- internals ----------------

    private boolean canEdit() {
        return entityId >= 0
                && world.getEntityManager().isActive(entityId)
                && isApplicable.test(entityId);
    }

    private String parse(String text) {
        String s = (text == null) ? "" : (trimOnCommit ? text.trim() : text);

        // Basic control-character filtering.
        s = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        if (maxLen > 0 && s.length() > maxLen) s = s.substring(0, maxLen);
        return s;
    }

    private void installCommitKeys() {
        area.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {

                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {

                    boolean ctrl =
                            Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) ||
                                    Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);

                    if (ctrl) {
                        commit();
                        if (getStage() != null) getStage().setKeyboardFocus(null);
                        return true; // consomme l'ENTER
                    }

                    // plain ENTER => new line (no commit)
                    return false;
                }

                return false;
            }
        });
    }

    private void installMaxLenGuard() {
        area.setTextFieldListener((tf, c) -> {
            if (internalRefresh) return;
            if (maxLen <= 0) return;

            String t = area.getText();
            if (t != null && t.length() > maxLen) {
                area.setText(t.substring(0, maxLen));
                area.setCursorPosition(maxLen);
            }
            refreshCounter();
        });
    }

    private void refreshCounter() {
        if (maxLen <= 0) {
            counter.setText("");
            return;
        }
        int len = area.getText() != null ? area.getText().length() : 0;
        counter.setText(len + "/" + maxLen);
    }
}
