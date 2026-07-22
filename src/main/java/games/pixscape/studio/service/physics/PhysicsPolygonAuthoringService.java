package games.pixscape.studio.service.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.event.EventFlow;

import java.util.Arrays;

public final class PhysicsPolygonAuthoringService {

    private final World world;

    private final ComponentMapper<PhysicsAuthoringComponent> mAuthoring;
    private final ComponentMapper<PhysicsFixturesComponent> mFixtures;

    private final int eventTag;

    public PhysicsPolygonAuthoringService(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }

        this.world = world;
        this.mAuthoring = world.getMapper(PhysicsAuthoringComponent.class);
        this.mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        this.eventTag = EventFlow.tag(this);
    }

    public PolygonBuildResult buildFromSource(float[] sourceVerts, int sourceCount) {
        return PolygonDecomposer.build(sourceVerts, sourceCount);
    }

    public AuthoredPolygonData applyAuthoredPolygon(
            int bodyEntityId,
            long authoringId,
            float[] sourceVerts,
            int sourceCount
    ) {
        return applyAuthoredPolygon(bodyEntityId, authoringId, sourceVerts, sourceCount, null);
    }

    public AuthoredPolygonData applyAuthoredPolygon(
            int bodyEntityId,
            long authoringId,
            float[] sourceVerts,
            int sourceCount,
            AuthoredPolygonData materialSource
    ) {
        if (bodyEntityId < 0) {
            throw new IllegalArgumentException("Invalid body entity id.");
        }

        PolygonBuildResult build = PolygonDecomposer.build(sourceVerts, sourceCount);
        if (!build.isValid()) {
            throw new IllegalArgumentException(build.message());
        }

        PhysicsAuthoringComponent authoring = getOrCreateAuthoring(bodyEntityId);
        PhysicsFixturesComponent fixtures = getOrCreateFixtures(bodyEntityId);

        AuthoredPolygonData polygon = findByAuthoringId(authoring, authoringId);
        if (polygon == null) {
            polygon = new AuthoredPolygonData();
            polygon.authoringId = authoringId > 0L
                    ? authoringId
                    : allocateAuthoringId(authoring);
            authoring.polygons.add(polygon);
        }

        if (materialSource != null) {
            copyMaterial(materialSource, polygon);
        }

        int[] previousGeneratedIds = safeCopy(polygon.generatedFixtureIds);
        Array<ConvexPolygonPartData> previousParts = copyParts(polygon.convexParts);

        removeGeneratedFixtures(fixtures, previousGeneratedIds);
        copyBuildToAuthoredPolygon(build, polygon);

        int[] generatedIds = materializeFixtures(
                fixtures, polygon, previousParts, previousGeneratedIds, 0);
        polygon.generatedFixtureIds = generatedIds;

        markPhysicsDirty(bodyEntityId);
        publishStructureChanged(bodyEntityId);

        return polygon;
    }

    public boolean removeAuthoredPolygon(int bodyEntityId, long authoringId) {
        if (bodyEntityId < 0 || authoringId <= 0L) {
            return false;
        }

        PhysicsAuthoringComponent authoring = mAuthoring.getSafe(bodyEntityId, null);
        PhysicsFixturesComponent fixtures = mFixtures.getSafe(bodyEntityId, null);

        if (authoring == null) {
            return false;
        }

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData polygon = authoring.polygons.get(i);
            if (polygon == null || polygon.authoringId != authoringId) {
                continue;
            }

            if (fixtures != null) {
                removeGeneratedFixtures(fixtures, polygon.generatedFixtureIds);
            }

            authoring.polygons.removeIndex(i);

            markPhysicsDirty(bodyEntityId);
            publishStructureChanged(bodyEntityId);

            return true;
        }

        return false;
    }

    public AuthoredPolygonData findByAuthoringId(int bodyEntityId, long authoringId) {
        PhysicsAuthoringComponent authoring = mAuthoring.getSafe(bodyEntityId, null);
        return findByAuthoringId(authoring, authoringId);
    }

    public AuthoredPolygonData findByGeneratedFixtureId(int bodyEntityId, long fixtureId) {
        if (fixtureId <= 0L) {
            return null;
        }

        PhysicsAuthoringComponent authoring = mAuthoring.getSafe(bodyEntityId, null);
        if (authoring == null) {
            return null;
        }

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData polygon = authoring.polygons.get(i);
            if (polygon == null || polygon.generatedFixtureIds == null) {
                continue;
            }

            if (contains(polygon.generatedFixtureIds, fixtureId)) {
                return polygon;
            }
        }

        return null;
    }

    public boolean isGeneratedFixture(int bodyEntityId, long fixtureId) {
        return findByGeneratedFixtureId(bodyEntityId, fixtureId) != null;
    }

    public boolean hasAuthoring(int bodyEntityId) {
        PhysicsAuthoringComponent authoring = mAuthoring.getSafe(bodyEntityId, null);
        return authoring != null && authoring.polygons.size > 0;
    }

    private PhysicsAuthoringComponent getOrCreateAuthoring(int bodyEntityId) {
        PhysicsAuthoringComponent authoring = mAuthoring.getSafe(bodyEntityId, null);
        if (authoring == null) {
            authoring = mAuthoring.create(bodyEntityId);
        }
        return authoring;
    }

    private PhysicsFixturesComponent getOrCreateFixtures(int bodyEntityId) {
        PhysicsFixturesComponent fixtures = mFixtures.getSafe(bodyEntityId, null);
        if (fixtures == null) {
            fixtures = mFixtures.create(bodyEntityId);
        }
        return fixtures;
    }

    private static AuthoredPolygonData findByAuthoringId(
            PhysicsAuthoringComponent authoring,
            long authoringId
    ) {
        if (authoring == null || authoringId <= 0L) {
            return null;
        }

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData polygon = authoring.polygons.get(i);
            if (polygon != null && polygon.authoringId == authoringId) {
                return polygon;
            }
        }

        return null;
    }

    private static long allocateAuthoringId(PhysicsAuthoringComponent authoring) {
        long max = 0L;

        if (authoring != null) {
            for (int i = 0; i < authoring.polygons.size; i++) {
                AuthoredPolygonData polygon = authoring.polygons.get(i);
                if (polygon != null && polygon.authoringId > max) {
                    max = polygon.authoringId;
                }
            }
        }

        return max + 1L;
    }

    private static void copyBuildToAuthoredPolygon(
            PolygonBuildResult build,
            AuthoredPolygonData target
    ) {
        target.sourceCount = build.sourceCount();
        target.sourceVerts = copyVerts(build.sourceVerts(), build.sourceCount());

        target.decompositionAlgorithmVersion = build.algorithmVersion();
        target.sourceHash = build.sourceHash();

        target.convexParts.clear();

        Array<ConvexPolygonPartData> parts = build.parts();
        for (int i = 0; i < parts.size; i++) {
            ConvexPolygonPartData sourcePart = parts.get(i);
            if (sourcePart == null) {
                continue;
            }

            ConvexPolygonPartData part = new ConvexPolygonPartData();
            part.count = sourcePart.count;
            part.verts = copyVerts(sourcePart.verts, sourcePart.count);

            target.convexParts.add(part);
        }
    }

    private int[] materializeFixtures(
            PhysicsFixturesComponent fixtures,
            AuthoredPolygonData polygon,
            Array<ConvexPolygonPartData> previousParts,
            int[] previousFixtureIds,
            int replacementFixtureId
    ) {
        if (fixtures == null || polygon == null || polygon.convexParts == null) {
            return new int[0];
        }

        int[] generatedIds = new int[polygon.convexParts.size];
        boolean[] previousUsed = previousParts != null ? new boolean[previousParts.size] : new boolean[0];

        for (int i = 0; i < polygon.convexParts.size; i++) {
            ConvexPolygonPartData part = polygon.convexParts.get(i);
            if (part == null || part.count < 3 || part.verts == null || part.verts.length < part.count * 2) {
                continue;
            }

            FixtureDefData fixture = new FixtureDefData();

            int restoredFixtureId = matchingPreviousFixtureId(
                    part, i, polygon.convexParts.size,
                    previousParts, previousFixtureIds, previousUsed);
            if (restoredFixtureId == 0 && i == 0 && replacementFixtureId > 0) {
                restoredFixtureId = replacementFixtureId;
            }
            fixture.fixtureId = restoredFixtureId > 0
                    ? restoredFixtureId : allocateNewFixtureId();

            fixture.shapeType = FixtureDefData.SHAPE_POLYGON;
            fixture.polyCount = part.count;
            fixture.polyVerts = copyVerts(part.verts, part.count);

            fixture.density = polygon.density;
            fixture.friction = polygon.friction;
            fixture.restitution = polygon.restitution;
            fixture.isSensor = polygon.isSensor;

            fixture.categoryBits = polygon.categoryBits;
            fixture.maskBits = polygon.maskBits;
            fixture.groupIndex = polygon.groupIndex;

            fixture.offsetX = polygon.offsetX;
            fixture.offsetY = polygon.offsetY;
            fixture.angleDeg = polygon.angleDeg;

            fixtures.fixtures.add(fixture);
            generatedIds[i] = fixture.fixtureId;
        }

        return compactIds(generatedIds);
    }

    private static void removeGeneratedFixtures(
            PhysicsFixturesComponent fixtures,
            int[] generatedFixtureIds
    ) {
        if (fixtures == null || fixtures.fixtures == null || generatedFixtureIds == null || generatedFixtureIds.length == 0) {
            return;
        }

        for (int i = fixtures.fixtures.size - 1; i >= 0; i--) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) {
                continue;
            }

            if (contains(generatedFixtureIds, fixture.fixtureId)) {
                fixtures.fixtures.removeIndex(i);
            }
        }
    }

    public AuthoredPolygonData applyAuthoredPolygonReplacingFixture(
            int bodyEntityId,
            long authoringId,
            float[] sourceVerts,
            int sourceCount,
            FixtureDefData materialSource,
            long fixtureIdToReplace
    ) {
        if (bodyEntityId < 0) {
            throw new IllegalArgumentException("Invalid body entity id.");
        }

        PolygonBuildResult build = PolygonDecomposer.build(sourceVerts, sourceCount);
        if (!build.isValid()) {
            throw new IllegalArgumentException(build.message());
        }

        PhysicsAuthoringComponent authoring = getOrCreateAuthoring(bodyEntityId);
        PhysicsFixturesComponent fixtures = getOrCreateFixtures(bodyEntityId);

        AuthoredPolygonData polygon = findByAuthoringId(authoring, authoringId);
        boolean creating = polygon == null;

        if (polygon == null) {
            polygon = new AuthoredPolygonData();
            polygon.authoringId = authoringId > 0L
                    ? authoringId
                    : allocateAuthoringId(authoring);
            authoring.polygons.add(polygon);
        }

        if (materialSource != null) {
            copyMaterial(materialSource, polygon);
        }

        int[] previousGeneratedIds = safeCopy(polygon.generatedFixtureIds);
        Array<ConvexPolygonPartData> previousParts = copyParts(polygon.convexParts);

        removeGeneratedFixtures(fixtures, previousGeneratedIds);

        if (creating && fixtureIdToReplace > 0L) {
            if (fixtureIdToReplace > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("fixtureIdToReplace exceeds int range: " + fixtureIdToReplace);
            }
            removeFixtureById(fixtures, fixtureIdToReplace);
        }

        copyBuildToAuthoredPolygon(build, polygon);

        int replacementFixtureId = creating && fixtureIdToReplace > 0L
                ? (int) fixtureIdToReplace : 0;
        int[] generatedIds = materializeFixtures(
                fixtures, polygon, previousParts, previousGeneratedIds, replacementFixtureId);
        polygon.generatedFixtureIds = generatedIds;

        markPhysicsDirty(bodyEntityId);
        publishStructureChanged(bodyEntityId);

        return polygon;
    }

    private static void copyMaterial(FixtureDefData source, AuthoredPolygonData target) {
        if (source == null || target == null) return;

        target.density = source.density;
        target.friction = source.friction;
        target.restitution = source.restitution;
        target.isSensor = source.isSensor;

        target.categoryBits = source.categoryBits;
        target.maskBits = source.maskBits;
        target.groupIndex = source.groupIndex;

        target.offsetX = source.offsetX;
        target.offsetY = source.offsetY;
        target.angleDeg = source.angleDeg;
    }

    private static void removeFixtureById(PhysicsFixturesComponent fixtures, long fixtureId) {
        if (fixtures == null || fixtures.fixtures == null || fixtureId <= 0L) return;

        for (int i = fixtures.fixtures.size - 1; i >= 0; i--) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;

            if (fixture.fixtureId == fixtureId) {
                fixtures.fixtures.removeIndex(i);
                return;
            }
        }
    }

    private static void copyMaterial(AuthoredPolygonData source, AuthoredPolygonData target) {
        target.density = source.density;
        target.friction = source.friction;
        target.restitution = source.restitution;
        target.isSensor = source.isSensor;

        target.categoryBits = source.categoryBits;
        target.maskBits = source.maskBits;
        target.groupIndex = source.groupIndex;

        target.offsetX = source.offsetX;
        target.offsetY = source.offsetY;
        target.angleDeg = source.angleDeg;
    }

    private static float[] copyVerts(float[] source, int count) {
        int n = Math.max(0, count) * 2;
        float[] out = new float[n];

        if (source != null && n > 0) {
            System.arraycopy(source, 0, out, 0, Math.min(source.length, n));
        }

        return out;
    }

    private static int[] safeCopy(int[] source) {
        return source != null ? Arrays.copyOf(source, source.length) : new int[0];
    }

    private static boolean contains(int[] ids, long id) {
        if (ids == null || id <= 0L) {
            return false;
        }

        for (int candidate : ids) {
            if (candidate == id) {
                return true;
            }
        }

        return false;
    }

    private static Array<ConvexPolygonPartData> copyParts(Array<ConvexPolygonPartData> source) {
        Array<ConvexPolygonPartData> copy = new Array<>();
        if (source == null) return copy;
        for (ConvexPolygonPartData part : source) {
            if (part == null) {
                copy.add(null);
                continue;
            }
            ConvexPolygonPartData cloned = new ConvexPolygonPartData();
            cloned.count = part.count;
            cloned.verts = copyVerts(part.verts, part.count);
            copy.add(cloned);
        }
        return copy;
    }

    private static int matchingPreviousFixtureId(ConvexPolygonPartData candidate,
                                                 int candidateIndex,
                                                 int newPartCount,
                                                 Array<ConvexPolygonPartData> previousParts,
                                                 int[] previousFixtureIds,
                                                 boolean[] previousUsed) {
        if (candidate == null || previousParts == null || previousFixtureIds == null) return 0;
        int count = Math.min(previousParts.size, previousFixtureIds.length);
        for (int i = 0; i < count; i++) {
            if (previousUsed[i] || previousFixtureIds[i] <= 0) continue;
            if (samePart(candidate, previousParts.get(i))) {
                previousUsed[i] = true;
                return previousFixtureIds[i];
            }
        }
        if (previousParts.size == newPartCount
                && candidateIndex >= 0 && candidateIndex < count
                && !previousUsed[candidateIndex]
                && previousFixtureIds[candidateIndex] > 0) {
            previousUsed[candidateIndex] = true;
            return previousFixtureIds[candidateIndex];
        }
        return 0;
    }

    private static boolean samePart(ConvexPolygonPartData a, ConvexPolygonPartData b) {
        if (a == null || b == null || a.count != b.count || a.verts == null || b.verts == null) return false;
        int length = a.count * 2;
        if (a.verts.length < length || b.verts.length < length) return false;
        for (int i = 0; i < length; i++) {
            if (Float.compare(a.verts[i], b.verts[i]) != 0) return false;
        }
        return true;
    }

    private int allocateNewFixtureId() {
        FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
        if (allocator == null) {
            throw new IllegalStateException("FixtureIdAllocatorSystem is required to materialize a polygon fixture.");
        }
        return allocator.allocateNewFixtureId();
    }

    private static int[] compactIds(int[] ids) {
        if (ids == null || ids.length == 0) {
            return new int[0];
        }

        int count = 0;
        for (int id : ids) {
            if (id > 0) {
                count++;
            }
        }

        if (count == ids.length) {
            return ids;
        }

        int[] out = new int[count];
        int w = 0;

        for (int id : ids) {
            if (id > 0) {
                out[w++] = id;
            }
        }

        return out;
    }

    private void markPhysicsDirty(int bodyEntityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(bodyEntityId, PhysicsDirtyBits.FIXTURE);
        }
    }

    private void publishStructureChanged(int bodyEntityId) {
        EventFlow.i().publish(
                new EventFlow.PhysicsBodyStructureChanged(bodyEntityId, eventTag)
        );
    }
}
