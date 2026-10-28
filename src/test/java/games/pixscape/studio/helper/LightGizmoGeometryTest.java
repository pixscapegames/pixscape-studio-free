package games.pixscape.studio.helper;

import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import org.junit.Test;

import static org.junit.Assert.*;

public class LightGizmoGeometryTest {
    @Test public void pointSectionMatchesTheRuntimeSphereAtSourceHeight() {
        float radius = 400f;
        for (float height : new float[]{0f, 180f, -80f}) {
            Vector2 source = LightGizmoGeometry.source(130f, -70f, height, new Vector2());
            float worldX = LightingCoordinates.worldX(130f, -70f, 0f);
            float worldY = LightingCoordinates.worldY(130f, -70f, 0f);
            for (int i = 0; i < 48; i++) {
                float angle = (float)(Math.PI * 2 * i / 48);
                Vector2 point = LightGizmoGeometry.pointSectionPoint(source.x, source.y, angle, radius, new Vector2());
                float dx = LightingCoordinates.worldX(point.x, point.y, height) - worldX;
                float dy = LightingCoordinates.worldY(point.x, point.y, height) - worldY;
                assertEquals(radius, (float)Math.sqrt(dx * dx + dy * dy), .001f);
                assertEquals(radius, LightGizmoGeometry.radiusFromScreenDelta(point.x-source.x, point.y-source.y), .001f);
            }
        }
    }

    @Test public void pointRadiusHandleIsOnTheRightOfTheSourceHeightEllipse() {
        Vector2 endpoint = LightGizmoGeometry.pointSectionPoint(100f, 200f, 0f, 400f, new Vector2());
        assertEquals(100f + (float)Math.sqrt(2) * 400f, endpoint.x, .001f);
        assertEquals(200f, endpoint.y, .001f);
        Vector2 upper = LightGizmoGeometry.pointSectionPoint(100f, 200f, (float)Math.PI/2, 400f, new Vector2());
        assertEquals(100f, upper.x, .001f);
        assertEquals(200f + 400f/(float)Math.sqrt(2), upper.y, .001f);
    }

    @Test public void pointHeightMovesTheWholeSectionWithoutChangingItsRange() {
        for (float angle : new float[]{0f, 1f, 2f, 3f}) {
            Vector2 zero = LightGizmoGeometry.pointSectionPoint(130f, -70f, angle, 400f, new Vector2());
            Vector2 raised = LightGizmoGeometry.pointSectionPoint(130f, 110f, angle, 400f, new Vector2());
            assertEquals(zero.x, raised.x, 0f);
            assertEquals(180f, raised.y-zero.y, .001f);
        }
    }

    @Test public void sourceMatchesRuntimeProjectionIncludingZeroAndNegativeHeights() {
        for (float height : new float[]{0f, 128f, -128f, 100000f}) {
            Vector2 source = LightGizmoGeometry.source(130f, -70f, height, new Vector2());
            float x = LightingCoordinates.worldX(130f, -70f, 0f);
            float y = LightingCoordinates.worldY(130f, -70f, 0f);
            assertEquals(LightingCoordinates.screenX(x,y), source.x, 0f);
            assertEquals(LightingCoordinates.screenY(x,y,height), source.y, 0f);
            assertEquals(-70f + height, source.y, .001f);
        }
    }

    @Test public void groundUsesTheSameDimensionsOriginAndParentPoseAsRuntimeBounds() {
        TransformComponent t = new TransformComponent(); t.x=3; t.y=4;
        t.originX=2; t.originY=5;
        DimensionsComponent dim=new DimensionsComponent(); dim.width=20;dim.height=30;
        WorldTransformState parent=new WorldTransformState(4);
        parent.setResolved(2,40f,50f,(float)Math.PI/2f,2f,3f);
        Vector2 ground=LightGizmoGeometry.ground(parent,2,t,dim,new Vector2());
        assertEquals(10f,ground.x,.01f);assertEquals(66f,ground.y,.01f);
        assertEquals(3f,t.x,0f);assertEquals(4f,t.y,0f);
    }

    @Test public void controlsHaveLogicalPixelPickingIndependentOfZoomAndFramebufferScale() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        com.badlogic.gdx.Graphics previous=com.badlogic.gdx.Gdx.graphics;
        try { for(float zoom:new float[]{.25f,1f,4f}) for(int dpi:new int[]{1,2,3}) {
            com.badlogic.gdx.Gdx.graphics=(com.badlogic.gdx.Graphics)java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Graphics.class.getClassLoader(),new Class[]{com.badlogic.gdx.Graphics.class},
                    (p,m,a)->switch(m.getName()) {
                        case "getWidth" -> 1200; case "getHeight" -> 800;
                        case "getBackBufferWidth" -> 1200*dpi; case "getBackBufferHeight" -> 800*dpi;
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
            var camera=new com.badlogic.gdx.graphics.OrthographicCamera(900,600);
            camera.zoom=zoom;camera.position.set(125,-75,0);camera.update();
            var viewport=new com.badlogic.gdx.utils.viewport.ScreenViewport(camera);
            viewport.setScreenBounds(100,50,900,600);
            var coords=new games.pixscape.studio.service.CoordSpaces(camera,viewport,null,null);
            float wpp=coords.worldUnitsPerPixel();
            Vector2 center=coords.worldToScreen(125,-75,new Vector2());
            Vector2 handle=coords.worldToScreen(125,-75-32*wpp,new Vector2());
            assertEquals(32f,center.y-handle.y,.001f);
            Vector2 pointer=coords.screenToWorld(handle.x,800-handle.y,new Vector2());
            assertTrue(LightGizmoGeometry.heightHit((pointer.x-125)/wpp,(pointer.y+75)/wpp));
            Vector2 start=coords.screenToWorld(center.x,800-center.y,new Vector2());
            Vector2 moved=coords.screenToWorld(center.x,800-center.y-20,new Vector2());
            assertEquals(20*zoom,moved.y-start.y,.001f);
            assertFalse(LightGizmoGeometry.heightHit(0,0));
            assertFalse(LightGizmoGeometry.intensityHit(0,0));
            assertFalse(LightGizmoGeometry.heightHit(32,24));
            assertFalse(LightGizmoGeometry.intensityHit(0,-32));
            assertFalse(LightGizmoGeometry.heightHit(0,-14));
        }} finally {com.badlogic.gdx.Gdx.graphics=previous;}
    }

    @Test public void movingAParentedLightUsesTheInverseParentFrame() {
        WorldTransformState parent=new WorldTransformState(4);
        parent.setResolved(2,40,50,(float)Math.PI/2,2,2);
        Vector2 delta=LightGizmoGeometry.localGroundDelta(parent,2,20,10,new Vector2());
        assertEquals(5,delta.x,.001f);assertEquals(-10,delta.y,.001f);
        assertEquals(20,parent.m00[2]*delta.x+parent.m01[2]*delta.y,.001f);
        assertEquals(10,parent.m10[2]*delta.x+parent.m11[2]*delta.y,.001f);
    }

    @Test public void radiusControlRemainsReachableAtCoincidentEndpoints() {
        for(float wpp:new float[]{.25f,1,4}) for(Vector2 endpoint:new Vector2[]{
                new Vector2(0,0),new Vector2(0,-32),new Vector2(32,24)}) {
            Vector2 control=LightGizmoGeometry.radiusControl(100,200,
                    100+endpoint.x*wpp,200+endpoint.y*wpp,wpp,new Vector2());
            float dx=(control.x-100)/wpp,dy=(control.y-200)/wpp;
            assertFalse(LightGizmoGeometry.heightHit(dx,dy));
            assertFalse(LightGizmoGeometry.intensityHit(dx,dy));
            assertFalse(Math.abs(dx)<=24 && Math.abs(dy)<=24);
        }
    }
}
