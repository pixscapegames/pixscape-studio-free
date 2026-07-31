package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class AlignServiceTest {

    @Test
    public void alignLeft_keepsReferenceFixed_andAlignsOtherToReferenceLeft() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);

        int reference = createRect(world, 10f, 0f, 20f, 10f);
        int moved = createRect(world, 35f, 0f, 8f, 10f);

        selection.selectOnly(reference);
        selection.selectAdd(moved);

        AlignService service = new AlignService(buildCanvas(world, selection, history));
        service.alignLeft();

        TransformComponent tRef = world.getMapper(TransformComponent.class).get(reference);
        TransformComponent tMoved = world.getMapper(TransformComponent.class).get(moved);

        assertEquals(10f, tRef.x, 0.0001f);
        assertEquals(10f, tMoved.x, 0.0001f);
    }

    @Test
    public void centerHorizontal_andCenterVertical_alignCentersToReference() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);

        int reference = createRect(world, 10f, 10f, 20f, 20f);
        int moved = createRect(world, 40f, 60f, 10f, 10f);

        selection.selectOnly(reference);
        selection.selectAdd(moved);

        AlignService service = new AlignService(buildCanvas(world, selection, history));
        service.centerHorizontal();
        service.centerVertical();

        TransformComponent tMoved = world.getMapper(TransformComponent.class).get(moved);
        assertEquals(15f, tMoved.x, 0.0001f);
        assertEquals(15f, tMoved.y, 0.0001f);
    }

    @Test
    public void packHorizontal_andPackVertical_produceContiguousBoundsWithoutGap() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(64);

        int a = createRect(world, 0f, 0f, 10f, 4f);
        int b = createRect(world, 20f, 8f, 6f, 3f);
        int c = createRect(world, 40f, 20f, 5f, 2f);

        selection.selectOnly(a);
        selection.selectAdd(b);
        selection.selectAdd(c);

        AlignService service = new AlignService(buildCanvas(world, selection, history));
        service.packHorizontal();

        ComponentMapper<TransformComponent> mt = world.getMapper(TransformComponent.class);
        assertEquals(0f, mt.get(a).x, 0.0001f);
        assertEquals(10f, mt.get(b).x, 0.0001f);
        assertEquals(16f, mt.get(c).x, 0.0001f);

        mt.get(a).x = 0f;
        mt.get(a).y = 0f;
        mt.get(b).x = 5f;
        mt.get(b).y = 20f;
        mt.get(c).x = 9f;
        mt.get(c).y = 40f;

        service.packVertical();

        assertEquals(0f, mt.get(a).y, 0.0001f);
        assertEquals(4f, mt.get(b).y, 0.0001f);
        assertEquals(7f, mt.get(c).y, 0.0001f);
    }

    @Test
    public void legacyProxyBearingParticleRemainsIneligibleForAlignment() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        int reference = createRect(world, 10f, 0f, 20f, 10f);
        int particle = createRect(world, 40f, 0f, 8f, 8f);
        world.getMapper(ParticleEmitterComponent.class).create(particle);
        selection.selectOnly(reference);
        selection.selectAdd(particle);

        new AlignService(buildCanvas(world, selection, history)).alignLeft();

        assertEquals(40f, world.getMapper(TransformComponent.class).get(particle).x, 0f);
    }

    private static int createRect(World world, float x, float y, float w, float h) {
        int e = world.create();
        TransformComponent t = world.getMapper(TransformComponent.class).create(e);
        t.x = x;
        t.y = y;
        t.originX = 0f;
        t.originY = 0f;
        t.scaleX = 1f;
        t.scaleY = 1f;
        t.rotationRad = 0f;

        DimensionsComponent d = world.getMapper(DimensionsComponent.class).create(e);
        d.width = w;
        d.height = h;
        return e;
    }

    private static WorldCanvas buildCanvas(World world, SelectionService selection, HistoryManager history) throws Exception {
        sun.misc.Unsafe unsafe = getUnsafe();
        WorldCanvas canvas = (WorldCanvas) unsafe.allocateInstance(WorldCanvas.class);
        setField(canvas, "world", world);
        setField(canvas, "selectionService", selection);
        setField(canvas, "historyManager", history);
        return canvas;
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
