package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class TmxObjectLayerPreflightTest {

    @Test
    public void objectLayersPreserveOrderPropertiesAndAccumulatedGroupState() throws Exception {
        Path dir = Files.createTempDirectory("tmx-object-layers");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Empty"/>
                <layer name="Ground" width="1" height="1"><data encoding="csv">0</data></layer>
                <group name="World" visible="0" opacity="0.5" offsetx="3" offsety="4" parallaxx="2" parallaxy="0.25">
                  <objectgroup name="Gameplay" opacity="0.5" offsetx="5" offsety="6" parallaxx="3" parallaxy="2">
                    <properties>
                      <property name="Category" value="gameplay"/>
                    </properties>
                  </objectgroup>
                </group>
                <objectgroup name="Last"/>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        assertEquals(List.of("Empty", "Ground", "World/Gameplay", "Last"),
                report.layers().stream().map(TmxLayerInfo::name).toList());
        assertTrue(report.layers().get(0) instanceof TmxObjectLayerInfo);
        assertTrue(report.layers().get(1) instanceof TmxTileLayerInfo);
        TmxObjectLayerInfo gameplay = (TmxObjectLayerInfo) report.layers().get(2);
        assertEquals("Gameplay", gameplay.originalName());
        assertFalse(gameplay.visible());
        assertEquals(0.25f, gameplay.opacity(), 0.0001f);
        assertEquals(8f, gameplay.offsetX(), 0.0001f);
        assertEquals(10f, gameplay.offsetY(), 0.0001f);
        assertEquals(6f, gameplay.parallaxX(), 0.0001f);
        assertEquals(0.5f, gameplay.parallaxY(), 0.0001f);
        assertEquals("gameplay", gameplay.properties().getString("Category", null));
        assertTrue(gameplay.objects().isEmpty());
    }

    @Test
    public void rectanglesAndPointsPreserveSourceMetadataAndOrder() throws Exception {
        Path dir = Files.createTempDirectory("tmx-object-metadata");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Gameplay">
                  <object id="1" name="Door" class="Trigger" type="LegacyTrigger" x="10.5" y="20.25"
                          width="32" height="64" rotation="90" visible="0">
                    <properties><property name="locked" type="bool" value="true"/></properties>
                  </object>
                  <object id="2" name="" class="Spawn" type="Spawn" x="4" y="8"><point/></object>
                  <object id="3" class="ClassOnly"/>
                  <object id="4" type="LegacyOnly"/>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        assertEquals(4, layer.objects().size());
        TmxObjectInfo rectangle = layer.objects().get(0);
        assertEquals(TmxObjectKind.RECTANGLE, rectangle.kind());
        assertEquals(1, rectangle.id());
        assertEquals("Door", rectangle.name());
        assertEquals("Trigger", rectangle.className());
        assertEquals("LegacyTrigger", rectangle.legacyType());
        assertEquals(10.5f, rectangle.x(), 0.0001f);
        assertEquals(20.25f, rectangle.y(), 0.0001f);
        assertEquals(32f, rectangle.width(), 0.0001f);
        assertEquals(64f, rectangle.height(), 0.0001f);
        assertEquals(90f, rectangle.rotation(), 0.0001f);
        assertFalse(rectangle.visible());
        assertTrue(rectangle.properties().getBoolean("locked", false));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_CLASS_TYPE_CONFLICT"));

        TmxObjectInfo point = layer.objects().get(1);
        assertEquals(TmxObjectKind.POINT, point.kind());
        assertEquals(2, point.id());
        assertEquals("", point.name());
        assertEquals("Spawn", point.className());
        assertEquals("Spawn", point.legacyType());
        assertEquals(0f, point.width(), 0f);
        assertEquals(0f, point.height(), 0f);
        assertFalse(report.diagnostics().stream()
                .anyMatch(d -> d.code().equals("TMX_OBJECT_CLASS_TYPE_CONFLICT") && d.location().contains("#2")));
        assertEquals("ClassOnly", layer.objects().get(2).className());
        assertNull(layer.objects().get(2).legacyType());
        assertNull(layer.objects().get(3).className());
        assertEquals("LegacyOnly", layer.objects().get(3).legacyType());
    }

    @Test
    public void supportedDeferredAndBlockingObjectKindsAreDistinguished() throws Exception {
        Path dir = Files.createTempDirectory("tmx-object-kinds");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Shapes">
                  <object id="1"><ellipse/></object>
                  <object id="2"><polygon points="0,0 8,0 8,8"/></object>
                  <object id="3"><polyline points="0,0 8,8"/></object>
                  <object id="5"><text>Hello</text></object>
                  <object id="6" template="enemy.tx"/>
                  <object id="7" gid="1"><point/></object>
                  <object id="8"><unknownshape/></object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        assertEquals(List.of(
                        TmxObjectKind.ELLIPSE,
                        TmxObjectKind.POLYGON,
                        TmxObjectKind.POLYLINE,
                        TmxObjectKind.TEXT,
                        TmxObjectKind.TEMPLATE,
                        TmxObjectKind.UNKNOWN,
                        TmxObjectKind.UNKNOWN),
                layer.objects().stream().map(TmxObjectInfo::kind).toList());
        assertEquals("enemy.tx", layer.objects().get(4).template());
        assertEquals(2, diagnosticCount(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_KIND_DEFERRED"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_POLYGON_POINTS_INVALID"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_POLYLINE_POINTS_INVALID"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_OBJECT_TEMPLATE_UNSUPPORTED"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_OBJECT_KIND_AMBIGUOUS"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_OBJECT_KIND_UNKNOWN"));
    }

    @Test
    public void polygonAndPolylinePointsAreStrictlyParsedAsImmutableSourceLocalGeometry()
            throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-object-path-points"), """
                <objectgroup name="Shapes">
                  <object id="10" name="Polygon" class="Area" type="LegacyArea" rotation="30">
                    <polygon points="-10,5 20.5,-7 40,12"/>
                    <properties><property name="target" type="object" value="11"/></properties>
                  </object>
                  <object id="11" name="Polyline"><polyline points="0,0 1.25,2.5"/></object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        TmxObjectInfo polygon = ((TmxObjectLayerInfo) report.layers().get(0)).objects().get(0);
        TmxObjectInfo polyline = ((TmxObjectLayerInfo) report.layers().get(0)).objects().get(1);
        assertEquals(TmxObjectKind.POLYGON, polygon.kind());
        assertEquals(List.of(new TmxObjectPoint(-10f, 5f), new TmxObjectPoint(20.5f, -7f),
                new TmxObjectPoint(40f, 12f)), polygon.points());
        assertEquals(30f, polygon.rotation(), 0f);
        assertEquals("Area", polygon.className());
        assertEquals("LegacyArea", polygon.legacyType());
        assertEquals(TmxObjectKind.POLYLINE, polyline.kind());
        assertEquals(List.of(new TmxObjectPoint(0f, 0f), new TmxObjectPoint(1.25f, 2.5f)),
                polyline.points());
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_KIND_DEFERRED"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_OBJECT_REFERENCE_TARGET_UNSUPPORTED"));
    }

    @Test
    public void malformedPolygonAndPolylinePointsBlockPreflight() throws Exception {
        FileHandle tmx = writeMap(Files.createTempDirectory("tmx-object-invalid-path-points"), """
                <objectgroup name="Shapes">
                  <object id="1"><polygon points="0,0 1,1"/></object>
                  <object id="2"><polyline points="0,0 1,"/></object>
                  <object id="3"><polygon points="NaN,0 1,1 2,2"/></object>
                  <object id="4"><polyline points="Infinity,0 1,1"/></object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertTrue(report.hasBlockingDiagnostics());
        assertEquals(2, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_POLYGON_POINTS_INVALID"));
        assertEquals(2, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_POLYLINE_POINTS_INVALID"));
    }

    @Test
    public void unusableAndDuplicateObjectIdsDoNotBlockPreflight() throws Exception {
        Path dir = Files.createTempDirectory("tmx-object-ids");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="First">
                  <object name="Missing"/>
                  <object id="bad"/>
                  <object id="-1"/>
                  <object id="0"/>
                  <object id="7"/>
                </objectgroup>
                <group name="Nested"><objectgroup name="Second"><object id="7" name="Duplicate"/></objectgroup></group>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        TmxObjectLayerInfo first = (TmxObjectLayerInfo) report.layers().get(0);
        assertEquals(TmxObjectInfo.NO_SOURCE_ID, first.objects().get(0).id());
        assertEquals(TmxObjectInfo.NO_SOURCE_ID, first.objects().get(1).id());
        assertEquals(TmxObjectInfo.NO_SOURCE_ID, first.objects().get(2).id());
        assertEquals(TmxObjectInfo.NO_SOURCE_ID, first.objects().get(3).id());
        assertFalse(first.objects().get(0).hasPositiveSourceId());
        assertTrue(first.objects().get(4).hasPositiveSourceId());
        TmxObjectLayerInfo second = (TmxObjectLayerInfo) report.layers().get(1);
        assertEquals(7, second.objects().get(0).id());
        assertTrue(second.objects().get(0).hasPositiveSourceId());
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_ID_INVALID"));
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_ID_DUPLICATE"));
        assertTrue(report.diagnostics().stream()
                .anyMatch(d -> d.code().equals("TMX_OBJECT_ID_DUPLICATE")
                        && d.location().contains("Nested/Second")
                        && d.location().contains("Duplicate")));
    }

    @Test
    public void primitivePropertiesUseRuntimeTypesDefaultsAndExactNames() throws Exception {
        Path dir = Files.createTempDirectory("tmx-object-properties");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Gameplay">
                  <properties>
                    <property name="Title" value="Layer"/>
                    <property name="title">lowercase</property>
                  </properties>
                  <object id="1">
                    <properties>
                      <property name="empty"/>
                      <property name="multiline">First line
Second line</property>
                      <property name="enabled" type="bool" value="true"/>
                      <property name="disabled" type="bool"/>
                      <property name="positive" type="int" value="12"/>
                      <property name="negative" type="int" value="-9"/>
                      <property name="zero" type="int"/>
                      <property name="ratio" type="float" value="1.25"/>
                      <property name="defaultFloat" type="float"/>
                      <property name="tint" type="color" value="#80FF0000"/>
                      <property name="defaultTint" type="color"/>
                    </properties>
                  </object>
                  <object id="2"><properties><property name="enabled" type="bool" value="false"/></properties></object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        PropertySet layerProperties = layer.properties();
        assertEquals(2, layerProperties.size());
        assertEquals("Layer", layerProperties.getString("Title", null));
        assertEquals("lowercase", layerProperties.getString("title", null));
        layerProperties.putString("Title", "mutated copy");
        assertEquals("Layer", layer.properties().getString("Title", null));

        PropertySet properties = layer.objects().get(0).properties();
        assertEquals(PropertyType.STRING, properties.typeOf("empty"));
        assertEquals("", properties.getString("empty", null));
        assertEquals("First line\nSecond line", properties.getString("multiline", null));
        assertTrue(properties.getBoolean("enabled", false));
        assertFalse(properties.getBoolean("disabled", true));
        assertEquals(12, properties.getInt("positive", 0));
        assertEquals(-9, properties.getInt("negative", 0));
        assertEquals(0, properties.getInt("zero", 1));
        assertEquals(1.25f, properties.getFloat("ratio", 0f), 0.0001f);
        assertEquals(0f, properties.getFloat("defaultFloat", 1f), 0f);
        assertEquals(PropertyType.COLOR, properties.typeOf("tint"));
        assertEquals(0xFF000080, properties.getColorRgba8888("tint", 0));
        assertEquals(0, properties.getColorRgba8888("defaultTint", -1));
        assertFalse(layer.objects().get(1).properties().getBoolean("enabled", true));
        properties.putString("new", "mutated copy");
        assertFalse(layer.objects().get(0).properties().contains("new"));
    }

    @Test
    public void classPropertiesParseEmptyPrimitiveAndNestedMembersRecursively() throws Exception {
        Path dir = Files.createTempDirectory("tmx-class-properties");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Gameplay">
                  <properties><property name="empty" type="class" propertytype="Attack"/></properties>
                  <object id="1" name="Rectangle"><properties>
                    <property name="physics" type="class" propertytype=" Physics "><properties>
                      <property name="label" value="heavy"/>
                      <property name="sensor" type="bool" value="true"/>
                      <property name="count" type="int" value="3"/>
                      <property name="mass" type="float" value="2.5"/>
                      <property name="tint" type="color" value="#40204080"/>
                      <property name="material" type="class" propertytype="Material"><properties>
                        <property name="friction" type="float" value="0.5"/>
                      </properties></property>
                    </properties></property>
                  </properties></object>
                  <object id="2" name="Point"><point/><properties>
                    <property name="follow" type="class" propertytype="Follow"/>
                  </properties></object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        ClassProperty empty = layer.properties().getClassValue("empty");
        assertEquals("Attack", empty.typeName());
        assertTrue(empty.properties().isEmpty());
        ClassProperty physics = layer.objects().get(0).properties().getClassValue("physics");
        assertEquals(" Physics", physics.typeName());
        assertEquals("heavy", physics.properties().getString("label", null));
        assertTrue(physics.properties().getBoolean("sensor", false));
        assertEquals(3, physics.properties().getInt("count", 0));
        assertEquals(2.5f, physics.properties().getFloat("mass", 0f), 0.0001f);
        assertEquals(0x20408040, physics.properties().getColorRgba8888("tint", 0));
        ClassProperty material = physics.properties().getClassValue("material");
        assertEquals("Material", material.typeName());
        assertEquals(0.5f, material.properties().getFloat("friction", 0f), 0.0001f);
        assertEquals("Follow", layer.objects().get(1).properties()
                .getClassValue("follow").typeName());
    }

    @Test
    public void customEnumMetadataKeepsTheAuthoritativePrimitiveType() throws Exception {
        Path dir = Files.createTempDirectory("tmx-custom-enum-properties");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Gameplay"><object id="1"><properties>
                  <property name="direction" type="string" propertytype="Direction" value="North"/>
                  <property name="flags" type="int" propertytype="Flags" value="3"/>
                </properties></object></objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        PropertySet properties = ((TmxObjectLayerInfo) report.layers().get(0)).objects().get(0).properties();
        assertEquals(PropertyType.STRING, properties.typeOf("direction"));
        assertEquals("North", properties.getString("direction", null));
        assertEquals(PropertyType.INTEGER, properties.typeOf("flags"));
        assertEquals(3, properties.getInt("flags", 0));
    }

    @Test
    public void tileDefinitionClassPropertiesUseTheSameParserForExternalAndImageCollectionTilesets() throws Exception {
        Path dir = Files.createTempDirectory("tmx-class-tile-metadata");
        writeFile(dir.resolve("external.png"), "fake image");
        writeFile(dir.resolve("collection.png"), "fake image");
        writeFile(dir.resolve("actors.tsx"), """
                <tileset name="actors" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                  <image source="external.png" width="16" height="16"/>
                  <tile id="0"><properties><property name="attack" type="class" propertytype="Attack"><properties>
                    <property name="damage" type="int" value="20"/>
                  </properties></property></properties></tile>
                </tileset>
                """);
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" source="actors.tsx"/>
                  <tileset firstgid="10" name="collection" tilewidth="16" tileheight="16" tilecount="1" columns="0">
                    <tile id="0"><image source="collection.png" width="12" height="14"/><properties>
                      <property name="wander" type="class" propertytype="Wander"/>
                    </properties></tile>
                  </tileset>
                  <objectgroup name="Actors"><object gid="1"/><object gid="10"/></objectgroup>
                </map>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING,
                "TMX_CUSTOM_PROPERTIES_IGNORED"));
        ClassProperty attack = report.tilesets().get(0).tileDefinition(0)
                .properties().getClassValue("attack");
        assertEquals("Attack", attack.typeName());
        assertEquals(20, attack.properties().getInt("damage", 0));
        ClassProperty wander = report.tilesets().get(1).tileDefinition(0)
                .properties().getClassValue("wander");
        assertEquals("Wander", wander.typeName());
        assertTrue(wander.properties().isEmpty());
    }

    @Test
    public void malformedAndUnsupportedPropertiesProduceBlockingDiagnostics() throws Exception {
        Path dir = Files.createTempDirectory("tmx-invalid-properties");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Invalid">
                  <object id="1">
                    <properties>
                      <property name="duplicate" value="first"/>
                      <property name="duplicate" value="second"/>
                      <property name="" value="empty"/>
                      <property name="   " value="blank"/>
                      <property value="missing"/>
                      <property name="badBool" type="bool" value="1"/>
                      <property name="badInt" type="int" value="twelve"/>
                      <property name="overflow" type="int" value="2147483648"/>
                      <property name="badFloat" type="float" value="many"/>
                      <property name="nan" type="float" value="NaN"/>
                      <property name="infinity" type="float" value="Infinity"/>
                      <property name="overflowFloat" type="float" value="1e100"/>
                      <property name="color" type="color" value="#fff"/>
                      <property name="emptyColor" type="color" value=""/>
                      <property name="file" type="file" value="asset.png"/>
                      <property name="reference" type="object" value="2"/>
                      <property name="classValue" type="class"/>
                      <property name="listValue" type="list"/>
                      <property name="customEnum" propertytype="Direction" value="North"/>
                      <property name="emptyType" type="" value="value"/>
                    </properties>
                  </object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertTrue(report.hasBlockingDiagnostics());
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_PROPERTY_NAME_DUPLICATE"));
        assertEquals(3, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_PROPERTY_NAME_INVALID"));
        assertEquals(9, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_PROPERTY_VALUE_INVALID"));
        assertEquals(3, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_PROPERTY_TYPE_UNSUPPORTED"));
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_OBJECT_REFERENCE_UNRESOLVED"));
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_CLASS_PROPERTY_TYPE_INVALID"));
        assertTrue(report.diagnostics().stream()
                .anyMatch(d -> d.location().contains("property 'overflowFloat'") && d.message().contains("finite Java float")));
    }

    @Test
    public void malformedAndUnsupportedNestedClassMembersReportTheirFullPaths() throws Exception {
        Path dir = Files.createTempDirectory("tmx-invalid-class-members");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Gameplay"><object id="1"><properties>
                  <property name="physics" type="class" propertytype="Physics"><properties>
                    <property name="mass" type="float" value="1"/>
                    <property name="mass" type="float" value="2"/>
                    <property name="collisionColor" type="color" value="#ffffffff"/>
                    <property name="source" type="file" value="physics.json"/>
                    <property name="target" type="object" value="2"/>
                    <property name="states" type="list"/>
                    <property name="material" type="class" propertytype=" "/>
                  </properties></property>
                </properties></object></objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertTrue(report.hasBlockingDiagnostics());
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_PROPERTY_NAME_DUPLICATE"));
        assertEquals(2, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_PROPERTY_TYPE_UNSUPPORTED"));
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_OBJECT_REFERENCE_UNRESOLVED"));
        assertEquals(1, diagnosticCount(report, TmxDiagnosticSeverity.BLOCKING, "TMX_CLASS_PROPERTY_TYPE_INVALID"));
        assertEquals(0xFFFFFFFF, ((TmxObjectLayerInfo) report.layers().get(0)).objects().get(0)
                .properties().getClassValue("physics").properties()
                .getColorRgba8888("collisionColor", 0));
        assertTrue(report.diagnostics().stream()
                .anyMatch(d -> d.location().contains("property 'physics.material'")));
    }

    @Test
    public void isometricObjectLayerIsAcceptedAndInspected() throws Exception {
        Path dir = Files.createTempDirectory("tmx-isometric-objects");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="isometric" width="1" height="1" tilewidth="16" tileheight="16">
                  <objectgroup name="Gameplay"><object id="1"><point/></object></objectgroup>
                </map>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_OBJECT_LAYER_ISOMETRIC_UNSUPPORTED"));
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        assertEquals(TmxObjectKind.POINT, layer.objects().get(0).kind());
    }

    @Test
    public void combinedTileAndObjectFixtureProducesOnlyPreflightModels() throws Exception {
        Path dir = Files.createTempDirectory("tmx-combined-object-layer");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeMap(dir, """
                <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                <objectgroup name="Gameplay">
                  <properties><property name="role" value="logic"/></properties>
                  <object id="1" name="Spawn" x="32" y="64">
                    <point/>
                    <properties><property name="player" type="bool" value="true"/></properties>
                  </object>
                  <object id="2" name="Door" x="128" y="96" width="32" height="64">
                    <properties><property name="key" value="gold"/></properties>
                  </object>
                </objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        assertEquals(2, report.layers().size());
        assertTrue(report.layers().get(0) instanceof TmxTileLayerInfo);
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(1);
        assertEquals("logic", layer.properties().getString("role", null));
        assertEquals(List.of(TmxObjectKind.POINT, TmxObjectKind.RECTANGLE),
                layer.objects().stream().map(TmxObjectInfo::kind).toList());
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_LAYER_OUT_OF_SCOPE"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_CUSTOM_PROPERTIES_IGNORED"));
    }

    @Test
    public void tileObjectsPreserveTilesetPresentationAndDrawOrderWithoutDeferredWarning() throws Exception {
        Path dir = Files.createTempDirectory("tmx-tile-object-preflight");
        writeFile(dir.resolve("tiles.png"), "fake image");
        writeFile(dir.resolve("objects.tsx"), """
                <tileset name="objects" tilewidth="16" tileheight="24" tilecount="2" columns="2"
                         objectalignment="center">
                  <tileoffset x="3" y="-2"/>
                  <image source="tiles.png" width="32" height="24"/>
                </tileset>
                """);
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="4" height="4" tilewidth="16" tileheight="24">
                  <tileset firstgid="10" source="objects.tsx"/>
                  <objectgroup name="Actors" draworder="index">
                    <object id="7" name="Gem" gid="2147483659" x="12" y="18" width="32" height="48"/>
                  </objectgroup>
                </map>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_KIND_DEFERRED"));
        TmxTilesetInfo tileset = report.tilesets().get(0);
        assertEquals(TmxObjectAlignment.CENTER, tileset.objectAlignment());
        assertEquals(3, tileset.tileOffsetX());
        assertEquals(-2, tileset.tileOffsetY());
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        assertEquals(TmxObjectDrawOrder.INDEX, layer.drawOrder());
        assertEquals(TmxObjectKind.TILE, layer.objects().get(0).kind());
        assertEquals(Long.valueOf(2147483659L), layer.objects().get(0).gid());
    }

    @Test
    public void animatedTileObjectsAreAcceptedWhileInvalidReferencesRemainBlocking() throws Exception {
        Path dir = Files.createTempDirectory("tmx-tile-object-blocking");
        writeFile(dir.resolve("tiles.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="objects" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                    <image source="tiles.png" width="32" height="16"/>
                    <tile id="0"><animation><frame tileid="1" duration="125"/></animation></tile>
                  </tileset>
                  <objectgroup name="Actors">
                    <object id="1" gid="1"/>
                    <object id="2" gid="99"/>
                    <object id="3" gid="536870914"/>
                    <object id="4" gid="268435458"/>
                  </objectgroup>
                </map>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_TILE_OBJECT_ANIMATION_UNSUPPORTED"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_TILE_OBJECT_GID_UNRESOLVED"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_TILE_OBJECT_DIAGONAL_TRANSFORM_UNSUPPORTED"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_TILE_OBJECT_HEX120_TRANSFORM_UNSUPPORTED"));
    }

    @Test
    public void otherwiseValidAnimatedTileObjectPassesPreflightWithoutAnimationDiagnostic() throws Exception {
        Path dir = Files.createTempDirectory("tmx-animated-tile-object-preflight");
        writeFile(dir.resolve("tiles.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="objects" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                    <image source="tiles.png" width="32" height="16"/>
                    <tile id="0"><animation><frame tileid="1" duration="125"/></animation></tile>
                  </tileset>
                  <objectgroup name="Actors"><object id="1" gid="1"/></objectgroup>
                </map>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertTrue(report.isImportableCandidate());
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING,
                "TMX_TILE_OBJECT_ANIMATION_UNSUPPORTED"));
    }

    @Test
    public void colorInheritedFromATileDefinitionIsSupported() throws Exception {
        Path dir = Files.createTempDirectory("tmx-inherited-property-blocking");
        writeFile(dir.resolve("tiles.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="objects" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="tiles.png" width="16" height="16"/>
                    <tile id="0"><properties>
                      <property name="color" type="color" value="#ffffffff"/>
                    </properties></tile>
                  </tileset>
                  <objectgroup name="Actors"><object gid="1"/></objectgroup>
                </map>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        assertEquals(0xFFFFFFFF, report.tilesets().get(0).tileDefinition(0)
                .properties().getColorRgba8888("color", 0));
    }

    @Test
    public void objectPropertiesKeepUnresolvedMapObjectIdsForLaterMaterialization() throws Exception {
        Path dir = Files.createTempDirectory("tmx-object-property-reference");
        FileHandle tmx = writeMap(dir, """
                <objectgroup name="Triggers"><properties>
                  <property name="layerTarget" type="object" value="2002"/>
                </properties>
                  <object id="1001" name="Switch"><properties>
                    <property name="target" type="object" value="2002"/>
                    <property name="none" type="object"/>
                    <property name="behavior" type="class" propertytype="Behavior"><properties>
                      <property name="self" type="object" value="1001"/>
                    </properties></property>
                  </properties></object>
                </objectgroup>
                <objectgroup name="Targets"><object id="2002" name="Door"/></objectgroup>
                """);

        TmxPreflightReport report = analyze(tmx);

        assertFalse(report.hasBlockingDiagnostics());
        TmxObjectLayerInfo layer = (TmxObjectLayerInfo) report.layers().get(0);
        assertEquals(1, layer.objectPropertyReferences().size());
        TmxObjectInfo source = layer.objects().get(0);
        assertEquals(3, source.objectPropertyReferences().size());
        assertFalse(source.properties().contains("target"));
        assertTrue(source.properties().getClassValue("behavior").properties().isEmpty());
        assertEquals(2002, source.objectPropertyReferences().get(0).sourceObjectId());
        assertEquals(0, source.objectPropertyReferences().get(1).sourceObjectId());
        assertEquals(java.util.List.of("behavior", "self"),
                source.objectPropertyReferences().get(2).path());
    }

    private static TmxPreflightReport analyze(FileHandle tmx) {
        return new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));
    }

    private static FileHandle writeMap(Path dir, String layers) throws Exception {
        String tileset = layers.contains("<layer")
                ? """
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                """
                : "";
        return writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                %s
                %s
                </map>
                """.formatted(tileset, layers));
    }

    private static boolean hasDiagnostic(TmxPreflightReport report,
                                         TmxDiagnosticSeverity severity,
                                         String code) {
        return diagnosticCount(report, severity, code) > 0;
    }

    private static long diagnosticCount(TmxPreflightReport report,
                                        TmxDiagnosticSeverity severity,
                                        String code) {
        return report.diagnostics().stream()
                .filter(d -> d.severity() == severity && d.code().equals(code))
                .count();
    }

    private static FileHandle writeFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }
}
