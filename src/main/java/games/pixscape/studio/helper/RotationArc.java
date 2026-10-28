package games.pixscape.studio.helper;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import space.earlygrey.shapedrawer.ShapeDrawer;

/** Shared arc rendering and angular arithmetic; clients own the projection and gesture lifetime. */
public final class RotationArc {
    public interface Projection { void point(float degrees,Vector2 out); }
    private final Vector2 point=new Vector2();
    public void draw(ShapeDrawer drawer,Projection projection,float start,float end,int segments,float thickness){
        projection.point(start,point);float x=point.x,y=point.y;
        for(int i=1;i<=segments;i++){
            projection.point(start+(end-start)*i/segments,point);
            drawer.line(x,y,point.x,point.y,thickness);x=point.x;y=point.y;
        }
    }
    public static void handle(ShapeDrawer drawer,float pivotX,float pivotY,float x,float y,float scale){
        drawer.line(pivotX,pivotY,x,y,scale);drawer.circle(x,y,4f*scale,1.5f*scale);
    }
    public static boolean hit(float x,float y,float hx,float hy,float radius){
        return Vector2.dst2(x,y,hx,hy)<=radius*radius;
    }
    public static float delta(float from,float to){
        return MathUtils.atan2(MathUtils.sin(to-from),MathUtils.cos(to-from));
    }
    /** Incremental unwrapping preserves continuity across zero and multiple complete turns. */
    public static final class Gesture {
        private float previous,total,factor;
        public void begin(float angle,boolean fine){previous=angle;total=0;factor=fine?.1f:1f;}
        public float update(float angle){
            double step=angle-previous;
            // Avoid lookup-table drift on incremental gestures; keep the historical light delta unchanged.
            total+=(float)Math.atan2(Math.sin(step),Math.cos(step));previous=angle;
            return total*MathUtils.radiansToDegrees*factor;
        }
    }
}
