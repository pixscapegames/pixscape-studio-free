package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TmxPreflightServiceTest {

    @Test
    public void minimalOrthogonalCsvMapIsImportableCandidate() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-csv");
        FileHandle image = writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map version="1.10" tiledversion="1.10" orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2" visible="1" opacity="1" offsetx="3" offsety="4" parallaxx="1.5" parallaxy="0.5">
                    <data encoding="csv">1,0,2,4</data>
                  </layer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertFalse(report.hasBlockingDiagnostics());
        assertTrue(report.isImportableCandidate());
        assertEquals(tmx.file().toPath().toAbsolutePath().normalize().toString(), report.sourcePath());
        assertEquals("orthogonal", report.mapInfo().orientation());
        assertEquals(2, report.mapInfo().width());
        assertEquals(2, report.mapInfo().height());
        assertEquals(16, report.mapInfo().tileWidth());
        assertEquals(16, report.mapInfo().tileHeight());
        assertFalse(report.mapInfo().infinite());
        assertEquals(1, report.tileLayerCount());
        assertEquals(4, report.requiredTiledCells());
        assertEquals(3, report.nonEmptyTileCount());
        assertFalse(new FileHandle(dir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()).exists());

        TmxTilesetInfo tileset = report.tilesets().get(0);
        assertEquals(1, tileset.firstGid());
        assertEquals("terrain", tileset.name());
        assertEquals(16, tileset.tileWidth());
        assertEquals(16, tileset.tileHeight());
        assertEquals(4, tileset.tileCount());
        assertEquals(2, tileset.columns());
        assertEquals("terrain.png", tileset.imageSource());
        assertEquals(32, tileset.imageWidth());
        assertEquals(32, tileset.imageHeight());
        assertEquals(image.file().toPath().toAbsolutePath().normalize().toString(), tileset.resolvedImagePath());
        assertTrue(tileset.imageExists());

        TmxTileLayerInfo layer = (TmxTileLayerInfo) report.layers().get(0);
        assertEquals("Ground", layer.name());
        assertTrue(layer.visible());
        assertEquals(1f, layer.opacity(), 0.0001f);
        assertEquals(3f, layer.offsetX(), 0.0001f);
        assertEquals(4f, layer.offsetY(), 0.0001f);
        assertEquals(1.5f, layer.parallaxX(), 0.0001f);
        assertEquals(0.5f, layer.parallaxY(), 0.0001f);
        assertEquals(2, layer.width());
        assertEquals(2, layer.height());
        assertEquals("csv", layer.encoding());
        assertEquals(3, layer.nonEmptyTileCount());
    }

    @Test
    public void externalTsxAndImageResolveRelativeToDeclaringFiles() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-tsx");
        Path maps = Files.createDirectories(dir.resolve("maps"));
        Path tiles = Files.createDirectories(dir.resolve("tiles"));
        Path images = Files.createDirectories(dir.resolve("images"));
        FileHandle image = writeFile(images.resolve("terrain.png"), "fake image");
        FileHandle tsx = writeFile(tiles.resolve("terrain.tsx"), """
                <tileset version="1.10" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                  <image source="../images/terrain.png" width="16" height="16"/>
                </tileset>
                """);
        FileHandle tmx = writeFile(maps.resolve("map.tmx"), """
                <map orientation="isometric" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" source="../tiles/terrain.tsx"/>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertFalse(report.hasBlockingDiagnostics());
        TmxTilesetInfo tileset = report.tilesets().get(0);
        assertTrue(tileset.external());
        assertEquals(tsx.file().toPath().toAbsolutePath().normalize().toString(), tileset.sourcePath());
        assertEquals(image.file().toPath().toAbsolutePath().normalize().toString(), tileset.resolvedImagePath());
    }

    @Test
    public void base64ZlibLayerIsDecoded() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-zlib");
        writeFile(dir.resolve("terrain.png"), "fake image");
        String data = base64Zlib(1, 0, 1, 0);
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="base64" compression="zlib">%s</data></layer>
                </map>
                """.formatted(data));

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertFalse(report.hasBlockingDiagnostics());
        assertEquals(2, report.nonEmptyTileCount());
        TmxTileLayerInfo layer = (TmxTileLayerInfo) report.layers().get(0);
        assertEquals("base64", layer.encoding());
        assertEquals("zlib", layer.compression());
        assertEquals(2, layer.nonEmptyTileCount());
    }

    @Test
    public void unsupportedOrientationReturnsBlockingDiagnostic() throws Exception {
        FileHandle tmx = validMap(Files.createTempDirectory("tmx-preflight-hex"), "hexagonal", "1", "1");

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_UNSUPPORTED_ORIENTATION"));
    }

    @Test
    public void infiniteMapReturnsBlockingDiagnostic() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-infinite");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" infinite="1" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_INFINITE_MAP"));
        assertEquals(0, report.tileLayerCount());
    }

    @Test
    public void missingTilesetImageReturnsBlockingDiagnostic() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-missing-image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="missing.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_TILESET_IMAGE_MISSING"));
    }

    @Test
    public void invalidGidReturnsBlockingDiagnostic() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-invalid-gid");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">2</data></layer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_GID_UNRESOLVED"));
    }

    @Test
    public void objectLayersWarnAndImageLayersAnalyze() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-other-layers");
        writeFile(dir.resolve("terrain.png"), "fake image");
        writeFile(dir.resolve("background.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <objectgroup name="Objects"/>
                  <imagelayer name="Backdrop" visible="0" opacity="0.5" offsetx="3" offsety="4" parallaxx="2" parallaxy="0.25" x="10" y="20">
                    <image source="background.png" width="64" height="32"/>
                  </imagelayer>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertFalse(report.hasBlockingDiagnostics());
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_LAYER_OUT_OF_SCOPE"));
        assertFalse(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_IMAGE_LAYER_OUT_OF_SCOPE"));
        assertEquals(1, report.tileLayerCount());
        assertEquals(3, report.layers().size());
        TmxImageLayerInfo image = (TmxImageLayerInfo) report.layers().get(1);
        assertEquals("Backdrop", image.name());
        assertFalse(image.visible());
        assertEquals(0.5f, image.opacity(), 0.0001f);
        assertEquals(3f, image.offsetX(), 0.0001f);
        assertEquals(4f, image.offsetY(), 0.0001f);
        assertEquals(2f, image.parallaxX(), 0.0001f);
        assertEquals(0.25f, image.parallaxY(), 0.0001f);
        assertEquals(10f, image.x(), 0.0001f);
        assertEquals(20f, image.y(), 0.0001f);
        assertEquals("background.png", image.imageSource());
        assertEquals(64, image.imageWidth());
        assertEquals(32, image.imageHeight());
        assertTrue(image.imageExists());
    }

    @Test
    public void imageLayerMissingImageSourceReturnsBlockingDiagnostic() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-image-missing-source");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <imagelayer name="Backdrop"><image/></imagelayer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.BLOCKING, "TMX_IMAGE_LAYER_SOURCE_MISSING"));
    }

    @Test
    public void imageLayerUnsupportedAttributesWarn() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-image-warnings");
        writeFile(dir.resolve("terrain.png"), "fake image");
        writeFile(dir.resolve("background.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16" parallaxoriginx="8">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <imagelayer name="Backdrop" repeatx="1" tintcolor="#ff00ff">
                    <properties><property name="ignored" value="1"/></properties>
                    <image source="background.png" trans="ff00ff"/>
                  </imagelayer>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertFalse(report.hasBlockingDiagnostics());
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_MAP_PARALLAX_ORIGIN_IGNORED"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_IMAGE_LAYER_REPEAT_UNSUPPORTED"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_IMAGE_LAYER_TINT_IGNORED"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_IMAGE_LAYER_TRANSPARENT_COLOR_IGNORED"));
        assertTrue(hasDiagnostic(report, TmxDiagnosticSeverity.WARNING, "TMX_CUSTOM_PROPERTIES_IGNORED"));
    }

    @Test
    public void nestedGroupsAreFlattenedWithCumulativeVisibilityAndParallax() throws Exception {
        Path dir = Files.createTempDirectory("tmx-preflight-groups");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <group name="World" visible="0" parallaxx="2">
                    <group name="Sub" parallaxy="0.5">
                      <layer name="Ground" width="1" height="1" parallaxx="3"><data encoding="csv">1</data></layer>
                    </group>
                    <layer name="Above" width="1" height="1"><data encoding="csv">0</data></layer>
                  </group>
                </map>
                """);

        TmxPreflightReport report = new TmxPreflightService().analyze(new TmxPreflightRequest(tmx));

        assertFalse(report.hasBlockingDiagnostics());
        assertEquals(List.of("World/Sub/Ground", "World/Above"), report.layers().stream().map(TmxLayerInfo::name).toList());

        TmxTileLayerInfo ground = (TmxTileLayerInfo) report.layers().get(0);
        assertFalse(ground.visible());
        assertEquals(6f, ground.parallaxX(), 0.0001f);
        assertEquals(0.5f, ground.parallaxY(), 0.0001f);

        TmxTileLayerInfo above = (TmxTileLayerInfo) report.layers().get(1);
        assertFalse(above.visible());
        assertEquals(2f, above.parallaxX(), 0.0001f);
        assertEquals(1f, above.parallaxY(), 0.0001f);
    }

    private static FileHandle validMap(Path dir, String orientation, String width, String height) throws Exception {
        writeFile(dir.resolve("terrain.png"), "fake image");
        return writeFile(dir.resolve("map.tmx"), """
                <map orientation="%s" width="%s" height="%s" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """.formatted(orientation, width, height));
    }

    private static boolean hasDiagnostic(TmxPreflightReport report,
                                         TmxDiagnosticSeverity severity,
                                         String code) {
        return report.diagnostics().stream()
                .anyMatch(d -> d.severity() == severity && d.code().equals(code));
    }

    private static FileHandle writeFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }

    private static String base64Zlib(int... gids) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(gids.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int gid : gids) {
            buffer.putInt(gid);
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(buffer.array());
        }
        return Base64.getEncoder().encodeToString(compressed.toByteArray());
    }

//    @Test
//    public void manualPreflight_realTmx() {
//        FileHandle tmx = new FileHandle("C:\\Users\\lauro\\Desktop\\test.tmx");
//
//        TmxPreflightService service = new TmxPreflightService();
//        TmxPreflightReport report = service.analyze(new TmxPreflightRequest(tmx));
//
//        System.out.println("Source: " + report.sourcePath());
//        System.out.println("Importable: " + report.isImportableCandidate());
//        System.out.println("Required cells: " + report.requiredTiledCells());
//        System.out.println("Non-empty tiles: " + report.nonEmptyTileCount());
//
//        for (TmxDiagnostic diagnostic : report.diagnostics()) {
//            System.out.println(diagnostic.severity() + " - " + diagnostic.message());
//        }
//    }
}
