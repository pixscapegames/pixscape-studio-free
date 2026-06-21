package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Input;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.VisCheckBox;

public class CheckBoxMenuItem extends MenuItem {
    public final VisCheckBox check;

    public CheckBoxMenuItem(String text, boolean initial) {
        super(""); // MenuItem applique le style de ligne (padding, hover, etc.)
        check = new VisCheckBox(text, initial);
        check.left();
        getLabelCell().setActor(check);   // remplace le label par la checkbox
        getClickListener().setButton(Input.Buttons.FORWARD);
    }

}

