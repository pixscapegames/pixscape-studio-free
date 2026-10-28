package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class ChangeParticleFlagsCommandTest {
    @Test public void ambientToggleUsesOneHistoryEntryAndRestoresPlaybackFlags() {
        World world=new World(new WorldConfiguration());HistoryManager history=new HistoryManager(16);
        try {
            int e=world.create();ParticleEmitterComponent c=world.getMapper(ParticleEmitterComponent.class).create(e);
            c.autoStart=false;c.looping=false;c.paused=true;
            ChangeParticleFlagsCommand.Snapshot before=ChangeParticleFlagsCommand.Snapshot.of(c);
            ChangeParticleFlagsCommand.Snapshot after=ChangeParticleFlagsCommand.Snapshot.of(c);after.ignoreAmbientLighting=true;
            ChangeParticleFlagsCommand command=new ChangeParticleFlagsCommand(world,history.historyIds());
            command.addEntry(history.historyIds().ensureForEntity(e),before,after);history.execute(command);
            Assert.assertEquals(1,history.getCursor());
            for(int i=0;i<3;i++){
                Assert.assertTrue(c.ignoreAmbientLighting);Assert.assertTrue(c.paused);Assert.assertFalse(c.autoStart);Assert.assertFalse(c.looping);
                history.undo();Assert.assertFalse(c.ignoreAmbientLighting);history.redo();
            }
            c.paused=false;c.playRequested=true;c.restartRequested=true;
            history.undo();history.redo();
            Assert.assertFalse(c.paused);Assert.assertTrue(c.playRequested);Assert.assertTrue(c.restartRequested);
            ChangeParticleFlagsCommand noop=new ChangeParticleFlagsCommand(world,history.historyIds());
            noop.addEntry(history.historyIds().ensureForEntity(e),ChangeParticleFlagsCommand.Snapshot.of(c),ChangeParticleFlagsCommand.Snapshot.of(c));Assert.assertTrue(noop.isNoop());
        } finally {world.dispose();}
    }
}
