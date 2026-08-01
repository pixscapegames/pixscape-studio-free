package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ScrollableCodeEditorTest {

    @BeforeClass
    public static void loadStudioSkin() {
        VisUiTestBootstrap.loadSkin();
        VisUI.dispose();
        VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
    }

    @AfterClass
    public static void unloadStudioSkin() {
        VisUI.dispose();
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void contentPrefSizeTracksLinesAndLongestLineOnlyWhenTextChanges() {
        ScrollableCodeEditor editor = new ScrollableCodeEditor(4, 180f);
        float minimumHeight = editor.getTextArea().getPrefHeight();
        Assert.assertEquals(180f, editor.getTextArea().getPrefWidth(), 0.01f);

        editor.setText("one\ntwo\nthree\nfour\nfive\nsix");
        Assert.assertTrue(editor.getTextArea().getPrefHeight() > minimumHeight);

        float shortWidth = editor.getTextArea().getPrefWidth();
        editor.setText("a line that is intentionally much wider than the editor viewport and keeps going");
        Assert.assertTrue(editor.getTextArea().getPrefWidth() > shortWidth);
    }

    @Test
    public void scrollbarsAppearForOverflowAndSetTextStartsAtTopLeft() {
        ScrollableCodeEditor editor = new ScrollableCodeEditor(4, 180f);
        editor.setSize(160f, 90f);
        editor.setText("first line is deliberately wider than the viewport\n2\n3\n4\n5\n6\n7\n8");
        editor.validate();
        VisScrollPane pane = editor.getScrollPane();
        pane.validate();

        Assert.assertTrue(pane.isScrollX());
        Assert.assertTrue(pane.isScrollY());
        Assert.assertEquals(0f, pane.getScrollX(), 0.01f);
        Assert.assertEquals(0f, pane.getScrollY(), 0.01f);
    }
}
