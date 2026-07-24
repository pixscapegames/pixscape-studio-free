package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.ClipboardPhysicsJointGraph;

import java.util.ArrayList;
import java.util.List;

public final class EntityGraphInstantiationService {
    private final World world;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;

    public EntityGraphInstantiationService(World world, HistoryManager historyManager, IdentityRegistry identityRegistry) {
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
    }

    public EntityGraphInstantiationResult instantiate(EntityGraph graph,
                                                      int activeLayerIndex,
                                                      float dx,
                                                      float dy,
                                                      String commandName) {
        if (graph == null || graph.isEmpty()) {
            return EntityGraphInstantiationResult.empty();
        }

        List<Command> commands = new ArrayList<>();
        IntArray createdIds = new IntArray();
        IntIntMap sourceToCreated = new IntIntMap();

        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntityInitializer init = entry.initializer().duplicate();
            init.allocateFreshPhysicsShapeIds();
            init.overrideLayerIndex(activeLayerIndex);
            init.translate(dx, dy);
            init.setIdentityStableId(identityRegistry.allocateStableId());

            int sourceId = entry.sourceEntityId();
            CreateEntityCommand cmd = new CreateEntityCommand(world, historyManager.historyIds(), init, createdEntityId -> {
                createdIds.add(createdEntityId);
                sourceToCreated.put(sourceId, createdEntityId);
            });
            commands.add(cmd);
        }

        if (commands.isEmpty()) return EntityGraphInstantiationResult.empty();
        commands.add(new RemapInstantiatedJointsCommand(graph, sourceToCreated));

        String label = isBlank(commandName) ? "Instantiate Entity Graph" : commandName;
        try {
            historyManager.execute(new CompositeCommand(label, commands));
        } catch (RuntimeException failure) {
            finishDeferredRollback(failure);
            throw failure;
        } catch (Error failure) {
            finishDeferredRollback(failure);
            throw failure;
        }

        return new EntityGraphInstantiationResult(createdIds, sourceToCreated);
    }

    private void finishDeferredRollback(Throwable originalFailure) {
        try {
            world.process();
        } catch (Throwable rollbackFailure) {
            originalFailure.addSuppressed(new IllegalStateException(
                    "Failed to flush deferred entity removals after graph rollback.",
                    rollbackFailure));
        }
    }

    private final class RemapInstantiatedJointsCommand implements Command {
        private final EntityGraph graph;
        private final IntIntMap sourceToCreated;

        private RemapInstantiatedJointsCommand(
                EntityGraph graph, IntIntMap sourceToCreated) {
            this.graph = graph;
            this.sourceToCreated = sourceToCreated;
        }

        @Override
        public String label() {
            return "Remap Instantiated Joints";
        }

        @Override
        public void redo() {
            for (EntityGraphEntry entry : graph.entries()) {
                int sourceId = entry.sourceEntityId();
                int pastedId = sourceToCreated.get(sourceId, -1);
                if (pastedId < 0) {
                    throw new IllegalStateException(
                            "Missing instantiated entity mapping for source " + sourceId + ".");
                }

                GenericEntitySnapshotData sourceSnapshot =
                        entry.initializer().toSnapshotData(sourceId);
                if (sourceSnapshot.hasJoint && !mJointBase.has(pastedId)) {
                    throw new IllegalStateException(
                            "Pasted joint lost PhysicsJointComponent: source="
                                    + sourceId + ", pasted=" + pastedId + ".");
                }
                if (!ClipboardPhysicsJointGraph.remapJointReferences(
                        world, pastedId, sourceToCreated)) {
                    throw new IllegalStateException(
                            "Failed to remap pasted joint dependencies for entity "
                                    + pastedId + ".");
                }
            }
        }

        @Override
        public void undo() {
            // Joint components belong to the created entities and are removed by child undo.
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
