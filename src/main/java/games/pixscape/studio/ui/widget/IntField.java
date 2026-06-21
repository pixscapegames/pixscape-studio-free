package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.kotcrab.vis.ui.util.InputValidator;
import com.kotcrab.vis.ui.widget.VisTextField;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * APPLY-ONLY int field (commit with ENTER, rebind via setEntityId).
 */
public class IntField extends BaseField<Integer> {

    private VisTextField.TextFieldFilter activeFilter;

    public IntField(World world,
                    IntFunction<Integer> currentValueReader,
                    IntPredicate hasRequiredComponent) {
        this(world, currentValueReader, hasRequiredComponent, defaultValidator(), null);
    }

    public IntField(World world,
                    IntFunction<Integer> currentValueReader,
                    IntPredicate hasRequiredComponent,
                    InputValidator validator,
                    VisTextField.TextFieldFilter filter) {
        super(world, currentValueReader, hasRequiredComponent);

        attachValidator(validator != null ? validator : defaultValidator());

        VisTextField.TextFieldFilter effectiveFilter =
                (filter != null) ? filter : defaultFilter();
        setTextFieldFilter(effectiveFilter);
    }

    @Override
    public void setTextFieldFilter(VisTextField.TextFieldFilter filter) {
        this.activeFilter = filter;
        super.setTextFieldFilter(filter);
    }

    @Override
    protected Integer parse(String text) {
        if (text == null) return null;

        String s = text.trim();
        if (s.isEmpty() || "-".equals(s)) {
            return null;
        }

        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void doSetProgrammaticText(String text) {
        setTextSafely(text);
    }

    public IntField setValidator(InputValidator v) {
        attachValidator(v);
        return this;
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

    private static InputValidator defaultValidator() {
        return input -> {
            if (input == null) return false;

            String s = input.trim();
            if (s.isEmpty() || "-".equals(s)) {
                return false;
            }

            try {
                Integer.parseInt(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        };
    }

    private static VisTextField.TextFieldFilter defaultFilter() {
        return (tf, c) ->
                Character.isDigit(c) ||
                        (c == '-' && tf.getCursorPosition() == 0 && !tf.getText().startsWith("-"));
    }
}