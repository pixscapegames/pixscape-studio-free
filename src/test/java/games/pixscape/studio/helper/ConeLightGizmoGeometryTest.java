package games.pixscape.studio.helper;

import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConeLightGizmoGeometryTest {
    @Test public void inclinedMeridianProjectsTheAxisAndPreservesTheZeroInclinationSection() {
        for(float direction:new float[]{-2.1935954f,0f,.6f,1.57f}) {
            Vector2 horizontal=sectionDirection(direction);
            for(float pitch:new float[]{-90f,-33.5f,0f,27.25f,90f}) for(float offset:new float[]{-.5f,0f,.5f}) {
                float cos=(float)Math.cos(offset),sin=(float)Math.sin(offset);
                float cp=Math.abs(pitch)==90?0:com.badlogic.gdx.math.MathUtils.cosDeg(pitch);
                float z=com.badlogic.gdx.math.MathUtils.sinDeg(pitch)*cos*400;
                Vector2 projected=ConeLightGizmoGeometry.tiltedSectionPoint(100,200,direction,pitch,offset,400,new Vector2());
                float x=(horizontal.x*cp*cos-horizontal.y*sin)*400,y=(horizontal.y*cp*cos+horizontal.x*sin)*400;
                assertEquals(x,LightingCoordinates.worldX(projected.x-100,projected.y-200,z),.001f);
                assertEquals(y,LightingCoordinates.worldY(projected.x-100,projected.y-200,z),.001f);
                if(pitch==0)assertEquals(ConeLightGizmoGeometry.sectionPoint(100,200,direction,offset,400,new Vector2()),projected);
            }
        }
    }

    @Test public void inclinationArcKeepsLogicalSizeAndAngularPickingAtEveryZoom() {
        for(float direction:new float[]{-2.2f,0f,.6f,2.8f}) for(float pitch:new float[]{-80f,-33.5f,0f,27.25f,80f}) {
            Vector2 reference=null;
            for(float wpp:new float[]{.25f,1f,4f}) {
                Vector2 p=ConeLightGizmoGeometry.inclinationArcPoint(100,200,direction,pitch,wpp,new Vector2());
                Vector2 logical=new Vector2(p).sub(100,200).scl(1/wpp);
                if(reference==null)reference=logical;else assertTrue(reference.epsilonEquals(logical,.001f));
                float angle=ConeLightGizmoGeometry.inclinationPointerAngle(100,200,direction,wpp,p.x,p.y);
                assertEquals(pitch,angle*com.badlogic.gdx.math.MathUtils.radiansToDegrees,.05f);
                Vector2 control=LightGizmoGeometry.radiusControl(100,200,p.x,p.y,wpp,new Vector2());
                assertFalse(ConeLightGizmoGeometry.inclinationControlNear((control.x-100)/wpp,(control.y-200)/wpp,8));
            }
        }
        assertTrue(ConeLightGizmoGeometry.inclinationEdgeOn((float)Math.PI/2));
        assertTrue(ConeLightGizmoGeometry.inclinationEdgeOn(-(float)Math.PI/2));
        assertFalse(ConeLightGizmoGeometry.inclinationEdgeOn(0));
    }

    private static Vector2 sectionDirection(float angle) {
        Vector2 p=ConeLightGizmoGeometry.sectionPoint(0,0,angle,0,1,new Vector2());
        return new Vector2(LightingCoordinates.worldX(p.x,p.y,0),LightingCoordinates.worldY(p.x,p.y,0));
    }

    @Test public void projectedSectionUsesRuntimeRadiusAndPhysicalOpening() {
        for(float angle:new float[]{0f,.3f,-2.1935954f,2f}) {
            Vector2 center=ConeLightGizmoGeometry.sectionPoint(0,0,angle,0,519.47705f,new Vector2());
            float cx=LightingCoordinates.worldX(center.x,center.y,0),cy=LightingCoordinates.worldY(center.x,center.y,0);
            for(float offset:new float[]{-.5235988f,0f,.5235988f}) {
                Vector2 point=ConeLightGizmoGeometry.sectionPoint(0,0,angle,offset,519.47705f,new Vector2());
                assertEquals(519.47705f,LightGizmoGeometry.radiusFromScreenDelta(point.x,point.y),.001f);
                float x=LightingCoordinates.worldX(point.x,point.y,0),y=LightingCoordinates.worldY(point.x,point.y,0);
                float cosine=(cx*x+cy*y)/(519.47705f*519.47705f);
                assertEquals(Math.cos(offset),cosine,.001f);
            }
        }
    }

    @Test public void sourceHeightTranslatesTheEntireSectionExactlyOnce() {
        Vector2 zero=ConeLightGizmoGeometry.sectionPoint(426.80966f,4240.0938f,-2.1935954f,.3f,519.47705f,new Vector2());
        Vector2 raised=ConeLightGizmoGeometry.sectionPoint(426.80966f,4368.0938f,-2.1935954f,.3f,519.47705f,new Vector2());
        assertEquals(zero.x,raised.x,0f);assertEquals(128f,raised.y-zero.y,.001f);
    }

    @Test public void rotatingAtConstantPhysicalRangeKeepsRadiusOnTheProjectedEllipse() {
        for(float angle:new float[]{-.6f,0f,.6f,1.7f}) {
            Vector2 point=ConeLightGizmoGeometry.sectionPoint(100,200,angle,0,320,new Vector2());
            assertEquals(320f,LightGizmoGeometry.radiusFromScreenDelta(point.x-100,point.y-200),.001f);
            float screenAngle=(float)Math.atan2(point.y-200,point.x-100);
            assertEquals(angle,screenAngle,.001f);
        }
    }
}
