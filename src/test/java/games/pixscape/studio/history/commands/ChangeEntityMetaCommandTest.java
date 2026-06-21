package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.model.EntityKind;
import org.junit.Assert;
import org.junit.Test;

public class ChangeEntityMetaCommandTest {

    @Test
    public void appliesOnlyNoteAndKind() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();

        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(entityId);
        meta.note = "before";
        meta.kind = EntityKind.SPRITE;

        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        long historyId = historyIds.ensureForEntity(entityId);

        ChangeEntityMetaCommand command = new ChangeEntityMetaCommand(
                world,
                historyIds,
                historyId,
                new ChangeEntityMetaCommand.Snapshot("before", EntityKind.SPRITE),
                new ChangeEntityMetaCommand.Snapshot("after", EntityKind.PARTICLE)
        );

        command.redo();
        Assert.assertEquals("after", meta.note);
        Assert.assertEquals(EntityKind.PARTICLE, meta.kind);

        command.undo();
        Assert.assertEquals("before", meta.note);
        Assert.assertEquals(EntityKind.SPRITE, meta.kind);
    }
}
