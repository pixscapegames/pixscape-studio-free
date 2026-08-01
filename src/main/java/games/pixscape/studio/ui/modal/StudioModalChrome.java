package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.scenes.scene2d.Actor;
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
        window.getTitleTable().setBackground(skin.getDrawable("modal-titlebar-light"));

        VisImageButton closeButton = findCloseButton(window, skin);
        if (closeButton == null) {
            window.addCloseButton();
            closeButton = findCloseButton(window, skin);
        }

        closeButton.setStyle(skin.get("modal-close", VisImageButtonStyle.class));
        window.getTitleTable().getCell(closeButton).size(CLOSE_BUTTON_SIZE);
        return window;
    }

    public static void layoutTitleBarEdgeToEdge(VisWindow window) {
        Table titleTable = window.getTitleTable();
        titleTable.setBounds(0f, titleTable.getY(), window.getWidth(), titleTable.getHeight());
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
}
