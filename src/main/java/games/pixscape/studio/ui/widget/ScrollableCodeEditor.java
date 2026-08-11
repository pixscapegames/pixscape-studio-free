package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;

/** A code text area whose scrollable content tracks the source dimensions. */
public final class ScrollableCodeEditor extends VisTable {

    private final CodeTextArea textArea;
    private final VisScrollPane scrollPane;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final int minimumRows;
    private final float minimumContentWidth;

    private float contentPrefWidth;
    private float contentPrefHeight;
    private boolean assigningText;
    private boolean cursorScrollPending;

    public ScrollableCodeEditor(int minimumRows, float minimumContentWidth) {
        this.minimumRows = Math.max(1, minimumRows);
        this.minimumContentWidth = Math.max(0f, minimumContentWidth);
        textArea = new CodeTextArea();
        refreshContentMetrics();

        scrollPane = new VisScrollPane(textArea);
        scrollPane.setScrollingDisabled(false, false);
        scrollPane.setForceScroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setFlickScroll(false);
        scrollPane.setCancelTouchFocus(false);
        add(scrollPane).grow();

        textArea.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (assigningText) return;
                refreshContentMetrics();
                scheduleCursorScroll();
            }
        });
        textArea.addListener(new InputListener() {
            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                scheduleCursorScroll();
                return false;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                scheduleCursorScroll();
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                scheduleCursorScroll();
            }
        });
    }

    public String getText() {
        return textArea.getText();
    }

    public void setText(String text) {
        assigningText = true;
        try {
            textArea.setText(text == null ? "" : text);
            refreshContentMetrics();
            textArea.setCursorPosition(0);
            validate();
            scrollPane.validate();
            scrollPane.setScrollX(0f);
            scrollPane.setScrollY(0f);
            scrollPane.updateVisualScroll();
        } finally {
            assigningText = false;
        }
    }

    public VisTextArea getTextArea() {
        return textArea;
    }

    public VisScrollPane getScrollPane() {
        return scrollPane;
    }

    private void refreshContentMetrics() {
        BitmapFont font = textArea.getStyle().font;
        Drawable background = textArea.getStyle().background;
        float horizontalPadding = background == null ? 0f : background.getLeftWidth() + background.getRightWidth();
        float verticalPadding = background == null ? 0f : background.getTopHeight() + background.getBottomHeight();

        String text = textArea.getText();
        int lines = 1;
        float longestLine = 0f;
        int lineStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                glyphLayout.setText(font, text.substring(lineStart, i));
                longestLine = Math.max(longestLine, glyphLayout.width);
                if (i < text.length()) lines++;
                lineStart = i + 1;
            }
        }

        contentPrefWidth = Math.max(minimumContentWidth, longestLine + horizontalPadding);
        contentPrefHeight = Math.max(minimumRows, lines) * font.getLineHeight() + verticalPadding;
        textArea.invalidateHierarchy();
    }

    private void scheduleCursorScroll() {
        if (assigningText || cursorScrollPending) return;
        cursorScrollPending = true;
        Runnable scroll = () -> {
            cursorScrollPending = false;
            scrollCursorIntoView();
        };
        if (Gdx.app == null) {
            scroll.run();
        } else {
            Gdx.app.postRunnable(scroll);
        }
    }

    private void scrollCursorIntoView() {
        validate();
        scrollPane.validate();

        BitmapFont font = textArea.getStyle().font;
        Drawable background = textArea.getStyle().background;
        float left = background == null ? 0f : background.getLeftWidth();
        float top = background == null ? 0f : background.getTopHeight();
        float lineHeight = font.getLineHeight();
        float cursorX = left + textArea.getCursorX();
        float cursorY = textArea.getHeight() - top - (textArea.getCursorLine() + 1f) * lineHeight;
        scrollPane.scrollTo(cursorX, cursorY, Math.max(1f, font.getSpaceXadvance()), lineHeight, false, false);
    }

    private final class CodeTextArea extends VisTextArea {
        @Override
        public float getPrefWidth() {
            return contentPrefWidth;
        }

        @Override
        public float getPrefHeight() {
            return contentPrefHeight;
        }
    }
}
