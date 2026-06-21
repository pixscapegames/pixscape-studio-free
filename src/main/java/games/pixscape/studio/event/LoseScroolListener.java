package games.pixscape.studio.event;

import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;

public class LoseScroolListener implements EventListener {

    @Override
    public boolean handle(Event event) {
        if (event instanceof InputEvent ie)
            if (ie.getType() == InputEvent.Type.exit)
                ie.getStage().setScrollFocus(ie.getStage().getRoot());
        return false;
    }
}
