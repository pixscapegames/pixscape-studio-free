package games.pixscape.studio.service.entitygraph;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.system.Box2dSyncSystem;

/** Detached classification shared by clipboard and asset publication. Never expands body scope. */
public final class CompositionJointScope {
    public record Result(IntArray internal, String diagnostic) {
    }

    private CompositionJointScope() {
    }

    public static Result classify(World world, IntSet bodies) {
        IntSet candidates = new IntSet();
        Box2dSyncSystem physics = world.getSystem(Box2dSyncSystem.class);
        if (physics != null) {
            for (IntSet.IntSetIterator it = bodies.iterator(); it.hasNext;) {
                IntArray incident = physics.jointDependencies().jointsForBody(it.next());
                if (incident != null) {
                    for (int i = 0; i < incident.size; i++) {
                        candidates.add(incident.get(i));
                    }
                }
            }
        } else {
            // Detached authoring/test Worlds have no published dependency service.
            var joints = world.getAspectSubscriptionManager().get(Aspect.all(PhysicsJointComponent.class)).getEntities();
            for (int i = 0; i < joints.size(); i++) {
                candidates.add(joints.get(i));
            }
        }
        var bases = world.getMapper(PhysicsJointComponent.class);
        var gears = world.getMapper(PhysicsGearJointComponent.class);
        IntSet internal = new IntSet();
        IntSet excluded = new IntSet();
        for (IntSet.IntSetIterator it = candidates.iterator(); it.hasNext;) {
            int entity = it.next();
            var base = bases.getSafe(entity, null);
            if (base == null) {
                continue;
            }
            boolean a = bodies.contains(base.aEid), b = bodies.contains(base.bEid);
            if (a && b) {
                internal.add(entity);
            } else if (a || b) {
                excluded.add(entity);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (IntSet.IntSetIterator it = internal.iterator(); it.hasNext;) {
                int entity = it.next();
                if (bases.get(entity).type != PhysicsJointComponent.TYPE_GEAR) {
                    continue;
                }
                var gear = gears.getSafe(entity, null);
                if (gear == null || !internal.contains(gear.joint1Eid) || !internal.contains(gear.joint2Eid)) {
                    it.remove();
                    excluded.add(entity);
                    changed = true;
                }
            }
        } while (changed);
        IntArray result = new IntArray();
        for (IntSet.IntSetIterator it = internal.iterator(); it.hasNext;) {
            result.add(it.next());
        }
        result.sort();
        IntArray excludedIds = new IntArray();
        for (IntSet.IntSetIterator it = excluded.iterator(); it.hasNext;) {
            int entity = it.next();
            var identity = world.getMapper(PixscapeIdentityComponent.class).getSafe(entity, null);
            excludedIds.add(identity == null ? entity : identity.stableId);
        }
        excludedIds.sort();
        String diagnostic = excludedIds.size == 0 ? ""
                : "External joints excluded from the copy/asset (IDs " + excludedIds
                + "). Source Scene connections are unchanged.";
        return new Result(result, diagnostic);
    }
}
