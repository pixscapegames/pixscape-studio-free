package games.pixscape.studio.ops;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class EditorOpsImplIntegrationContractTest {

    @Test
    public void deleteEntities_usesHistoryDeleteCommand_andGuardsEmptySelection() throws Exception {
        String source = readEditorOpsImpl();
        String body = methodBody(source, "public void deleteEntities(IntArray entities)");

        assertTrue(body.contains("if (entities == null || entities.isEmpty())"));
        assertTrue(body.contains("IntConsumer onRestoredEntity = restoredEntityId ->"));
        assertTrue(body.contains("rebindHistoryEntityRenderAssets(restoredEntityId);"));
        assertTrue(body.contains("DeleteEntitiesCommand cmd = new DeleteEntitiesCommand(world, historyIds, entities, onRestoredEntity);"));
        assertTrue(body.contains("execute(cmd);"));
    }

    @Test
    public void deleteJoint_andDeleteFixture_applyInvalidGuards_beforeHistoryMutation() throws Exception {
        String source = readEditorOpsImpl();
        String deleteJoint = methodBody(source, "public void deleteJoint(int jointEntityId)");
        String deleteFixture = methodBody(source, "public void deleteFixture(int bodyEid, int physicsShapeId)");

        assertTrue(deleteJoint.contains("if (jointEntityId < 0 || !physicsService.isJoint(jointEntityId))"));
        assertTrue(deleteJoint.contains("historyManager.execute(new DeleteJointCommand(world, historyIds, jointEntityId));"));

        assertTrue(deleteFixture.contains("if (bodyEid < 0 || physicsShapeId <= 0) return;"));
        assertTrue(deleteFixture.contains("if (fixtures == null)"));
        assertTrue(deleteFixture.contains("new DeleteFixtureCommand("));
        assertTrue(deleteFixture.contains("physicsSelectionService"));
    }

    @Test
    public void addFixtureFlows_centerFixturesOnBody_andPushThroughHistoryCommands() throws Exception {
        String source = readEditorOpsImpl();
        String addBox = methodBody(source, "public void addBoxFixture(int bodyEid, float worldX, float worldY)");
        String addCircle = methodBody(source, "public void addCircleFixture(int bodyEid, float worldX, float worldY)");

        assertTrue(addBox.contains("fixture.geometry.offsetX = 0f;"));
        assertTrue(addBox.contains("fixture.geometry.offsetY = 0f;"));
        assertTrue(addBox.contains("historyManager.execute(new AddFixtureCommand("));

        assertTrue(addCircle.contains("fixture.geometry.offsetX = 0f;"));
        assertTrue(addCircle.contains("fixture.geometry.offsetY = 0f;"));
        assertTrue(addCircle.contains("historyManager.execute(new AddFixtureCommand("));
    }

    @Test
    public void directSpatialWallCreationUsesTheStrictRectanglePipeline() throws Exception {
        String source = readEditorOpsImpl();
        String body = methodBody(source, "public void addSpatialBlock(int layerEntityId, SpatialBlockPlacementTarget target)");

        assertTrue(body.contains("if (!tiled.data.isInside(targetGx, targetGy)) return;"));
        assertTrue(body.contains("int tileAssetId = tiled.data.getTile(targetGx, targetGy);"));
        assertTrue(body.contains("if (tileAssetId <= 0) return;"));
        assertTrue(body.contains("SpatialTileSelectionService.fromOccupiedRect("));
        assertTrue(body.contains("historyManager.execute(command);"));
    }

    @Test
    public void tileSelectionSpatialWallUsesTheSameCommandPlanner() throws Exception {
        String source = readEditorOpsImpl();
        String body = methodBody(source, "public void createSpatialBlockFromSelectedTiles()");

        assertTrue(body.contains("SpatialWallCreationService.executeSelectedRectangle("));
        String service = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/spatial/SpatialWallCreationService.java"),
                StandardCharsets.UTF_8
        );
        assertTrue(service.contains("new AddSpatialBlockCommand("));
        assertTrue(service.contains("tileSelection.clear();"));
    }

    private static String readEditorOpsImpl() throws Exception {
        return Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ops/EditorOpsImpl.java"),
                StandardCharsets.UTF_8
        );
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
