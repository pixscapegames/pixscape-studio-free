package games.pixscape.studio.history.initializer;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.PolygonComponent;
import games.pixscape.runtime.component.PolylineComponent;
import org.junit.Test;

import static org.junit.Assert.*;

public class GenericEntityInitializerContourTest {
    @Test
    public void snapshotsOwnTheirArraysAcrossWorldsAndRepeatedRestores() {
        World source = new World(new WorldConfiguration());
        World target = new World(new WorldConfiguration());
        try {
            float[] polygon = {0, 0, 20, 0, 20, 10, 10, 5, 0, 10};
            float[] polyline = {-10, 5, 20, -7, 40, 12};
            int e = source.create();
            source.getMapper(PolygonComponent.class).create(e).setVertices(polygon);
            source.getMapper(PolylineComponent.class).create(e).setVertices(polyline);
            source.process();
            GenericEntityInitializer captured = new GenericEntityInitializer(source);
            captured.syncFrom(e);
            GenericEntityInitializer duplicate = captured.duplicateFor(target);
            GenericEntitySnapshotData data = captured.toSnapshotData(e);
            GenericEntityInitializer applied = new GenericEntityInitializer(target);
            applied.applySnapshotData(data);
            source.getMapper(PolygonComponent.class).get(e).vertices[0] = 101;
            source.getMapper(PolylineComponent.class).get(e).vertices[0] = 102;
            data.polygonVertices[0] = 103;
            data.polylineVertices[0] = 104;
            // Recapturing the original initializer must not mutate its duplicate or exported data.
            captured.syncFrom(e);
            for (GenericEntityInitializer initializer : new GenericEntityInitializer[]{duplicate, applied}) {
                for (int cycle = 0; cycle < 2; cycle++) {
                    int restored = target.create();
                    initializer.init(restored);
                    target.process();
                    assertArrayEquals(polygon, target.getMapper(PolygonComponent.class).get(restored).vertices, 0f);
                    assertArrayEquals(polyline, target.getMapper(PolylineComponent.class).get(restored).vertices, 0f);
                    target.getMapper(PolygonComponent.class).get(restored).vertices[0] = 999;
                    target.getMapper(PolylineComponent.class).get(restored).vertices[0] = 999;
                }
            }
        } finally {
            source.dispose();
            target.dispose();
        }
    }

    @Test
    public void emptyComponentsAndAbsentComponentsStayDistinctWhenRecapturing() {
        World world = new World(new WorldConfiguration());
        try {
            int empty = world.create();
            world.getMapper(PolygonComponent.class).create(empty);
            world.getMapper(PolylineComponent.class).create(empty);
            int absent = world.create();
            world.process();
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(empty);
            int restored = world.create();
            initializer.duplicate().init(restored);
            world.process();
            assertEquals(0, world.getMapper(PolygonComponent.class).get(restored).vertices.length);
            assertEquals(0, world.getMapper(PolylineComponent.class).get(restored).vertices.length);
            initializer.syncFrom(absent);
            int rectangle = world.create();
            initializer.init(rectangle);
            world.process();
            assertFalse(world.getMapper(PolygonComponent.class).has(rectangle));
            assertFalse(world.getMapper(PolylineComponent.class).has(rectangle));
        } finally {
            world.dispose();
        }
    }
}
