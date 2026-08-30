package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.studio.history.HistoryIdRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Builds one failure-atomic history command for a mixed standalone/hierarchy selection. */
public final class DeleteEntitiesCommandFactory {
    private DeleteEntitiesCommandFactory() {
    }

    public static Command create(
            World world,
            HistoryIdRegistry historyIds,
            IntArray entities,
            IntConsumer onRestoredEntity) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Entity deletion requires at least one target.");
        }

        ComponentMapper<GameObjectComponent> gameObjects =
                world.getMapper(GameObjectComponent.class);
        ComponentMapper<GameObjectMemberComponent> members =
                world.getMapper(GameObjectMemberComponent.class);
        IntArray hierarchyEntities = new IntArray();
        IntArray standaloneEntities = new IntArray();
        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);
            if (members.has(entityId) || gameObjects.has(entityId)) {
                hierarchyEntities.add(entityId);
            } else {
                standaloneEntities.add(entityId);
            }
        }

        List<Command> commands = new ArrayList<>();
        if (hierarchyEntities.size > 0) {
            commands.add(new DeleteGameObjectHierarchyCommand(
                    world, historyIds, hierarchyEntities, onRestoredEntity));
        }
        if (standaloneEntities.size > 0) {
            commands.add(new DeleteEntitiesCommand(
                    world, historyIds, standaloneEntities, onRestoredEntity));
        }
        return commands.size() == 1
                ? commands.get(0)
                : new CompositeCommand("Delete Entities", commands);
    }
}
