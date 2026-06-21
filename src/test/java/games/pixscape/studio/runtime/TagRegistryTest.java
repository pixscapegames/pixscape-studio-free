package games.pixscape.studio.runtime;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.service.TagRegistry;
import org.junit.Assert;
import org.junit.Test;

public class TagRegistryTest {
    @Test
    public void replaceTagsWritesComponent() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();

        TagRegistry registry = new TagRegistry();
        registry.bind(world);
        registry.rebuild();
        registry.setTags(entityId, " alpha ", "", "beta");

        PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class).get(entityId);
        Assert.assertNotNull(tags);
        Assert.assertEquals(2, tags.tags.size);
        Assert.assertEquals("alpha", tags.tags.get(0));
        Assert.assertEquals("beta", tags.tags.get(1));
    }
}
