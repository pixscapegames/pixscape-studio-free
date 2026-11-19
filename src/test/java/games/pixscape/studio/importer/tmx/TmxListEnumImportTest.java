package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.service.asset.TsxTilesetImportParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class TmxListEnumImportTest {
    private static final String ROOT = "src/test/resources/tmx/list-enums/";

    @Test
    public void mixedListsEnumsPrecisionAndEmptyOverridesUseTheSameReaderForAllOwners() {
        for (String projection : new String[]{"orthogonal", "isometric"}) {
            var result = new TmxImportPlanner().plan(new TmxImportPlanRequest(
                    new FileHandle(ROOT + projection + ".tmx"), new FileHandle(ROOT + "types.tiled-project")));
            assertTrue(result.preflightReport().diagnostics().toString(), result.hasPlan());
            assertValues(result.plan().scene().properties(), 20, 10);
            for (TmxLayerPlan layer : result.plan().layers()) {
                assertValues(layer.properties(), 20, 10);
            }
            var objects = ((TmxObjectLayerPlan) result.plan().layers().get(2)).objects();
            assertValues(objects.stream().filter(o -> o.name().equals("Actor")).findFirst().orElseThrow().properties(), 20, 10);
            assertValues(objects.stream().filter(o -> o.name().equals("Inherited")).findFirst().orElseThrow().properties(), -1, -1);
            var empty = objects.stream().filter(o -> o.name().equals("EmptyOverride")).findFirst().orElseThrow().properties();
            assertEquals(PropertyType.LIST, empty.typeOf("items"));
            assertEquals(0, empty.getListValue("items").size());
            assertEquals(16777217d, empty.getDouble("precise", 0), 0);
            assertValues(result.plan().tilesets().get(0).tileDefinitions().get(0).properties(), -1, -1);
        }
        var tsx = new TsxTilesetImportParser().parse(new FileHandle(ROOT + "tiles.tsx"), new FileHandle(ROOT + "types.tiled-project"));
        assertValues(tsx.tileProperties().get(0), -1, -1);
    }

    @Test
    public void missingProjectUnknownEnumsAndNestedUnsupportedValuesAreExplicit() throws Exception {
        var missing = new TmxPreflightService().analyze(new TmxPreflightRequest(new FileHandle(ROOT + "orthogonal.tmx")));
        assertTrue(missing.hasBlockingDiagnostics());
        assertTrue(missing.diagnostics().stream().anyMatch(d -> d.code().equals("TMX_PROJECT_REQUIRED")
                && d.message().contains("Direction") && d.location().contains("direction")));
        for (String item : new String[]{"<item type='file' value='missing.png'/>",
                "<item type='object' value='999'/>", "<item propertytype='Missing' value='X'/>",
                "<item propertytype='Direction' value='Unknown'/>", "<item type='int' propertytype='Flags' value='8'/>"}) {
            var file = new FileHandle(java.nio.file.Files.createTempFile("list-invalid", ".tmx").toFile());
            file.writeString("<map orientation='orthogonal' width='1' height='1' tilewidth='16' tileheight='16'>"
                    + "<properties><property name='items' type='list'>" + item + "</property></properties></map>", false);
            var result = new TmxPreflightService().analyze(new TmxPreflightRequest(file, new FileHandle(ROOT + "types.tiled-project")));
            assertTrue(result.hasBlockingDiagnostics());
            assertTrue(result.diagnostics().toString(), result.diagnostics().stream().anyMatch(d ->
                    d.severity() == TmxDiagnosticSeverity.BLOCKING && d.location().contains("items") && d.location().contains("[0]")));
        }
    }

    @Test
    public void encounteredExternalClassDefaultsAndAmbiguousDefinitionsAreRejected() throws Exception {
        var dir = new FileHandle(java.nio.file.Files.createTempDirectory("list-class-default").toFile());
        var project = dir.child("types.tiled-project");
        project.writeString("{propertyTypes:[{id:1,name:Config,type:class,members:[{name:count,type:int,value:9}]}]}", false);
        var map = dir.child("map.tmx");
        map.writeString("<map orientation='orthogonal' width='1' height='1' tilewidth='16' tileheight='16'>"
                + "<properties><property name='items' type='list'><item type='class' propertytype='Config'/></property></properties></map>", false);
        var result = new TmxPreflightService().analyze(new TmxPreflightRequest(map, project));
        assertTrue(result.hasBlockingDiagnostics());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.message().contains("external default member 'count'")
                && d.location().contains("[0]")));
        map.writeString("<map class='Config' orientation='orthogonal' width='1' height='1' tilewidth='16' tileheight='16'/>", false);
        result = new TmxPreflightService().analyze(new TmxPreflightRequest(map, project));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("TMX_CLASS_DEFAULT_UNSUPPORTED")
                && d.location().equals("map")));
        project.writeString("{propertyTypes:[{id:1,name:Mode,type:enum,storageType:string,values:[A]},"
                + "{id:2,name:Mode,type:enum,storageType:string,values:[B]}]}", false);
        assertThrows(IllegalArgumentException.class, () -> TiledPropertyTypes.load(project));
    }

    static void assertValues(PropertySet properties, int target, int self) {
        assertEquals(16777217d, properties.getDouble("precise", 0), 0);
        assertEquals(0.1234567890123456, properties.getDouble("decimal", 0), 0);
        assertEquals(0, properties.getListValue("empty").size());
        assertEquals("West", properties.getEnumValue("direction").stringValue());
        assertEquals(5, properties.getEnumValue("flags").intValue());
        assertEquals("C,A", properties.getEnumValue("stringFlags").stringValue());
        assertEquals(1, properties.getEnumValue("mode").intValue());
        var items = properties.getListValue("items");
        assertEquals(8, items.size());
        assertEquals("7", items.getString(0));
        assertEquals(7, items.getInt(1));
        assertEquals(7, items.getInt(2));
        assertEquals(16777217d, items.getDouble(3), 0);
        assertEquals(target, items.getObjectStableId(4));
        assertEquals(-1, items.getObjectStableId(5));
        assertEquals(0.1234567890123456, items.getListValue(6).getDouble(0), 0);
        assertEquals("North", items.getListValue(6).getEnumValue(1).stringValue());
        assertEquals(self, items.getClassValue(7).properties().getListValue("targets").getObjectStableId(0));
        assertEquals(3, items.getClassValue(7).properties().getEnumValue("flags").intValue());
    }
}
