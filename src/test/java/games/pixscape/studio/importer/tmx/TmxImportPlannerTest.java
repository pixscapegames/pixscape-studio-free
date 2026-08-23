package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class TmxImportPlannerTest {

    @Test
    public void minimalOrthogonalCsvMapCreatesPlan() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-csv");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("simple_map.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">1,0,2,4</data></layer>
                </map>
                """);

        TmxImportPlanResult result = new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx));

        assertEquals(TmxImportPlanStatus.PLAN_CREATED, result.status());
        assertTrue(result.hasPlan());
        assertFalse(result.preflightReport().hasBlockingDiagnostics());

        TmxImportPlan plan = result.plan();
        assertEquals("Simple_map", plan.scene().proposedSceneName());
        assertEquals(SceneMetaRuntime.TiledProjection.ORTHO, plan.scene().tiledProjection());
        assertEquals(2, plan.scene().mapWidthCells());
        assertEquals(2, plan.scene().mapHeightCells());
        assertEquals(16, plan.scene().tileWidth());
        assertEquals(16, plan.scene().tileHeight());
        assertEquals(4, plan.scene().requiredTiledCells());
        assertEquals(1, plan.scene().tileLayerCount());
        assertEquals(3, plan.scene().nonEmptyTileCount());

        assertEquals(1, plan.tilesets().size());
        TmxTilesetPlan tileset = plan.tilesets().get(0);
        assertEquals(0, tileset.planIndex());
        assertEquals(1, tileset.firstGid());
        assertEquals("terrain", tileset.name());
        assertEquals(4, tileset.tileCount());
        assertEquals(2, tileset.columns());
        assertEquals(0, tileset.localTileIdStart());
        assertEquals(4, tileset.localTileIdEndExclusive());

        assertEquals(1, plan.layers().size());
        TmxTileLayerPlan layer = (TmxTileLayerPlan) plan.layers().get(0);
        assertEquals("Ground", layer.name());
        assertEquals("Ground", layer.originalName());
        assertEquals(0, layer.sourceLayerIndex());
        assertEquals(4, layer.requiredCells());
        assertEquals(3, layer.nonEmptyCellCount());
        assertEquals(3, layer.cells().size());
        assertEquals(0, layer.cells().get(0).sourceX());
        assertEquals(0, layer.cells().get(0).sourceY());
        assertEquals(0, layer.cells().get(0).localTileId());
        assertFalse(new FileHandle(dir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()).exists());
    }

    @Test
    public void externalTsxTilesetCreatesResolvedTilesetPlan() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-tsx");
        Path maps = Files.createDirectories(dir.resolve("maps"));
        Path tiles = Files.createDirectories(dir.resolve("tiles"));
        Path images = Files.createDirectories(dir.resolve("images"));
        FileHandle image = writeFile(images.resolve("terrain.png"), "fake image");
        FileHandle tsx = writeFile(tiles.resolve("terrain.tsx"), """
                <tileset name="terrain" tilewidth="16" tileheight="16" tilecount="8" columns="4" spacing="1" margin="2">
                  <image source="../images/terrain.png" width="69" height="35"/>
                </tileset>
                """);
        FileHandle tmx = writeFile(maps.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="17" source="../tiles/terrain.tsx"/>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">17</data></layer>
                </map>
                """);

        TmxImportPlanResult result = new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx));

        assertTrue(result.hasPlan());
        TmxTilesetPlan tileset = result.plan().tilesets().get(0);
        assertTrue(tileset.external());
        assertEquals(17, tileset.firstGid());
        assertEquals(4, tileset.columns());
        assertEquals(8, tileset.tileCount());
        assertEquals(16, tileset.tileWidth());
        assertEquals(16, tileset.tileHeight());
        assertEquals(69, tileset.imageWidth());
        assertEquals(35, tileset.imageHeight());
        assertEquals(tsx.file().toPath().toAbsolutePath().normalize().toString(), tileset.sourceTsxPath());
        assertEquals(image.file().toPath().toAbsolutePath().normalize().toString(), tileset.resolvedImagePath());
    }

    @Test
    public void multipleTilesetsResolveCellPlansToTilesetAndLocalTileId() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-multiple-tilesets");
        writeFile(dir.resolve("terrain.png"), "fake image");
        writeFile(dir.resolve("props.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="3" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="2" columns="2">
                    <image source="terrain.png" width="32" height="16"/>
                  </tileset>
                  <tileset firstgid="10" name="props" tilewidth="16" tileheight="16" tilecount="3" columns="3">
                    <image source="props.png" width="48" height="16"/>
                  </tileset>
                  <layer name="Ground" width="3" height="1"><data encoding="csv">1,10,12</data></layer>
                </map>
                """);

        TmxTileLayerPlan layer = firstLayer(new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx)));

        assertEquals(3, layer.cells().size());
        assertCell(layer.cells().get(0), 0, 1, 1, 0);
        assertCell(layer.cells().get(1), 1, 10, 10, 0);
        assertCell(layer.cells().get(2), 1, 12, 10, 2);
    }

    @Test
    public void transformFlagsArePreservedInCellPlans() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-flips");
        writeFile(dir.resolve("terrain.png"), "fake image");
        long h = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        long v = TmxGidSupport.FLIPPED_VERTICALLY_FLAG | 1L;
        long d = TmxGidSupport.FLIPPED_DIAGONALLY_FLAG | 1L;
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="3" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="3" height="1"><data encoding="csv">%d,%d,%d</data></layer>
                </map>
                """.formatted(h, v, d));

        TmxTileLayerPlan layer = firstLayer(new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx)));

        assertEquals(3, layer.cells().size());
        assertEquals(1, layer.cells().get(0).cleanGid());
        assertTrue(layer.cells().get(0).transform().hasTransformFlags());
        assertTrue(layer.cells().get(0).transform().horizontalFlip());
        assertTrue(layer.cells().get(1).transform().verticalFlip());
        assertTrue(layer.cells().get(2).transform().diagonalFlip());
    }

    @Test
    public void flaggedGidsResolveTilesetsAfterClearingHighBits() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-flagged-gid-resolution");
        writeFile(dir.resolve("a.png"), "fake image");
        writeFile(dir.resolve("b.png"), "fake image");
        writeFile(dir.resolve("c.png"), "fake image");
        long h72 = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 0x48L;
        long all72 = TmxGidSupport.GID_FLAGS_MASK | 0x48L;
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="3" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="a" tilewidth="16" tileheight="16" tilecount="64" columns="8">
                    <image source="a.png" width="128" height="128"/>
                  </tileset>
                  <tileset firstgid="65" name="b" tilewidth="16" tileheight="16" tilecount="50" columns="10">
                    <image source="b.png" width="160" height="80"/>
                  </tileset>
                  <tileset firstgid="115" name="c" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="c.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="3" height="1"><data encoding="csv">72,%d,%d</data></layer>
                </map>
                """.formatted(h72, all72));

        TmxTileLayerPlan layer = firstLayer(new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx)));

        assertEquals(3, layer.cells().size());
        assertCell(layer.cells().get(0), 1, 72, 65, 7);
        assertFalse(layer.cells().get(0).transform().hasTransformFlags());
        assertCell(layer.cells().get(1), 1, 72, 65, 7);
        assertTrue(layer.cells().get(1).transform().horizontalFlip());
        assertCell(layer.cells().get(2), 1, 72, 65, 7);
        assertTrue(layer.cells().get(2).transform().horizontalFlip());
        assertTrue(layer.cells().get(2).transform().verticalFlip());
        assertTrue(layer.cells().get(2).transform().diagonalFlip());
        assertTrue(layer.cells().get(2).transform().hexagonal120Flag());
    }

    @Test
    public void nestedGroupLayerOrderAndEffectiveValuesArePlanned() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-groups");
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

        TmxImportPlan plan = new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx)).plan();

        assertEquals(List.of("World/Sub/Ground", "World/Above"), plan.layers().stream().map(TmxLayerPlan::name).toList());
        TmxTileLayerPlan ground = (TmxTileLayerPlan) plan.layers().get(0);
        assertFalse(ground.visible());
        assertEquals(6f, ground.parallaxX(), 0.0001f);
        assertEquals(0.5f, ground.parallaxY(), 0.0001f);
        assertEquals("Ground", ground.originalName());
    }

    @Test
    public void imageAndObjectLayersArePlannedInSourceOrder() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-warnings");
        writeFile(dir.resolve("terrain.png"), "fake image");
        writeFile(dir.resolve("background.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <objectgroup name="Objects"/>
                  <imagelayer name="Backdrop" offsetx="3" offsety="4" x="10" y="20" opacity="0.5" parallaxx="2" parallaxy="0.25" repeatx="true">
                    <image source="background.png" width="64" height="32"/>
                  </imagelayer>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxImportPlanResult result = new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx));

        assertEquals(TmxImportPlanStatus.PLAN_CREATED, result.status());
        assertTrue(result.hasPlan());
        assertEquals(3, result.plan().layers().size());
        assertEquals(List.of("Objects", "Backdrop", "Ground"),
                result.plan().layers().stream().map(TmxLayerPlan::name).toList());
        TmxObjectLayerPlan objects = (TmxObjectLayerPlan) result.plan().layers().get(0);
        assertEquals(0, objects.sourceLayerIndex());
        assertTrue(objects.objects().isEmpty());
        TmxImageLayerPlan image = (TmxImageLayerPlan) result.plan().layers().get(1);
        assertEquals(1, image.sourceLayerIndex());
        assertEquals("Backdrop", image.originalName());
        assertEquals(3f, image.offsetX(), 0.0001f);
        assertEquals(4f, image.offsetY(), 0.0001f);
        assertEquals(10f, image.x(), 0.0001f);
        assertEquals(20f, image.y(), 0.0001f);
        assertEquals(0.5f, image.opacity(), 0.0001f);
        assertEquals(2f, image.parallaxX(), 0.0001f);
        assertEquals(0.25f, image.parallaxY(), 0.0001f);
        assertTrue(image.repeatX());
        assertFalse(image.repeatY());
        assertEquals("background.png", image.imageSource());
        assertEquals(64, image.imageWidth());
        assertEquals(32, image.imageHeight());
        assertFalse(hasDiagnostic(result.preflightReport(), TmxDiagnosticSeverity.WARNING, "TMX_OBJECT_LAYER_OUT_OF_SCOPE"));
        assertFalse(hasDiagnostic(result.preflightReport(), TmxDiagnosticSeverity.WARNING, "TMX_IMAGE_LAYER_OUT_OF_SCOPE"));
    }

    @Test
    public void blockingDiagnosticsPreventPlanning() throws Exception {
        Path dir = Files.createTempDirectory("tmx-plan-blocking");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="missing.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxImportPlanResult result = new TmxImportPlanner().plan(new TmxImportPlanRequest(tmx));

        assertEquals(TmxImportPlanStatus.PREFLIGHT_FAILED, result.status());
        assertFalse(result.hasPlan());
        assertNull(result.plan());
        assertTrue(result.preflightReport().hasBlockingDiagnostics());
        assertFalse(result.blockingDiagnostics().isEmpty());
    }

    private static TmxTileLayerPlan firstLayer(TmxImportPlanResult result) {
        assertNotNull(result.plan());
        return (TmxTileLayerPlan) result.plan().layers().get(0);
    }

    private static void assertCell(TmxTileCellPlan cell,
                                   int tilesetPlanIndex,
                                   int cleanGid,
                                   int tilesetFirstGid,
                                   int localTileId) {
        assertEquals(tilesetPlanIndex, cell.tilesetPlanIndex());
        assertEquals(cleanGid, cell.cleanGid());
        assertEquals(tilesetFirstGid, cell.tilesetFirstGid());
        assertEquals(localTileId, cell.localTileId());
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
}
