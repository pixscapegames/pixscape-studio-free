package games.pixscape.studio.component;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TiledObjectComponentTest {

    @Test
    public void defaultsAndResetClearImportedProvenance() {
        TiledObjectComponent component = new TiledObjectComponent();
        assertEquals(TiledObjectComponent.Kind.UNKNOWN, component.kind);
        assertEquals("", component.className);

        component.kind = TiledObjectComponent.Kind.TILE;
        component.className = "Enemy";
        component.reset();

        assertEquals(TiledObjectComponent.Kind.UNKNOWN, component.kind);
        assertEquals("", component.className);
    }

    @Test
    public void pooledReuseDoesNotLeakKindOrClassName() {
        World world = new World(new WorldConfiguration());
        int first = world.create();
        TiledObjectComponent component = world.getMapper(TiledObjectComponent.class).create(first);
        component.kind = TiledObjectComponent.Kind.RECTANGLE;
        component.className = "Trigger";
        world.delete(first);
        world.process();

        int second = world.create();
        TiledObjectComponent reused = world.getMapper(TiledObjectComponent.class).create(second);

        assertEquals(TiledObjectComponent.Kind.UNKNOWN, reused.kind);
        assertEquals("", reused.className);
        world.dispose();
    }

    @Test
    public void allSupportedKindsRemainRepresentable() {
        assertEquals(TiledObjectComponent.Kind.RECTANGLE, TiledObjectComponent.Kind.valueOf("RECTANGLE"));
        assertEquals(TiledObjectComponent.Kind.POINT, TiledObjectComponent.Kind.valueOf("POINT"));
        assertEquals(TiledObjectComponent.Kind.TILE, TiledObjectComponent.Kind.valueOf("TILE"));
    }
}
