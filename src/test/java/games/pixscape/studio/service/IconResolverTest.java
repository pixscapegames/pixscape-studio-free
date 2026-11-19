package games.pixscape.studio.service;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotSame;

public class IconResolverTest {
    private final World world = new World();
    private final IconResolver resolver = new IconResolver(world);

    @After
    public void tearDown() {
        world.dispose();
    }

    @Test
    public void mapWithoutEditorMetadataIsRecognizedAsMap() {
        int entity = world.create();
        world.getMapper(TiledMapComponent.class).create(entity);

        assertEquals(EntityKind.TILED_MAP, resolver.kindForEntity(entity));
    }

    @Test
    public void runtimeComponentsDistinguishMapsAndTileLayersDespiteOldMetadata() {
        int map = world.create();
        world.getMapper(TiledMapComponent.class).create(map);
        world.getMapper(EntityMetaComponent.class).create(map).kind = EntityKind.GAME_OBJECT;
        int layer = world.create();
        world.getMapper(TiledLayerComponent.class).create(layer);
        world.getMapper(EntityMetaComponent.class).create(layer).kind = EntityKind.TILED_MAP;

        assertEquals(EntityKind.TILED_MAP, resolver.kindForEntity(map));
        assertEquals(EntityKind.TILED_LAYER, resolver.kindForEntity(layer));
    }

    @Test
    public void tiledObjectsUseDedicatedDrawablesFromTheStudioSkin() {
        games.pixscape.studio.ui.widget.VisUiTestBootstrap.loadSkin();
        try {
            var skin = com.kotcrab.vis.ui.VisUI.getSkin();
            EntityKind[] kinds = {EntityKind.TILED_POINT, EntityKind.POLYLINE,
                    EntityKind.TILED_RECTANGLE, EntityKind.POLYGON};
            String[] names = {"tiled-point", "tiled-polyline", "tiled-rectangle", "tiled-polygon"};
            int entity = world.create();
            var meta = world.getMapper(EntityMetaComponent.class).create(entity);
            for (int i = 0; i < kinds.length; i++) {
                meta.kind = kinds[i];
                var expected = skin.getDrawable(names[i]);
                assertSame(expected, resolver.iconForEntity(entity));
                assertSame(expected, IconResolver.iconForEntity(kinds[i]));
                assertNotSame(skin.getDrawable("image_icon16"), expected);
            }
        } finally {
            games.pixscape.studio.ui.widget.VisUiTestBootstrap.unloadSkin();
        }
    }

    @Test
    public void ordinaryEntitiesKeepTheirMetadataTypeOrUnknown() {
        int entity = world.create();
        assertEquals(EntityKind.UNKNOWN, resolver.kindForEntity(entity));
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(entity);
        meta.kind = EntityKind.SPRITE;
        assertEquals(EntityKind.SPRITE, resolver.kindForEntity(entity));
        meta.kind = null;
        assertEquals(EntityKind.UNKNOWN, resolver.kindForEntity(entity));
    }
}
