package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.graphics.Color;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class ColorPickerFieldTest {

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void cancelRestoresTheOriginalRgbaColorBoundValueAndSwatch() throws Exception {
        Color original = rgba(0x10203040);
        Color changed = rgba(0xA0B0C0D0);
        AtomicReference<Color> bound = new AtomicReference<Color>(new Color(original));
        ColorPickerField field = new ColorPickerField("Color", "Choose")
                .useColorSwatch(20f, 20f)
                .bind(bound::get, color -> bound.get().set(color));
        beginPickerSession(field);
        ColorPickerListener listener = listener(field);

        listener.changed(changed);
        assertColor(0xA0B0C0D0, bound.get());
        assertColor(0xA0B0C0D0, field.getCurrentColor());

        listener.canceled(original);
        assertColor(0x10203040, bound.get());
        assertColor(0x10203040, field.getCurrentColor());
        assertColor(0x10203040, field(field, "colorSwatch", VisImage.class).getColor());
    }

    @Test
    public void finishingAfterALivePreviewKeepsTheSelectedColor() throws Exception {
        Color original = rgba(0x01020304);
        Color selected = rgba(0x50607080);
        AtomicReference<Color> bound = new AtomicReference<Color>(new Color(original));
        ColorPickerField field = new ColorPickerField("Color", "Choose")
                .bind(bound::get, color -> bound.get().set(color));
        ColorPickerListener listener = listener(field);

        listener.changed(selected);
        listener.finished(selected);

        assertColor(0x50607080, bound.get());
        assertColor(0x50607080, field.getCurrentColor());
    }

    @Test
    public void cancelUsesTheOpeningColorEvenWhenThePickerCallbackNormalizesAlpha()
            throws Exception {
        Color original = rgba(0x10203040);
        AtomicReference<Color> bound = new AtomicReference<Color>(new Color(original));
        ColorPickerField field = new ColorPickerField("Color", "Choose")
                .allowAlpha(false)
                .bind(bound::get, color -> bound.get().set(color));
        beginPickerSession(field);
        ColorPickerListener listener = listener(field);

        listener.changed(rgba(0xA0B0C0FF));
        listener.canceled(rgba(0x102030FF));

        assertColor(0x10203040, bound.get());
        assertColor(0x10203040, field.getCurrentColor());
    }

    private static ColorPickerListener listener(ColorPickerField field) throws Exception {
        Method method = ColorPickerField.class.getDeclaredMethod("createPickerListener");
        method.setAccessible(true);
        return (ColorPickerListener) method.invoke(field);
    }

    private static void beginPickerSession(ColorPickerField field) throws Exception {
        Method method = ColorPickerField.class.getDeclaredMethod("captureColorBeforePicker");
        method.setAccessible(true);
        method.invoke(field);
    }

    private static Color rgba(int packed) {
        Color color = new Color();
        Color.rgba8888ToColor(color, packed);
        return color;
    }

    private static void assertColor(int expected, Color actual) {
        Assert.assertEquals(expected, Color.rgba8888(actual));
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
