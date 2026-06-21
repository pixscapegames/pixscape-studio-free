package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.ui.config.CommonLayout;

public final class ParticleFxPanel extends CollapsibleWidget {

    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);

    private int entityId = -1;

    public ParticleFxPanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;

        setTable(root);
        root.defaults().top().left().pad(5);

        VisTextButton playBtn = new VisTextButton("Play");
        playBtn.setColor(CommonLayout.BUTTON_COLOR);
        playBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                if (entityId >= 0)
                    EventFlow.i().publish(new EventFlow.ParticleControlRequested(entityId, EventFlow.ParticleControlType.PLAY));
            }
        });

        VisTextButton pauseBtn = new VisTextButton("Pause");
        pauseBtn.setColor(CommonLayout.BUTTON_COLOR);
        pauseBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                if (entityId >= 0)
                    EventFlow.i().publish(new EventFlow.ParticleControlRequested(entityId, EventFlow.ParticleControlType.PAUSE));
            }
        });

        VisTextButton restartBtn = new VisTextButton("Restart");
        restartBtn.setColor(CommonLayout.BUTTON_COLOR);
        restartBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                if (entityId >= 0)
                    EventFlow.i().publish(new EventFlow.ParticleControlRequested(entityId, EventFlow.ParticleControlType.RESTART));
            }
        });

        root.add(new VisLabel("Particle FX:")).left();
        root.add(playBtn);
        root.add(pauseBtn);
        root.add(restartBtn);
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }
}
