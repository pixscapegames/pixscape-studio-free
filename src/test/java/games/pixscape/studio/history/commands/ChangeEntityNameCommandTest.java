package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Assert;
import org.junit.Test;

public class ChangeEntityNameCommandTest {

    @Test
    public void redoUndoRestoresIdentityName() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();

        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entityId);
        identity.name = "before";

        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        long historyId = historyIds.ensureForEntity(entityId);

        ChangeEntityNameCommand command = new ChangeEntityNameCommand(
                world,
                historyIds,
                historyId,
                "before",
                "after",
                0
        );

        command.redo();
        Assert.assertEquals("after", identity.name);

        command.undo();
        Assert.assertEquals("before", identity.name);
    }
}
