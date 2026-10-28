package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.commands.ChangeParticleFlagsCommand;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.UiBinders;

public final class ParticleFxPanel extends CollapsibleWidget {

    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);
    private final UiBinders.CheckBoxBinder ambientBinder;

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
        root.add(restartBtn).row();

        // Reuse the native checkbox's ECS binder; the history command owns authored mutation.
        VisCheckBox ignoreAmbient = new VisCheckBox("Ignore ambient lighting");
        ignoreAmbient.setName("particle-ignore-ambient-lighting");
        ambientBinder = new UiBinders.CheckBoxBinder(ctx.world, ignoreAmbient,
                e -> ctx.world.getMapper(ParticleEmitterComponent.class).has(e),
                e -> ctx.world.getMapper(ParticleEmitterComponent.class).get(e).ignoreAmbientLighting,
                this::setIgnoreAmbientLighting);
        root.add(ignoreAmbient).colspan(4).left().row();
    }

    private void setIgnoreAmbientLighting(int e, boolean ignore) {
        ParticleEmitterComponent emitter = ctx.world.getMapper(ParticleEmitterComponent.class).get(e);
        ChangeParticleFlagsCommand.Snapshot before = ChangeParticleFlagsCommand.Snapshot.of(emitter);
        ChangeParticleFlagsCommand.Snapshot after = ChangeParticleFlagsCommand.Snapshot.of(emitter);
        after.ignoreAmbientLighting = ignore;
        ChangeParticleFlagsCommand command = new ChangeParticleFlagsCommand(ctx.world, ctx.history.historyIds());
        command.addEntry(ctx.history.historyIds().ensureForEntity(e), before, after);
        if (!command.isNoop()) ctx.history.execute(command);
    }

    public void refresh() { ambientBinder.refresh(); }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        ambientBinder.setEntityId(entityId);
    }
}
