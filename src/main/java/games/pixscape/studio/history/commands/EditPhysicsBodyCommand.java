package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditPhysicsBodyCommand implements Command, HistoryManager.SupportsNoop {

    public record Snapshot(int type, float gravityScale, float linearDamping, float angularDamping,
                           boolean fixedRotation, boolean bullet, boolean allowSleep) {
        public Snapshot(int type,
                        float gravityScale,
                        float linearDamping,
                        float angularDamping,
                        boolean fixedRotation,
                        boolean bullet,
                        boolean allowSleep) {
            this.type = sanitizeBodyType(type);
            this.gravityScale = gravityScale;
            this.linearDamping = Math.max(0f, linearDamping);
            this.angularDamping = Math.max(0f, angularDamping);
            this.fixedRotation = fixedRotation;
            this.bullet = bullet;
            this.allowSleep = allowSleep;
        }

        public static Snapshot capture(PhysicsBodyComponent body) {
            if (body == null) return null;
            return new Snapshot(
                    body.type,
                    body.gravityScale,
                    body.linearDamping,
                    body.angularDamping,
                    body.fixedRotation,
                    body.bullet,
                    body.allowSleep
            );
        }

        public Snapshot withType(int value) {
            return new Snapshot(value, gravityScale, linearDamping, angularDamping, fixedRotation, bullet, allowSleep);
        }

        public Snapshot withGravityScale(float value) {
            return new Snapshot(type, value, linearDamping, angularDamping, fixedRotation, bullet, allowSleep);
        }

        public Snapshot withLinearDamping(float value) {
            return new Snapshot(type, gravityScale, value, angularDamping, fixedRotation, bullet, allowSleep);
        }

        public Snapshot withAngularDamping(float value) {
            return new Snapshot(type, gravityScale, linearDamping, value, fixedRotation, bullet, allowSleep);
        }

        public Snapshot withFixedRotation(boolean value) {
            return new Snapshot(type, gravityScale, linearDamping, angularDamping, value, bullet, allowSleep);
        }

        public Snapshot withBullet(boolean value) {
            return new Snapshot(type, gravityScale, linearDamping, angularDamping, fixedRotation, value, allowSleep);
        }

        public Snapshot withAllowSleep(boolean value) {
            return new Snapshot(type, gravityScale, linearDamping, angularDamping, fixedRotation, bullet, value);
        }

        public void apply(PhysicsBodyComponent body) {
            body.type = type;
            body.gravityScale = gravityScale;
            body.linearDamping = linearDamping;
            body.angularDamping = angularDamping;
            body.fixedRotation = fixedRotation;
            body.bullet = bullet;
            body.allowSleep = allowSleep;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return type == other.type
                    && Float.compare(gravityScale, other.gravityScale) == 0
                    && Float.compare(linearDamping, other.linearDamping) == 0
                    && Float.compare(angularDamping, other.angularDamping) == 0
                    && fixedRotation == other.fixedRotation
                    && bullet == other.bullet
                    && allowSleep == other.allowSleep;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long bodyHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditPhysicsBodyCommand(World world,
                                  HistoryIdRegistry historyIds,
                                  int bodyEntityId,
                                  Snapshot before,
                                  Snapshot after) {
        this.world = world;
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.bodyHistoryId = historyIds != null ? historyIds.ensureForEntity(bodyEntityId) : -1L;

        this.noop = world == null
                || historyIds == null
                || bodyHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Physics Body";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    private void apply(Snapshot snapshot) {
        if (noop || snapshot == null) return;

        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;

        ComponentMapper<PhysicsBodyComponent> mBody = world.getMapper(PhysicsBodyComponent.class);
        PhysicsBodyComponent body = mBody.getSafe(entityId, null);
        if (body == null) return;

        snapshot.apply(body);
        if (world.getMapper(TiledLayerComponent.class).has(entityId)) {
            body.type = PhysicsBodyComponent.STATIC;
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(entityId, PhysicsDirtyBits.BODY);
        }
        EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(entityId, EventFlow.tag(this)));
    }

    private int resolveBodyEntityId() {
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }

    private static int sanitizeBodyType(int bodyType) {
        return switch (bodyType) {
            case PhysicsBodyComponent.STATIC,
                 PhysicsBodyComponent.KINEMATIC,
                 PhysicsBodyComponent.DYNAMIC -> bodyType;
            default -> PhysicsBodyComponent.DYNAMIC;
        };
    }
}
