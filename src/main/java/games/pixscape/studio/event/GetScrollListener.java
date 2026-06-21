package games.pixscape.studio.event;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;

public class GetScrollListener implements EventListener {
    final Actor target;

    public GetScrollListener(Actor target) {
        this.target = target;
    }

    @Override
    public boolean handle(Event event) {
        if (event instanceof InputEvent ie)
            if (ie.getType() == InputEvent.Type.enter)
                ie.getStage().setScrollFocus(target);
        return false;
    }
}
