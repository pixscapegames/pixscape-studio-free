package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.service.SpatialActorPose;
import games.pixscape.runtime.spatial.SpatialActorGeometry;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SpatialActorShapeSupport;

/** Changes only the local override; circle conversion is prepared before any mutation. */
public final class SetFootprintInheritanceCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final ToggleSpatialActorCommand delegate;

    public SetFootprintInheritanceCommand(World world, HistoryIdRegistry ids, int entity, boolean inherited) {
        this(world, ids, entity, inherited, EditorDocumentType.SCENE);
    }

    public SetFootprintInheritanceCommand(World world, HistoryIdRegistry ids, int entity, boolean inherited,
                                          EditorDocumentType documentType) {
        int ancestor = SpatialActorShapeSupport.ancestorOwner(world, entity);
        if (ancestor < 0) {
            throw new IllegalArgumentException("A spatial ancestor is required to change footprint inheritance.");
        }
        SpatialFootprintComponent initial = inherited ? null : convertedFootprint(world, ancestor, entity);
        delegate = new ToggleSpatialActorCommand(world, ids, entity, !inherited, true, initial, false, documentType);
    }

    private static SpatialFootprintComponent convertedFootprint(World world, int owner, int target) {
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        var poses = hierarchy == null ? null : hierarchy.worldTransforms();
        var transforms = world.getMapper(TransformComponent.class);
        var source = world.getMapper(SpatialFootprintComponent.class).get(owner);
        var circle = new SpatialActorGeometry.Footprint();
        var identities = games.pixscape.runtime.service.IdentityRegistry.boundTo(world);
        var ownerPose = poses != null && poses.isResolved(owner) ? transforms.getSafe(owner, null)
                : GameObjectHierarchyCommandSupport.worldTransform(world, identities, owner);
        if (!SpatialActorPose.resolve(owner, ownerPose, source, poses, circle)) {
            throw new IllegalArgumentException("Inherited footprint has no valid world circle.");
        }
        var pose = transforms.getSafe(target, null);
        if (pose == null) {
            throw new IllegalArgumentException("Footprint owner requires a transform.");
        }
        boolean resolved = poses != null && poses.isResolved(target);
        if (!resolved) {
            pose = GameObjectHierarchyCommandSupport.worldTransform(world, identities, target);
        }
        float angle = resolved ? poses.rotationRad[target] : pose.rotationRad;
        float dx = circle.footX - (resolved ? poses.x[target] : pose.x);
        float dy = circle.footY - (resolved ? poses.y[target] : pose.y);
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        float norm = cos * cos + sin * sin;
        SpatialFootprintComponent result = source.copy();
        // Offsets/radius are logical world pixels, not scaled visual-local coordinates.
        result.localOffsetXPx = (cos * dx + sin * dy) / norm;
        result.localOffsetYPx = (cos * dy - sin * dx) / norm;
        result.validate();
        return result;
    }

    @Override
    public String label() {
        return "Change footprint inheritance";
    }
    @Override
    public boolean isNoop() {
        return delegate.isNoop();
    }
    @Override
    public void redo() {
        delegate.redo();
    }
    @Override
    public void undo() {
        delegate.undo();
    }
    @Override
    public CommandOutcome executeOutcome() {
        return delegate.executeOutcome();
    }
    @Override
    public CommandOutcome redoOutcome() {
        return delegate.redoOutcome();
    }
    @Override
    public CommandOutcome undoOutcome() {
        return delegate.undoOutcome();
    }
}
