package games.pixscape.studio.history.initializer;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;
import org.junit.Assert;
import org.junit.Test;

public class AbstractCommonInitializerMigrationTest {

    @Test
    public void snapshotsAndRestoresIdentityTagsAndMeta() {
        World world = new World(new WorldConfiguration());
        int source = world.create();

        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(source);
        identity.name = "Source";
        identity.stableId = 123;

        PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class).create(source);
        tags.tags.add("alpha");

        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(source);
        meta.note = "hello";
        meta.kind = EntityKind.PARTICLE;

        AbstractCommonInitializer initializer = new AbstractCommonInitializer(world) {};
        initializer.syncFrom(source);

        int target = world.create();
        initializer.init(target);

        PixscapeIdentityComponent restoredIdentity = world.getMapper(PixscapeIdentityComponent.class).get(target);
        PixscapeTagComponent restoredTags = world.getMapper(PixscapeTagComponent.class).get(target);
        EntityMetaComponent restoredMeta = world.getMapper(EntityMetaComponent.class).get(target);

        Assert.assertEquals("Source", restoredIdentity.name);
        Assert.assertEquals(123, restoredIdentity.stableId);
        Assert.assertEquals(1, restoredTags.tags.size);
        Assert.assertEquals("alpha", restoredTags.tags.get(0));
        Assert.assertEquals("hello", restoredMeta.note);
        Assert.assertEquals(EntityKind.PARTICLE, restoredMeta.kind);
    }
}
