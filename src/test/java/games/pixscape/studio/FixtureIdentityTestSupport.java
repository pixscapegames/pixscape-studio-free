package games.pixscape.studio;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;

public final class FixtureIdentityTestSupport {
    private FixtureIdentityTestSupport() {
    }

    public static World newWorld() {
        return new World(configuration());
    }

    public static WorldConfiguration configuration() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.name = "test-scene";
        return new WorldConfiguration().setSystem(new FixtureIdAllocatorSystem(meta));
    }

    public static int allocate(World world) {
        FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
        if (allocator == null) throw new IllegalStateException("Test world has no fixture ID allocator");
        return allocator.allocateNewFixtureId();
    }

    public static FixtureDefData createFixture(World world) {
        FixtureDefData fixture = new FixtureDefData();
        PhysicsService.initDefaultFixture(fixture);
        fixture.fixtureId = allocate(world);
        return fixture;
    }

    public static void loadScene(World world, FileHandle file, boolean clearFirst) {
        FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
        if (allocator == null) throw new IllegalStateException("Test world has no fixture ID allocator");
        SceneLoader.loadScene(world, file, clearFirst, allocator.sceneMeta());
    }

    public static void copyHighWater(World source, World target) {
        FixtureIdAllocatorSystem sourceAllocator = source.getSystem(FixtureIdAllocatorSystem.class);
        FixtureIdAllocatorSystem targetAllocator = target.getSystem(FixtureIdAllocatorSystem.class);
        if (sourceAllocator == null || targetAllocator == null) {
            throw new IllegalStateException("Both test worlds require fixture ID allocators");
        }
        targetAllocator.sceneMeta().nextFixtureId = sourceAllocator.sceneMeta().nextFixtureId;
    }
}
