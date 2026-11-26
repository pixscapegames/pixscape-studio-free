package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.service.asset.TsxTilesetImportParser;
import games.pixscape.studio.ui.importer.TmxImportUiSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.*;

public class TmxPropertyImportTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void filePathsAndUnknownTypesUseTheSameReaderAcrossOwnersAndExternalTilesets() throws Exception {
        for (boolean external : new boolean[]{false, true}) {
            for (boolean collection : new boolean[]{false, true}) {
                FileHandle root = new FileHandle(temporary.newFolder());
                FileHandle map = fixture(root, external, collection);
                var preparation = TmxImportUiSupport.prepare(map);
                assertFalse(preparation.diagnostics().toString(), preparation.hasBlockingDiagnostics());
                var plan = preparation.plan();
                assertNotNull(plan);
                assertProperties(plan.scene().properties());
                assertProperties(plan.tilesets().get(0).properties());
                for (var layer : plan.layers()) {
                    assertProperties(layer.properties());
                }
                var objects = ((TmxObjectLayerPlan) plan.layers().get(2)).objects();
                for (var object : objects) {
                    assertProperties(object.properties());
                }
                assertProperties(plan.tilesets().get(0).tileDefinitions().get(0).properties());
                var warnings = preparation.diagnostics().stream()
                        .filter(d -> d.code().equals("TMX_PROPERTY_TYPE_UNSUPPORTED")).toList();
                assertEquals(warnings.toString(), 7, warnings.size());
                assertEquals(1, warnings.stream().filter(d -> d.location().contains("tile 0")).count());
                for (var warning : warnings) {
                    assertEquals(TmxDiagnosticSeverity.WARNING, warning.severity());
                    assertTrue(warning.message().contains("unknown"));
                    assertTrue(warning.message().contains("future-type"));
                }
                String shown = TmxImportUiSupport.formatDiagnostics(preparation.diagnostics());
                for (String owner : List.of("map", "Ground", "Backdrop", "Actors", "Hero", "terrain", "tile 0")) {
                    assertTrue(shown, shown.contains(owner));
                }
                if (external) {
                    var tsx = new TsxTilesetImportParser().parse(root.child("terrain.tsx"));
                    assertProperties(tsx.properties());
                    assertProperties(tsx.tileProperties().get(0));
                    assertTrue(tsx.diagnostics().stream().anyMatch(d -> d.location().contains("terrain")
                            && d.location().contains("tile 0") && d.message().contains("future-type")));
                }
            }
        }
    }

    @Test
    public void skipsOnlyUnsupportedListItemsAndRemapsRemainingObjectPaths() throws Exception {
        FileHandle map = new FileHandle(temporary.newFile("nested.tmx"));
        map.writeString("""
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <properties>
                    <property name="mixed" type="list"><item type="future"/><item type="object" value="1"/></property>
                    <property name="paths" type="list"><item type="file" value="../absent path"/><item type="file" value=""/></property>
                    <property name="settings" type="class" propertytype="Settings"><properties>
                      <property name="unknown" type="future"/><property name="kept" type="int" value="8"/>
                    </properties></property>
                  </properties>
                  <objectgroup name="Targets"><object id="1" name="Target" x="0" y="0" width="4" height="4"/></objectgroup>
                </map>
                """, false);
        var result = TmxImportUiSupport.prepare(map);
        assertFalse(result.diagnostics().toString(), result.hasBlockingDiagnostics());
        PropertySet values = result.plan().scene().properties();
        assertEquals(1, values.getListValue("mixed").size());
        assertEquals(1, values.getListValue("mixed").getObjectStableId(0));
        assertEquals("../absent path", values.getListValue("paths").getString(0));
        assertEquals("", values.getListValue("paths").getString(1));
        assertEquals(8, values.getClassValue("settings").properties().getInt("kept", 0));
        assertFalse(values.getClassValue("settings").properties().contains("unknown"));
        assertEquals(2, result.warningCount());
    }

    @Test
    public void skippedExplicitClassMembersDoNotBecomeMissingDefaultsOnTileInstances() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        FileHandle map = fixture(root, true, false);
        FileHandle tsx = root.child("terrain.tsx");
        tsx.writeString(tsx.readString().replace("<tile id='0'>", "<tile id='0' class='Config'>"), false);
        FileHandle types = root.child("types.tiled-project");
        types.writeString("{propertyTypes:[{id:1,name:Config,type:class,members:[{name:unknown,type:string,value:default}]}]}", false);
        var result = TmxImportUiSupport.prepare(map, types);
        assertFalse(result.diagnostics().toString(), result.hasBlockingDiagnostics());
        assertEquals(7, result.warningCount());
    }

    @Test
    public void externalTsxFilePropertiesAreNotRebasedToTheMapDirectory() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        FileHandle map = fixture(root, true, false);
        FileHandle nested = root.child("sets/terrain.tsx");
        nested.writeString(root.child("terrain.tsx").readString()
                .replace("source='terrain.png'", "source='../terrain.png'"), false);
        map.writeString(map.readString().replace("source='terrain.tsx'", "source='sets/terrain.tsx'"), false);
        var result = TmxImportUiSupport.prepare(map);
        assertFalse(result.diagnostics().toString(), result.hasBlockingDiagnostics());
        assertProperties(result.plan().tilesets().get(0).properties());
        assertProperties(result.plan().tilesets().get(0).tileDefinitions().get(0).properties());
        assertProperties(new TsxTilesetImportParser().parse(nested).properties());
    }

    static FileHandle fixture(FileHandle root, boolean external, boolean collection) throws Exception {
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), "png", root.child("terrain.png").file());
        String properties = properties();
        String tileset = "<tileset name='terrain' tilewidth='32' tileheight='32' tilecount='1' columns='"
                + (collection ? 0 : 1) + "'>"
                + properties
                + (collection ? "" : "<image source='terrain.png' width='32' height='32'/>")
                + "<tile id='0'>" + properties
                + (collection ? "<image source='terrain.png' width='32' height='32'/>" : "")
                + "</tile></tileset>";
        if (external) {
            root.child("terrain.tsx").writeString(tileset, false);
            tileset = "<tileset firstgid='1' source='terrain.tsx'/>";
        } else {
            tileset = tileset.replace("<tileset ", "<tileset firstgid='1' ");
        }
        FileHandle map = root.child("map.tmx");
        map.writeString("<map orientation='orthogonal' width='2' height='1' tilewidth='32' tileheight='32'>"
                + properties + tileset
                + "<layer name='Ground' width='2' height='1'>" + properties + "<data encoding='csv'>1,1</data></layer>"
                + "<imagelayer name='Backdrop'>" + properties + "<image source='terrain.png' width='32' height='32'/></imagelayer>"
                + "<objectgroup name='Actors'>" + properties
                + "<object id='1' name='Hero' gid='1' x='0' y='32'>" + properties + "</object>"
                + "<object id='2' name='Inherited' gid='1' x='32' y='32'/></objectgroup></map>", false);
        return map;
    }

    private static String properties() {
        return """
                <properties>
                  <property name="relative" type="file" value="../scripts/missing.lua"/>
                  <property name="spaces" type="file" value="folder with spaces/missing file.txt"/>
                  <property name="empty" type="file" value=""/>
                  <property name="noValue" type="file"/>
                  <property name="text" type="file">../text path.txt</property>
                  <property name="customFile" type="file" propertytype="FileMetadata" value="missing.dat"/>
                  <property name="kept" type="int" value="42"/>
                  <property name="unknown" type="future-type" propertytype="UnknownMetadata" value="ignored"/>
                </properties>
                """;
    }

    static void assertProperties(PropertySet values) {
        for (String key : List.of("relative", "spaces", "empty", "noValue", "text", "customFile")) {
            assertEquals(key, PropertyType.STRING, values.typeOf(key));
        }
        assertEquals("../scripts/missing.lua", values.getString("relative", null));
        assertEquals("folder with spaces/missing file.txt", values.getString("spaces", null));
        assertEquals("", values.getString("empty", null));
        assertEquals("", values.getString("noValue", null));
        assertEquals("../text path.txt", values.getString("text", null));
        assertEquals("missing.dat", values.getString("customFile", null));
        assertEquals(42, values.getInt("kept", 0));
        assertFalse(values.contains("unknown"));
    }
}
