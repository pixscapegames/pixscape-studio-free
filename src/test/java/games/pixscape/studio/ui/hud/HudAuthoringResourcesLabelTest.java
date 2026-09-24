package games.pixscape.studio.ui.hud;

import com.kotcrab.vis.ui.VisUI;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import games.pixscape.studio.service.hud.HudAuthoringResources;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HudAuthoringResourcesLabelTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void builtInDefaultNeedsNoSkin() {
        HudAuthoringResources resources = resources(null);
        try {
            assertTrue(resources.labelStyleNames().isEmpty());
            HudAuthoringResources.LabelDefaults defaults = resources.defaultLabel();
            assertNotNull(defaults);
            assertEquals(null, defaults.styleName());
            assertNotNull(resources.builtInLabelStyle());
            assertEquals(null, resources.defaultTextField().styleName());
            assertEquals(null, resources.defaultSelectBox().styleName());
            assertTrue(resources.hasDefaultList());
            assertEquals(null, resources.defaultCheckBox().styleName());
            assertEquals(null, resources.defaultSlider().styleName());
            assertNotNull(resources.builtInSliderStyle().background);
            assertNotNull(resources.builtInSliderStyle().knob);
        } finally {
            resources.dispose();
        }
    }

    @Test public void exposesCustomStylesWithoutReplacingTheBuiltInDefault() {
        Skin skin = new Skin();
        Label.LabelStyle source = VisUI.getSkin().get("default", Label.LabelStyle.class);
        skin.add("body", new Label.LabelStyle(source));
        HudAuthoringResources resources = resources(skin);
        try {
            List<String> styles = resources.labelStyleNames();
            assertFalseUnsorted(styles);
            assertEquals(List.of("body"), styles);
            HudAuthoringResources.LabelDefaults defaults = resources.defaultLabel();
            assertNotNull(defaults);
            assertEquals(null, defaults.styleName());
        } finally {
            resources.dispose();
        }
    }

    @Test public void textButtonUsesBuiltInDefaultOrFirstUsableCustomStyle() {
        HudAuthoringResources withoutSkin = resources(null);
        try {
            HudAuthoringResources.TextButtonDefaults defaults =
                    withoutSkin.defaultTextButton();
            assertNotNull(defaults);
            assertEquals(null, defaults.styleName());
            assertNotNull(withoutSkin.builtInTextButtonStyle().up);
            assertNotNull(withoutSkin.builtInTextButtonStyle().over);
            assertNotNull(withoutSkin.builtInTextButtonStyle().down);
            assertNotNull(withoutSkin.builtInTextButtonStyle().disabled);
        } finally {
            withoutSkin.dispose();
        }

        Skin skin = new Skin();
        TextButton.TextButtonStyle source =
                VisUI.getSkin().get("default", TextButton.TextButtonStyle.class);
        skin.add("primary", new TextButton.TextButtonStyle(source));
        HudAuthoringResources withSkin = resources(skin);
        try {
            assertEquals(List.of("primary"), withSkin.textButtonStyleNames());
            assertEquals("primary", withSkin.defaultTextButton().styleName());
        } finally {
            withSkin.dispose();
        }
    }

    @Test public void imageButtonStylesNeedNoButtonBackground() {
        Skin skin = new Skin();
        ImageButton.ImageButtonStyle imageOnly = new ImageButton.ImageButtonStyle();
        imageOnly.imageUp = VisUI.getSkin().getDrawable("white");
        skin.add("image-only", imageOnly);
        skin.add("empty", new ImageButton.ImageButtonStyle());
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("empty", "image-only"), resources.imageButtonStyleNames());
            assertNotNull(resources.imageButtonStyle("image-only"));
            assertNotNull(resources.imageButtonStyle("empty"));
        } finally {
            resources.dispose();
        }
    }

    @Test public void textFieldStylesRequireAFontAndFontColorButNoBackground() {
        Skin skin = new Skin();
        TextField.TextFieldStyle source =
                VisUI.getSkin().get("default", TextField.TextFieldStyle.class);
        TextField.TextFieldStyle textOnly = new TextField.TextFieldStyle();
        textOnly.font = source.font;
        textOnly.fontColor = source.fontColor;
        skin.add("text-only", textOnly);
        TextField.TextFieldStyle missingColor = new TextField.TextFieldStyle();
        missingColor.font = source.font;
        skin.add("missing-color", missingColor);
        skin.add("missing-font", new TextField.TextFieldStyle());
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("text-only"), resources.textFieldStyleNames());
            assertEquals(null, resources.defaultTextField().styleName());
            assertNotNull(resources.builtInTextFieldStyle().font);
            assertNotNull(resources.builtInTextFieldStyle().cursor);
            assertNotNull(resources.builtInTextFieldStyle().selection);
            assertNotNull(resources.builtInTextFieldStyle().background);
            assertNotNull(resources.builtInTextFieldStyle().messageFontColor);
        } finally {
            resources.dispose();
        }
    }

    @Test public void selectBoxStylesRequireListAndScrollPaneButNoBackground() {
        Skin skin = new Skin();
        SelectBox.SelectBoxStyle source = VisUI.getSkin().get("default", SelectBox.SelectBoxStyle.class);
        SelectBox.SelectBoxStyle textOnly = new SelectBox.SelectBoxStyle(source);
        textOnly.background = null;
        skin.add("text-only", textOnly);
        SelectBox.SelectBoxStyle missingList = new SelectBox.SelectBoxStyle(source);
        missingList.listStyle = null;
        skin.add("missing-list", missingList);
        SelectBox.SelectBoxStyle missingScroll = new SelectBox.SelectBoxStyle(source);
        missingScroll.scrollStyle = null;
        skin.add("missing-scroll", missingScroll);
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("text-only"), resources.selectBoxStyleNames());
            assertNotNull(resources.selectBoxStyle("text-only"));
        } finally {
            resources.dispose();
        }
    }

    @Test public void listStylesExposeUsableSkinNamesAndDefault() {
        Skin skin = new Skin();
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle source =
                VisUI.getSkin().get("default", com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle.class);
        skin.add("compact", new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle(source));
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle missingSelection =
                new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle(source);
        missingSelection.selection = null;
        skin.add("missing-selection", missingSelection);
        HudAuthoringResources resources = resources(skin);
        try {
            assertTrue(resources.hasDefaultList());
            assertEquals(List.of("compact"), resources.listStyleNames(false));
            assertNotNull(resources.listStyle("compact"));
        } finally {
            resources.dispose();
        }
    }

    @Test public void checkBoxStylesRequireFontAndBothBoxDrawablesButNotFontColor() {
        Skin skin = new Skin();
        CheckBox.CheckBoxStyle source =
                VisUI.getSkin().get("default", CheckBox.CheckBoxStyle.class);
        CheckBox.CheckBoxStyle textOnly = new CheckBox.CheckBoxStyle();
        textOnly.font = source.font;
        textOnly.fontColor = null;
        textOnly.checkboxOff = source.checkboxOff;
        textOnly.checkboxOn = source.checkboxOn;
        skin.add("text-only", textOnly);
        CheckBox.CheckBoxStyle missingOn = new CheckBox.CheckBoxStyle();
        missingOn.font = source.font;
        missingOn.checkboxOff = source.checkboxOff;
        skin.add("missing-on", missingOn);
        skin.add("missing-font", new CheckBox.CheckBoxStyle());
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("text-only"), resources.checkBoxStyleNames());
            assertNotNull(resources.checkBoxStyle("text-only"));
        } finally {
            resources.dispose();
        }
    }

    @Test public void sliderStylesRequireOnlyTheNativeInteractiveBackground() {
        Skin skin = new Skin();
        Slider.SliderStyle usable = new Slider.SliderStyle();
        usable.background = VisUI.getSkin().getDrawable("white");
        skin.add("usable", usable);
        Slider.SliderStyle missingBackground = new Slider.SliderStyle();
        missingBackground.knob = VisUI.getSkin().getDrawable("white");
        skin.add("missing-background", missingBackground);
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("usable"), resources.sliderStyleNames());
            assertNotNull(resources.sliderStyle("usable"));
            assertEquals(null, resources.defaultSlider().styleName());
        } finally {
            resources.dispose();
        }
    }

    @Test public void progressBarStylesRequireOnlyTheNativeNonNullStyleInstance() {
        Skin skin = new Skin();
        ProgressBar.ProgressBarStyle backgroundAndKnob = new ProgressBar.ProgressBarStyle();
        backgroundAndKnob.background = VisUI.getSkin().getDrawable("white");
        backgroundAndKnob.knob = VisUI.getSkin().getDrawable("white");
        skin.add("background-and-knob", backgroundAndKnob);
        ProgressBar.ProgressBarStyle knobAfterOnly = new ProgressBar.ProgressBarStyle();
        knobAfterOnly.knobAfter = VisUI.getSkin().getDrawable("white");
        skin.add("knob-after-only", knobAfterOnly);
        ProgressBar.ProgressBarStyle withoutBackground = new ProgressBar.ProgressBarStyle();
        withoutBackground.knob = VisUI.getSkin().getDrawable("white");
        skin.add("without-background", withoutBackground);
        skin.add("empty", new ProgressBar.ProgressBarStyle());
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("background-and-knob", "empty", "knob-after-only", "without-background"),
                    resources.progressBarStyleNames());
            assertNotNull(resources.progressBarStyle("empty"));
            assertEquals(null, resources.progressBarStyle("absent"));
            assertEquals(null, resources.defaultProgressBar().styleName());
        } finally {
            resources.dispose();
        }
    }

    @Test public void scrollPaneStylesRequireOnlyTheNativeNonNullStyleInstance() {
        Skin skin = new Skin();
        ScrollPane.ScrollPaneStyle complete = new ScrollPane.ScrollPaneStyle();
        complete.background = VisUI.getSkin().getDrawable("white");
        complete.vScroll = VisUI.getSkin().getDrawable("white");
        complete.vScrollKnob = VisUI.getSkin().getDrawable("white");
        skin.add("complete", complete);
        skin.add("empty", new ScrollPane.ScrollPaneStyle());
        HudAuthoringResources resources = resources(skin);
        try {
            assertEquals(List.of("complete", "empty"), resources.scrollPaneStyleNames());
            assertNotNull(resources.scrollPaneStyle("empty"));
            assertEquals(null, resources.scrollPaneStyle("absent"));
            assertEquals(null, resources.defaultScrollPane().styleName());
        } finally {
            resources.dispose();
        }
    }

    private static void assertFalseUnsorted(List<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        assertEquals(sorted, values);
    }

    private static HudAuthoringResources resources(Skin skin) {
        try {
            Constructor<HudAuthoringResources> constructor =
                    HudAuthoringResources.class.getDeclaredConstructor(Skin.class);
            constructor.setAccessible(true);
            return constructor.newInstance(skin);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
