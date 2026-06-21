package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.util.InputValidator;
import com.kotcrab.vis.ui.widget.VisTextField;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * APPLY-ONLY text field (commit with ENTER, rebind via setEntityId).
 */
public class TextField extends BaseField<String> {

    private VisTextField.TextFieldFilter activeFilter;

    public TextField(World world,
                     IntFunction<String> currentValueReader,
                     IntPredicate hasRequiredComponent) {
        this(world, currentValueReader, hasRequiredComponent, true, acceptAll(), null);
    }

    public TextField(World world,
                     IntFunction<String> currentValueReader,
                     IntPredicate hasRequiredComponent,
                     boolean trimOnCommit,
                     InputValidator validator,
                     VisTextField.TextFieldFilter filter) {
        super(world, currentValueReader, hasRequiredComponent);

        setTrimOnCommit(trimOnCommit);
        attachValidator(validator != null ? validator : acceptAll());

        if (filter != null) {
            setTextFieldFilter(filter);
        }
    }

    @Override
    public void setTextFieldFilter(VisTextField.TextFieldFilter filter) {
        this.activeFilter = filter;
        super.setTextFieldFilter(filter);
    }

    @Override
    protected String parse(String text) {
        return text;
    }

    @Override
    protected void doSetProgrammaticText(String text) {
        setTextSafely(text);
    }

    public static InputValidator acceptAll() {
        return input -> true;
    }

    public static InputValidator nonEmpty() {
        return input -> input != null && !input.trim().isEmpty();
    }

    public static VisTextField.TextFieldFilter safeNameFilter() {
        return (tf, c) -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ';
    }

    /**
     * Executes action when the user presses ENTER (or numpad ENTER).
     */
    public TextField onEnter(Runnable action) {
        return onEnter(true, true, action);
    }

    /**
     * Variante configurable : blur et nettoyage du \n/\r optionnels.
     */
    public TextField onEnter(boolean blurAfter, boolean stripNewline, Runnable action) {
        this.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    if (stripNewline) stripNewlines();
                    action.run();
                    if (blurAfter && getStage() != null) {
                        getStage().setKeyboardFocus(null);
                    }
                    return true;
                }
                return false;
            }
        });

        this.setTextFieldListener((tf, c) -> {
            if (c == '\n' || c == '\r') {
                if (stripNewline) stripNewlines();
                action.run();
                if (blurAfter && getStage() != null) {
                    getStage().setKeyboardFocus(null);
                }
            }
        });

        return this;
    }

    /**
     * Executes action when the field loses keyboard focus.
     */
    public TextField onFocusLost(Runnable action) {
        this.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) {
                    action.run();
                }
            }
        });
        return this;
    }

    /**
     * ESC to cancel/rollback.
     */
    public TextField onEscape(Runnable action) {
        this.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    action.run();
                    return true;
                }
                return false;
            }
        });
        return this;
    }

    private void stripNewlines() {
        String t = getText();
        if (t != null && (t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0)) {
            setProgrammaticText(t.replace("\n", "").replace("\r", ""));
        }
    }

    @Override
    public void setText(String text) {
        setTextSafely(text);
    }

    private void setTextSafely(String value) {
        VisTextField.TextFieldFilter filterToRestore = activeFilter;
        super.setTextFieldFilter(null);
        try {
            super.setText(value == null ? "" : value);
        } finally {
            super.setTextFieldFilter(filterToRestore);
        }
    }
}