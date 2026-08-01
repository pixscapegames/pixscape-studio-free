package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.kotcrab.vis.ui.util.dialog.OptionDialogListener;
import com.kotcrab.vis.ui.widget.VisDialog;

public final class Dialogs {

    private Dialogs() {
    }

    public static VisDialog showOKDialog(Stage stage, String title, String text) {
        return apply(com.kotcrab.vis.ui.util.dialog.Dialogs.showOKDialog(stage, title, text));
    }

    public static com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialog showOptionDialog(
            Stage stage,
            String title,
            String text,
            com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType type,
            OptionDialogListener listener) {
        return apply(com.kotcrab.vis.ui.util.dialog.Dialogs.showOptionDialog(stage, title, text, type, listener));
    }

    public static com.kotcrab.vis.ui.util.dialog.Dialogs.DetailsDialog showErrorDialog(Stage stage, String text) {
        return apply(com.kotcrab.vis.ui.util.dialog.Dialogs.showErrorDialog(stage, text));
    }

    public static com.kotcrab.vis.ui.util.dialog.Dialogs.DetailsDialog showErrorDialog(
            Stage stage, String text, Throwable exception) {
        return apply(com.kotcrab.vis.ui.util.dialog.Dialogs.showErrorDialog(stage, text, exception));
    }

    public static com.kotcrab.vis.ui.util.dialog.Dialogs.DetailsDialog showErrorDialog(
            Stage stage, String text, String details) {
        return apply(com.kotcrab.vis.ui.util.dialog.Dialogs.showErrorDialog(stage, text, details));
    }

    private static <T extends com.kotcrab.vis.ui.widget.VisWindow> T apply(T dialog) {
        StudioModalChrome.apply(dialog);
        dialog.pack();
        dialog.centerWindow();
        dialog.validate();
        StudioModalChrome.layoutTitleBarEdgeToEdge(dialog);
        return dialog;
    }

    public static final class OptionDialogType {
        public static final com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType YES_NO_CANCEL =
                com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType.YES_NO_CANCEL;

        private OptionDialogType() {
        }
    }
}
