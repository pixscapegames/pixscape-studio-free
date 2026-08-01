package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageButton.VisImageButtonStyle;
import com.kotcrab.vis.ui.widget.VisWindow;

public final class StudioModalChrome {

    private static final float CLOSE_BUTTON_SIZE = 22f;

    private StudioModalChrome() {
    }

    public static <T extends VisWindow> T apply(T window) {
        Skin skin = VisUI.getSkin();
        window.getTitleLabel().setStyle(skin.get("modal-title", LabelStyle.class));
        window.getTitleLabel().setAlignment(Align.center);
        window.getTitleTable().setBackground((Drawable) null);

        VisImageButton closeButton = findCloseButton(window, skin);
        if (closeButton == null) {
            window.addCloseButton();
            closeButton = findCloseButton(window, skin);
        }

        closeButton.setStyle(skin.get("modal-close", VisImageButtonStyle.class));
        window.getTitleTable().getCell(closeButton).size(CLOSE_BUTTON_SIZE);
        return window;
    }

    public static void drawTitleBarBackground(
            VisWindow window,
            Batch batch,
            float parentAlpha,
            float x,
            float y) {
        Table titleTable = window.getTitleTable();
        float titleHeight = titleTable.getHeight();
        if (titleHeight <= 0f) {
            titleHeight = window.getPadTop();
        }
        if (titleHeight <= 0f) return;

        float titleY = y + titleTable.getY();
        VisUI.getSkin().getDrawable("modal-titlebar-light")
                .draw(batch, x, titleY, window.getWidth(), titleHeight);

        // Window draws its title table inside super.drawBackground. The full-width stripe
        // is deliberately painted afterwards, so restore the title and close button on top.
        titleTable.draw(batch, parentAlpha);
    }

    /** Adds the same background-level title stripe to third-party modal window subclasses. */
    public static void installBackgroundTitleBar(VisWindow window) {
        WindowStyle current = window.getStyle();
        if (current.background instanceof FullWidthTitleBackground) return;

        WindowStyle style = new WindowStyle(current);
        style.background = new FullWidthTitleBackground(window, current.background);
        window.setStyle(style);
    }

    private static VisImageButton findCloseButton(VisWindow window, Skin skin) {
        VisImageButtonStyle standardStyle = skin.get("close-window", VisImageButtonStyle.class);
        VisImageButtonStyle modalStyle = skin.get("modal-close", VisImageButtonStyle.class);
        Table titleTable = window.getTitleTable();

        for (Actor child : titleTable.getChildren()) {
            if (child instanceof VisImageButton) {
                VisImageButton button = (VisImageButton) child;
                if (usesStyle(button.getStyle(), standardStyle) || usesStyle(button.getStyle(), modalStyle)) {
                    return button;
                }
            }
        }
        return null;
    }

    private static boolean usesStyle(VisImageButtonStyle actual, VisImageButtonStyle expected) {
        return actual.up == expected.up
                && actual.down == expected.down
                && actual.over == expected.over
                && actual.disabled == expected.disabled
                && actual.imageUp == expected.imageUp;
    }

    private static final class FullWidthTitleBackground extends BaseDrawable {
        private final VisWindow window;
        private final Drawable windowBackground;

        FullWidthTitleBackground(VisWindow window, Drawable windowBackground) {
            super(windowBackground);
            this.window = window;
            this.windowBackground = windowBackground;
        }

        @Override
        public void draw(Batch batch, float x, float y, float width, float height) {
            if (windowBackground != null) {
                windowBackground.draw(batch, x, y, width, height);
            }
            float titleHeight = window.getTitleTable().getHeight();
            if (titleHeight <= 0f) titleHeight = window.getPadTop();
            if (titleHeight <= 0f) return;
            VisUI.getSkin().getDrawable("modal-titlebar-light")
                    .draw(batch, x, y + height - titleHeight, width, titleHeight);
        }
    }
}
