package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.kotcrab.vis.ui.util.InputValidator;
import com.kotcrab.vis.ui.widget.VisTextField;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public class FloatField extends BaseField<Float> {

    private int displayDecimals = 0;
    private final DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(Locale.ROOT);
    private DecimalFormat displayFormat;

    private VisTextField.TextFieldFilter activeFilter;

    public FloatField(World world,
                      IntFunction<Float> currentValueReader,
                      IntPredicate hasRequiredComponent) {
        this(world, currentValueReader, hasRequiredComponent, defaultValidator(), null);
    }

    public FloatField(World world,
                      IntFunction<Float> currentValueReader,
                      IntPredicate hasRequiredComponent,
                      InputValidator validator,
                      VisTextField.TextFieldFilter filter) {
        super(world, currentValueReader, hasRequiredComponent);

        attachValidator(validator != null ? validator : defaultValidator());

        rebuildFormat();

        VisTextField.TextFieldFilter effectiveFilter =
                (filter != null) ? filter : defaultFloatFilter();
        setTextFieldFilter(effectiveFilter);
    }

    /**
     * Sets the number of displayed decimals.
     */
    public FloatField setDisplayDecimals(int n) {
        this.displayDecimals = Math.max(0, n);
        rebuildFormat();
        reformatDisplayedValue();
        return this;
    }

    @Override
    public void setTextFieldFilter(VisTextField.TextFieldFilter filter) {
        this.activeFilter = filter;
        super.setTextFieldFilter(filter);
    }

    @Override
    protected Float parse(String text) {
        if (text == null) return null;

        String s = text.trim();
        if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "-.".equals(s)) {
            return null;
        }

        try {
            return Float.parseFloat(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void doSetProgrammaticText(String text) {
        setTextSafely(text);
    }

    @Override
    protected String formatValue(Float value) {
        return value == null ? "" : displayFormat.format(value);
    }

    public FloatField setValidator(InputValidator v) {
        attachValidator(v);
        return this;
    }

    private void rebuildFormat() {
        StringBuilder pattern = new StringBuilder("0");
        if (displayDecimals > 0) {
            pattern.append('.');
            for (int i = 0; i < displayDecimals; i++) {
                pattern.append('0');
            }
        }

        displayFormat = new DecimalFormat(pattern.toString(), dfs);
        displayFormat.setGroupingUsed(false);
        displayFormat.setRoundingMode(RoundingMode.HALF_UP);
    }

    private void reformatDisplayedValue() {
        String s = getText();
        Float value = parse(s);
        if (value != null) {
            setProgrammaticText(formatValue(value));
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

    private static InputValidator defaultValidator() {
        return input -> {
            if (input == null) return false;

            String s = input.trim();
            if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "-.".equals(s)) {
                return false;
            }

            try {
                Float.parseFloat(s);
                return true;
            } catch (Exception e) {
                return false;
            }
        };
    }

    /**
     * Float filter: digits, leading '-', and a single '.'
     */
    private static VisTextField.TextFieldFilter defaultFloatFilter() {
        return (tf, c) -> {
            if (Character.isDigit(c)) return true;
            if (c == '-' && tf.getCursorPosition() == 0 && !tf.getText().startsWith("-")) return true;
            return c == '.' && !tf.getText().contains(".");
        };
    }
}