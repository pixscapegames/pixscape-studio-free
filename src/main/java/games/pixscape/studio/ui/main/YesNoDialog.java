package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;


public final class YesNoDialog {

    private YesNoDialog() {
    }

    public static void show(
            Stage stage,
            Skin skin,
            String title,
            String message,
            Runnable onYes,
            Runnable onNo
    ) {
        Dialog dialog = new Dialog(title, skin) {
            @Override
            protected void result(Object object) {
                boolean yes = Boolean.TRUE.equals(object);
                if (yes) {
                    if (onYes != null) onYes.run();
                } else {
                    if (onNo != null) onNo.run();
                }
            }
        };

        dialog.text(message);
        dialog.button("Yes", true);
        dialog.button("No", false);
        dialog.key(Input.Keys.ENTER, true);
        dialog.key(Input.Keys.ESCAPE, false);
        dialog.show(stage);
    }
}

