package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

final class PhysicsAuthoringBodySnapshot {

    private final boolean hadFixtures;
    private final Array<FixtureDefData> fixtures = new Array<>();

    private final boolean hadAuthoring;
    private final Array<AuthoredPolygonData> polygons = new Array<>();

    private final int selectedFixtureId;

    private PhysicsAuthoringBodySnapshot(boolean hadFixtures,
                                         boolean hadAuthoring,
                                         int selectedFixtureId) {
        this.hadFixtures = hadFixtures;
        this.hadAuthoring = hadAuthoring;
        this.selectedFixtureId = selectedFixtureId;
    }

    static PhysicsAuthoringBodySnapshot capture(World world,
                                                PhysicsSelectionService selection,
                                                int bodyEid) {
        boolean hadFixtures = false;
        boolean hadAuthoring = false;

        PhysicsAuthoringBodySnapshot snapshot =
                new PhysicsAuthoringBodySnapshot(
                        false,
                        false,
                        selection != null ? selection.getSelectedFixtureId() : PhysicsSelectionService.NO_FIXTURE
                );

        if (world == null || bodyEid < 0) {
            return snapshot;
        }

        ComponentMapper<PhysicsFixturesComponent> mFixtures =
                world.getMapper(PhysicsFixturesComponent.class);

        PhysicsFixturesComponent fixturesComp = mFixtures.getSafe(bodyEid, null);
        if (fixturesComp != null) {
            hadFixtures = true;
            if (fixturesComp.fixtures != null) {
                for (FixtureDefData fixture : fixturesComp.fixtures) {
                    if (fixture != null) {
                        snapshot.fixtures.add(fixture.copy());
                    }
                }
            }
        }

        ComponentMapper<PhysicsAuthoringComponent> mAuthoring =
                world.getMapper(PhysicsAuthoringComponent.class);

        PhysicsAuthoringComponent authoringComp = mAuthoring.getSafe(bodyEid, null);
        if (authoringComp != null) {
            hadAuthoring = true;
            if (authoringComp.polygons != null) {
                for (AuthoredPolygonData polygon : authoringComp.polygons) {
                    if (polygon != null) {
                        snapshot.polygons.add(copyAuthoredPolygon(polygon));
                    }
                }
            }
        }

        return new PhysicsAuthoringBodySnapshot(hadFixtures, hadAuthoring, snapshot.selectedFixtureId)
                .withFixtures(snapshot.fixtures)
                .withPolygons(snapshot.polygons);
    }

    private PhysicsAuthoringBodySnapshot withFixtures(Array<FixtureDefData> source) {
        fixtures.clear();
        for (FixtureDefData fixture : source) {
            if (fixture != null) fixtures.add(fixture.copy());
        }
        return this;
    }

    private PhysicsAuthoringBodySnapshot withPolygons(Array<AuthoredPolygonData> source) {
        polygons.clear();
        for (AuthoredPolygonData polygon : source) {
            if (polygon != null) polygons.add(copyAuthoredPolygon(polygon));
        }
        return this;
    }

    void restore(World world,
                 HistoryIdRegistry historyIds,
                 PhysicsSelectionService selection,
                 long bodyHistoryId) {
        if (world == null || historyIds == null || bodyHistoryId <= 0L) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        ComponentMapper<PhysicsFixturesComponent> mFixtures =
                world.getMapper(PhysicsFixturesComponent.class);

        if (hadFixtures) {
            PhysicsFixturesComponent comp =
                    mFixtures.has(bodyEid) ? mFixtures.get(bodyEid) : mFixtures.create(bodyEid);

            comp.fixtures.clear();

            for (FixtureDefData fixture : fixtures) {
                if (fixture != null) {
                    comp.fixtures.add(fixture.copy());
                }
            }
        } else if (mFixtures.has(bodyEid)) {
            mFixtures.remove(bodyEid);
        }

        ComponentMapper<PhysicsAuthoringComponent> mAuthoring =
                world.getMapper(PhysicsAuthoringComponent.class);

        if (hadAuthoring) {
            PhysicsAuthoringComponent comp =
                    mAuthoring.has(bodyEid) ? mAuthoring.get(bodyEid) : mAuthoring.create(bodyEid);

            comp.polygons.clear();

            for (AuthoredPolygonData polygon : polygons) {
                if (polygon != null) {
                    comp.polygons.add(copyAuthoredPolygon(polygon));
                }
            }
        } else if (mAuthoring.has(bodyEid)) {
            mAuthoring.remove(bodyEid);
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(bodyEid, PhysicsDirtyBits.ALL);
        }

        EventFlow.i().publish(
                new EventFlow.PhysicsBodyStructureChanged(bodyEid, EventFlow.tag(this))
        );

        if (selection != null) {
            selection.focusBody(bodyEid);

            if (selectedFixtureId > 0 && fixtureExists(world, bodyEid, selectedFixtureId)) {
                selection.setSelectedFixture(bodyEid, selectedFixtureId);
            } else {
                selection.clearSelectionOnly();
            }
        }
    }

    private static boolean fixtureExists(World world, int bodyEid, int fixtureId) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(bodyEid, null);

        if (fixtures == null) return false;

        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) {
                return true;
            }
        }

        return false;
    }

    private static AuthoredPolygonData copyAuthoredPolygon(AuthoredPolygonData source) {
        AuthoredPolygonData out = new AuthoredPolygonData();

        if (source == null) return out;

        out.authoringId = source.authoringId;

        out.sourceCount = source.sourceCount;
        out.sourceVerts = copyFloatArray(source.sourceVerts, source.sourceCount * 2);

        out.decompositionAlgorithmVersion = source.decompositionAlgorithmVersion;
        out.sourceHash = source.sourceHash;

        out.generatedFixtureIds = copyIntArray(source.generatedFixtureIds);

        out.density = source.density;
        out.friction = source.friction;
        out.restitution = source.restitution;
        out.isSensor = source.isSensor;

        out.categoryBits = source.categoryBits;
        out.maskBits = source.maskBits;
        out.groupIndex = source.groupIndex;

        out.offsetX = source.offsetX;
        out.offsetY = source.offsetY;
        out.angleDeg = source.angleDeg;

        out.convexParts.clear();

        if (source.convexParts != null) {
            for (ConvexPolygonPartData part : source.convexParts) {
                if (part == null) continue;

                ConvexPolygonPartData copy = new ConvexPolygonPartData();
                copy.count = part.count;
                copy.verts = copyFloatArray(part.verts, part.count * 2);

                out.convexParts.add(copy);
            }
        }

        return out;
    }

    private static float[] copyFloatArray(float[] source, int wantedLength) {
        int n = Math.max(0, wantedLength);
        float[] out = new float[n];

        if (source != null && n > 0) {
            System.arraycopy(source, 0, out, 0, Math.min(source.length, n));
        }

        return out;
    }

    private static int[] copyIntArray(int[] source) {
        if (source == null || source.length == 0) {
            return new int[0];
        }

        int[] out = new int[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }
}