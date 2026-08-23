package games.pixscape.studio.importer.tmx;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.LayerComponent;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class TmxObjectLayerPlanningTest {

    @Test
    public void emptyObjectLayerCreatesAnEmptyPlan() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-plan-empty-objects"), """
                <objectgroup name="Gameplay"/>
                """);

        TmxImportPlanResult result = plan(tmx);

        assertTrue(result.hasPlan());
        TmxObjectLayerPlan layer = (TmxObjectLayerPlan) result.plan().layers().get(0);
        assertEquals("Gameplay", layer.name());
        assertEquals("Gameplay", layer.originalName());
        assertEquals(0, layer.sourceLayerIndex());
        assertTrue(layer.objects().isEmpty());
    }

    @Test
    public void rectanglePlanPreservesRawMetadataPropertiesAndAbsentSourceId() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-plan-rectangle"), """
                <objectgroup name="Gameplay">
                  <object name="Door" class="Trigger" type="LegacyTrigger" x="10.5" y="20.25"
                          width="32" height="64" rotation="90" visible="0">
                    <properties><property name="locked" type="bool" value="true"/></properties>
                  </object>
                </objectgroup>
                """);

        TmxObjectPlan object = firstObject(plan(tmx));

        assertEquals(TmxObjectInfo.NO_SOURCE_ID, object.sourceId());
        assertFalse(object.hasPositiveSourceId());
        assertEquals("Door", object.name());
        assertEquals("Trigger", object.className());
        assertEquals("LegacyTrigger", object.legacyType());
        assertEquals(10.5f, object.x(), 0.0001f);
        assertEquals(20.25f, object.y(), 0.0001f);
        assertEquals(32f, object.width(), 0.0001f);
        assertEquals(64f, object.height(), 0.0001f);
        assertEquals(90f, object.rotation(), 0.0001f);
        assertFalse(object.visible());
        assertEquals(TmxObjectKind.RECTANGLE, object.kind());
        assertTrue(object.properties().getBoolean("locked", false));
    }

    @Test
    public void pointPlanPreservesSourceCoordinatesAndIdWithoutConversion() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-plan-point"), """
                <objectgroup name="Gameplay">
                  <object id="42" name="Spawn" class="SpawnPoint" type="LegacySpawn"
                          x="123.5" y="456.25"><point/></object>
                </objectgroup>
                """);

        TmxObjectPlan object = firstObject(plan(tmx));

        assertEquals(42, object.sourceId());
        assertTrue(object.hasPositiveSourceId());
        assertEquals(123.5f, object.x(), 0.0001f);
        assertEquals(456.25f, object.y(), 0.0001f);
        assertEquals(0f, object.width(), 0f);
        assertEquals(0f, object.height(), 0f);
        assertEquals(TmxObjectKind.POINT, object.kind());
    }

    @Test
    public void supportedObjectsPreserveSourceOrder() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-plan-object-order"), """
                <objectgroup name="Gameplay">
                  <object id="3" name="First" width="2" height="3"/>
                  <object id="1" name="Second"><point/></object>
                  <object id="2" name="Third" width="4" height="5"/>
                </objectgroup>
                """);

        TmxObjectLayerPlan layer = firstObjectLayer(plan(tmx));

        assertEquals(List.of("First", "Second", "Third"),
                layer.objects().stream().map(TmxObjectPlan::name).toList());
        assertEquals(List.of(TmxObjectKind.RECTANGLE, TmxObjectKind.POINT, TmxObjectKind.RECTANGLE),
                layer.objects().stream().map(TmxObjectPlan::kind).toList());
    }

    @Test
    public void mixedTileAndObjectLayersPreserveGlobalSourceOrder() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-mixed-layer-order");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Below" width="1" height="1"><data encoding="csv">1</data></layer>
                  <objectgroup name="Gameplay"><object id="1"><point/></object></objectgroup>
                  <layer name="Above" width="1" height="1"><data encoding="csv">0</data></layer>
                </map>
                """);

        TmxImportPlan plan = plan(tmx).plan();

        assertEquals(List.of("Below", "Gameplay", "Above"),
                plan.layers().stream().map(TmxLayerPlan::name).toList());
        assertTrue(plan.layers().get(0) instanceof TmxTileLayerPlan);
        assertTrue(plan.layers().get(1) instanceof TmxObjectLayerPlan);
        assertTrue(plan.layers().get(2) instanceof TmxTileLayerPlan);
        assertEquals(List.of(0, 1, 2), plan.layers().stream().map(TmxLayerPlan::sourceLayerIndex).toList());
    }

    @Test
    public void nestedObjectLayerPreservesEffectivePresentationAndProperties() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-plan-nested-objects"), """
                <group name="World" visible="0" opacity="0.5" offsetx="3" offsety="4"
                       parallaxx="2" parallaxy="0.5">
                  <objectgroup name="Gameplay" opacity="0.5" offsetx="5" offsety="6"
                               parallaxx="3" parallaxy="4">
                    <properties><property name="role" value="logic"/></properties>
                    <object id="1" name="Spawn"><point/>
                      <properties><property name="player" type="bool" value="true"/></properties>
                    </object>
                  </objectgroup>
                </group>
                """);

        TmxImportPlanResult result = plan(tmx);
        TmxObjectLayerPlan layer = firstObjectLayer(result);

        assertEquals("World/Gameplay", layer.name());
        assertEquals("Gameplay", layer.originalName());
        assertFalse(layer.visible());
        assertEquals(0.25f, layer.opacity(), 0.0001f);
        assertEquals(8f, layer.offsetX(), 0.0001f);
        assertEquals(10f, layer.offsetY(), 0.0001f);
        assertEquals(6f, layer.parallaxX(), 0.0001f);
        assertEquals(2f, layer.parallaxY(), 0.0001f);
        assertEquals("logic", layer.properties().getString("role", null));
        assertTrue(layer.objects().get(0).properties().getBoolean("player", false));
        assertSame(layer.properties(), layer.properties());
        assertSame(layer.objects().get(0).properties(), layer.objects().get(0).properties());
        TmxObjectLayerInfo preflightLayer = (TmxObjectLayerInfo) result.preflightReport().layers().get(0);
        assertNotSame(preflightLayer.properties(), layer.properties());
        assertNotSame(preflightLayer.objects().get(0).properties(), layer.objects().get(0).properties());
    }

    @Test
    public void deferredObjectKindsAreExcludedWithoutReorderingSupportedObjects() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-plan-deferred-objects"), """
                <objectgroup name="Shapes">
                  <object id="1" name="Rectangle" width="1" height="1"/>
                  <object id="2"><ellipse/></object>
                  <object id="3"><polygon points="0,0 8,0 8,8"/></object>
                  <object id="4"><polyline points="0,0 8,8"/></object>
                  <object id="5" gid="123"/>
                  <object id="6"><text>Hello</text></object>
                  <object id="7" name="Point"><point/></object>
                </objectgroup>
                """);

        TmxImportPlanResult result = plan(tmx);
        TmxObjectLayerPlan layer = firstObjectLayer(result);

        assertTrue(result.hasPlan());
        assertEquals(List.of("Rectangle", "Point"),
                layer.objects().stream().map(TmxObjectPlan::name).toList());
        assertEquals(5, result.preflightReport().diagnostics().stream()
                .filter(d -> d.code().equals("TMX_OBJECT_KIND_DEFERRED"))
                .count());
    }

    @Test
    public void blockingObjectPreflightPreventsPlanIncludingIsometricLayers() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-blocked-objects");
        FileHandle isometric = writeFile(dir.resolve("isometric.tmx"), """
                <map orientation="isometric" width="1" height="1" tilewidth="16" tileheight="16">
                  <objectgroup name="Gameplay"><object id="1"><point/></object></objectgroup>
                </map>
                """);
        FileHandle template = writeMap(dir, """
                <objectgroup name="Templates"><object id="1" template="enemy.tx"/></objectgroup>
                """);
        FileHandle invalidProperty = writeFile(dir.resolve("invalid-property.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <objectgroup name="Invalid"><object id="1"><properties>
                    <property name="bad" type="int" value="not-an-int"/>
                  </properties></object></objectgroup>
                </map>
                """);

        TmxImportPlanResult isometricResult = plan(isometric);
        TmxImportPlanResult templateResult = plan(template);
        TmxImportPlanResult invalidPropertyResult = plan(invalidProperty);

        assertEquals(TmxImportPlanStatus.PREFLIGHT_FAILED, isometricResult.status());
        assertNull(isometricResult.plan());
        assertTrue(hasBlocking(isometricResult, "TMX_OBJECT_LAYER_ISOMETRIC_UNSUPPORTED"));
        assertEquals(TmxImportPlanStatus.PREFLIGHT_FAILED, templateResult.status());
        assertNull(templateResult.plan());
        assertTrue(hasBlocking(templateResult, "TMX_OBJECT_TEMPLATE_UNSUPPORTED"));
        assertEquals(TmxImportPlanStatus.PREFLIGHT_FAILED, invalidPropertyResult.status());
        assertNull(invalidPropertyResult.plan());
        assertTrue(hasBlocking(invalidPropertyResult, "TMX_PROPERTY_VALUE_INVALID"));
    }

    @Test
    public void planningDoesNotMutateWorldOrCreateProjectArtifacts() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-no-mutation");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Gameplay"><object id="1" width="4" height="5"/></objectgroup>
                """);
        World world = new World(new WorldConfiguration());
        int existingEntity = world.create();
        world.getMapper(LayerComponent.class).create(existingEntity);
        world.process();
        int layerCountBefore = world.getAspectSubscriptionManager()
                .get(Aspect.all(LayerComponent.class))
                .getEntities()
                .size();

        TmxImportPlanResult result;
        try {
            result = plan(tmx);
            world.process();
            assertTrue(world.getEntityManager().isActive(existingEntity));
            assertEquals(layerCountBefore, world.getAspectSubscriptionManager()
                    .get(Aspect.all(LayerComponent.class))
                    .getEntities()
                    .size());
        } finally {
            world.dispose();
        }

        assertTrue(result.hasPlan());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("map.tmx"), files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    private static TmxImportPlanResult plan(FileHandle tmx) {
        return new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx));
    }

    private static TmxObjectLayerPlan firstObjectLayer(TmxImportPlanResult result) {
        assertNotNull(result.plan());
        return (TmxObjectLayerPlan) result.plan().layers().get(0);
    }

    private static TmxObjectPlan firstObject(TmxImportPlanResult result) {
        return firstObjectLayer(result).objects().get(0);
    }

    private static boolean hasBlocking(TmxImportPlanResult result, String code) {
        return result.preflightReport().diagnostics().stream()
                .anyMatch(d -> d.severity() == TmxDiagnosticSeverity.BLOCKING && d.code().equals(code));
    }

    private static FileHandle writeMap(Path dir, String layers) throws Exception {
        return writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                %s
                </map>
                """.formatted(layers));
    }

    private static FileHandle writeFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }
}
