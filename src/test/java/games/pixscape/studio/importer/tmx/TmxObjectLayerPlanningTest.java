package games.pixscape.studio.importer.tmx;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.component.LayerComponent;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
                  <object id="6"><text>Hello</text></object>
                  <object id="7" name="Point"><point/></object>
                </objectgroup>
                """);

        TmxImportPlanResult result = plan(tmx);
        TmxObjectLayerPlan layer = firstObjectLayer(result);

        assertTrue(result.hasPlan());
        assertEquals(List.of("Rectangle", "Point"),
                layer.objects().stream().map(TmxObjectPlan::name).toList());
        assertEquals(4, result.preflightReport().diagnostics().stream()
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
    public void tileObjectPlanResolvesGidFlagsAlignmentOffsetsAndNativeSize() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-tile-object");
        writeFile(dir.resolve("first.png"), "fake image");
        writeFile(dir.resolve("second.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="first" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                    <image source="first.png" width="32" height="16"/>
                  </tileset>
                  <tileset firstgid="20" name="second" tilewidth="16" tileheight="16" tilecount="3" columns="0"
                           objectalignment="topright">
                    <tileoffset x="4" y="5"/>
                    <tile id="2"><image source="second.png" width="23" height="29"/></tile>
                  </tileset>
                  <objectgroup name="Actors" draworder="index">
                    <object id="9" name="Tree" gid="3221225494" x="5" y="6" width="46" height="58" rotation="90"/>
                  </objectgroup>
                </map>
                """);

        TmxImportPlanResult result = plan(tmx);
        TmxObjectLayerPlan layer = firstObjectLayer(result);
        TmxObjectPlan object = layer.objects().get(0);

        assertTrue(result.hasPlan());
        assertEquals(TmxObjectDrawOrder.INDEX, layer.drawOrder());
        assertEquals(TmxObjectKind.TILE, object.kind());
        assertEquals(1, object.tilesetPlanIndex());
        assertEquals(2, object.localTileId());
        assertEquals(22, object.cleanGid());
        assertTrue(object.tileTransform().horizontalFlip());
        assertTrue(object.tileTransform().verticalFlip());
        assertFalse(object.tileTransform().diagonalFlip());
        assertEquals(TmxObjectAlignment.TOP_RIGHT, object.tileObjectAlignment());
        assertEquals(4, object.tileOffsetX());
        assertEquals(5, object.tileOffsetY());
        assertEquals(23, object.nativeTileWidth());
        assertEquals(29, object.nativeTileHeight());
        assertEquals(0, object.sourceOrder());
        assertEquals(0, object.zIndex());
    }

    @Test
    public void objectDrawOrdersProduceDeterministicZIndices() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-object-draw-order");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Index" draworder="index">
                  <object name="A" y="20"><point/></object>
                  <object name="B" y="5"><point/></object>
                </objectgroup>
                <objectgroup name="Topdown">
                  <object name="C" y="20"><point/></object>
                  <object name="D" y="5"><point/></object>
                  <object name="E" y="20"><point/></object>
                </objectgroup>
                """);

        TmxImportPlan plan = plan(tmx).plan();
        TmxObjectLayerPlan index = (TmxObjectLayerPlan) plan.layers().get(0);
        TmxObjectLayerPlan topdown = (TmxObjectLayerPlan) plan.layers().get(1);

        assertEquals(List.of(0, 1), index.objects().stream().map(TmxObjectPlan::zIndex).toList());
        assertEquals(List.of(1, 0, 2), topdown.objects().stream().map(TmxObjectPlan::zIndex).toList());
    }

    @Test
    public void tileObjectPlansResolveEffectiveClassificationAndPropertyInheritance() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-tile-inheritance");
        writeFile(dir.resolve("inline.png"), "fake image");
        writeFile(dir.resolve("external.png"), "fake image");
        writeFile(dir.resolve("external.tsx"), """
                <tileset name="external" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                  <image source="external.png" width="16" height="16"/>
                  <tile id="0" class="House"><properties>
                    <property name="animation_speed" type="float" value="0.6"/>
                  </properties></tile>
                </tileset>
                """);
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="inline" tilewidth="16" tileheight="16" tilecount="3" columns="3">
                    <image source="inline.png" width="48" height="16"/>
                    <tile id="0" class="Gem"><properties>
                      <property name="label" value="source"/>
                      <property name="collectible" type="bool" value="true"/>
                      <property name="damage" type="int" value="10"/>
                      <property name="tint" type="color" value="#40010203"/>
                      <property name="speed" type="float" value="0.6"/>
                    </properties></tile>
                    <tile id="1" type="LegacyPickup"/>
                  </tileset>
                  <tileset firstgid="10" source="external.tsx"/>
                  <objectgroup name="Actors">
                    <object name="Inherited" gid="1"><properties>
                      <property name="damage" type="int" value="25"/>
                      <property name="instance" value="yes"/>
                    </properties></object>
                    <object name="Legacy" gid="2"/>
                    <object name="ClassOverride" class="Boss" gid="1"/>
                    <object name="LegacyOverride" type="Enemy" gid="1"/>
                    <object name="None" gid="3"/>
                    <object name="External" gid="10"/>
                  </objectgroup>
                </map>
                """);

        TmxImportPlan plan = plan(tmx).plan();
        TmxObjectLayerPlan layer = (TmxObjectLayerPlan) plan.layers().get(0);
        Map<String, TmxObjectPlan> objects = new HashMap<>();
        for (TmxObjectPlan object : layer.objects()) objects.put(object.name(), object);

        assertEquals("Gem", TmxSceneImportService.classificationTag(objects.get("Inherited")));
        assertEquals("LegacyPickup", TmxSceneImportService.classificationTag(objects.get("Legacy")));
        assertEquals("Boss", TmxSceneImportService.classificationTag(objects.get("ClassOverride")));
        assertEquals("Enemy", TmxSceneImportService.classificationTag(objects.get("LegacyOverride")));
        assertNull(TmxSceneImportService.classificationTag(objects.get("None")));
        assertEquals("House", TmxSceneImportService.classificationTag(objects.get("External")));

        TmxObjectPlan inherited = objects.get("Inherited");
        assertEquals("source", inherited.properties().getString("label", null));
        assertTrue(inherited.properties().getBoolean("collectible", false));
        assertEquals(25, inherited.properties().getInt("damage", 0));
        assertEquals(0.6f, inherited.properties().getFloat("speed", 0f), 0.0001f);
        assertEquals("yes", inherited.properties().getString("instance", null));
        assertEquals(0.6f, objects.get("External").properties()
                .getFloat("animation_speed", 0f), 0.0001f);

        TmxTileDefinitionPlan sourceTile = plan.tilesets().get(0).tileDefinition(0);
        assertNotSame(sourceTile.properties(), inherited.properties());
        assertEquals(10, sourceTile.properties().getInt("damage", 0));
    }

    @Test
    public void tileObjectClassPropertiesMergeExplicitSameClassMembersRecursively() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-class-inheritance");
        writeFile(dir.resolve("tiles.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="actors" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="tiles.png" width="16" height="16"/>
                    <tile id="0"><properties>
                      <property name="physics" type="class" propertytype="Physics"><properties>
                        <property name="mass" type="float" value="1"/>
                        <property name="friction" type="float" value="0.5"/>
                        <property name="material" type="class" propertytype="Material"><properties>
                          <property name="restitution" type="float" value="0.2"/>
                          <property name="density" type="float" value="1"/>
                          <property name="tint" type="color" value="#40010203"/>
                        </properties></property>
                      </properties></property>
                      <property name="damage" type="int" value="10"/>
                      <property name="tint" type="color" value="#40010203"/>
                    </properties></tile>
                  </tileset>
                  <objectgroup name="Actors">
                    <object name="Partial" gid="1"><properties>
                      <property name="physics" type="class" propertytype="Physics"><properties>
                        <property name="mass" type="float" value="2"/>
                        <property name="material" type="class" propertytype="Material"><properties>
                          <property name="restitution" type="float" value="0.8"/>
                        </properties></property>
                      </properties></property>
                      <property name="damage" type="int" value="20"/>
                      <property name="tint" type="color" value="#800A0B0C"/>
                    </properties></object>
                    <object name="Different" gid="1"><properties>
                      <property name="physics" type="class" propertytype="Behavior"><properties>
                        <property name="enabled" type="bool" value="true"/>
                      </properties></property>
                    </properties></object>
                  </objectgroup>
                </map>
                """);

        TmxImportPlan plan = plan(tmx).plan();
        TmxObjectLayerPlan layer = (TmxObjectLayerPlan) plan.layers().get(0);
        TmxObjectPlan partial = layer.objects().get(0);
        ClassProperty physics = partial.properties().getClassValue("physics");
        assertEquals("Physics", physics.typeName());
        assertEquals(2f, physics.properties().getFloat("mass", 0f), 0.0001f);
        assertEquals(0.5f, physics.properties().getFloat("friction", 0f), 0.0001f);
        ClassProperty material = physics.properties().getClassValue("material");
        assertEquals(0.8f, material.properties().getFloat("restitution", 0f), 0.0001f);
        assertEquals(1f, material.properties().getFloat("density", 0f), 0.0001f);
        assertEquals(0x01020340, material.properties().getColorRgba8888("tint", 0));
        assertEquals(20, partial.properties().getInt("damage", 0));
        assertEquals(0x0A0B0C80, partial.properties().getColorRgba8888("tint", 0));

        ClassProperty replacement = layer.objects().get(1).properties().getClassValue("physics");
        assertEquals("Behavior", replacement.typeName());
        assertTrue(replacement.properties().getBoolean("enabled", false));
        assertFalse(replacement.properties().contains("mass"));

        ClassProperty source = plan.tilesets().get(0).tileDefinition(0)
                .properties().getClassValue("physics");
        assertEquals(1f, source.properties().getFloat("mass", 0f), 0.0001f);
        assertEquals(0.2f, source.properties().getClassValue("material")
                .properties().getFloat("restitution", 0f), 0.0001f);
        assertEquals(0x01020340, plan.tilesets().get(0).tileDefinition(0)
                .properties().getColorRgba8888("tint", 0));
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
