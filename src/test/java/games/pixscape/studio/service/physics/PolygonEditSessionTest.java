package games.pixscape.studio.service.physics;

import com.artemis.World;
import games.pixscape.studio.service.geometry.PolygonEditSession;
import games.pixscape.studio.service.tiled.TiledObjectGeometry;
import org.junit.Test;
import static org.junit.Assert.*;

public class PolygonEditSessionTest {
    @Test public void sharedDrawingSupportsBackspaceCloseAndEscapeForBothTargets() {
        for (PolygonEditSession session : new PolygonEditSession[]{new PolygonEditSession(), new PolygonDrawSession()}) {
            session.beginDraw();
            session.drawInput(0, 0, true, false, false, 2);
            session.drawInput(100, 0, true, false, false, 2);
            session.drawInput(100, 100, true, false, false, 2);
            session.drawInput(0, 0, false, true, false, 2);
            assertEquals(2, session.pointCount());
            session.drawInput(50, 40, true, false, false, 2);
            assertEquals(PolygonEditSession.DrawAction.CLOSED, session.drawInput(1, 1, true, false, false, 2));
            assertArrayEquals(new float[]{0,0,100,0,50,40}, session.toFloatArrayWorld(), 0);
            session.drawInput(0, 0, false, false, true, 2);
            assertFalse(session.isActive()); assertEquals(0, session.pointCount());
        }
    }

    @Test public void candidateDragIsIndependentAndDomainValidationStaysSeparate() {
        World world = new World();
        try {
            PhysicsPolygonAuthoringService physics = new PhysicsPolygonAuthoringService(world);
            float[] source = {0,0,4,0,4,4,0,4};
            PolygonDrawSession session = new PolygonDrawSession();
            session.beginEdit(17, 9, source, 4);
            session.beginDrag(source, 1);
            session.dragTo(-2, 3);
            assertArrayEquals(new float[]{0,0,4,0,4,4,0,4}, source, 0);
            assertArrayEquals(source, session.dragBefore(), 0);
            assertFalse(physics.buildFromSource(session.dragCandidate(), 4).isValid());
            TiledObjectGeometry.validate(session.dragCandidate());
            session.cancelDrag();
            assertArrayEquals(source, session.getBeforeVerts(), 0);
            assertEquals(17, session.getBodyEid()); assertEquals(9, session.getFixtureId());
            session.beginCreate(21);
            assertTrue(session.isCreateMode()); assertEquals(21, session.getBodyEid());
            assertEquals(0, session.getBeforeCount());
            assertTrue(physics.buildFromSource(new float[]{0,0,4,0,4,4,2,1,0,4}, 5).isValid());
        } finally { world.dispose(); }
    }
}
