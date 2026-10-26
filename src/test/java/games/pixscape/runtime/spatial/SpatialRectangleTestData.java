package games.pixscape.runtime.spatial;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
/** Historical sorting fixtures retain their envelopes through explicit rectangles. */
public final class SpatialRectangleTestData {
 public static ActorSpatialRectangles rectangles(float height) {
  ActorSpatialRectangles r=new ActorSpatialRectangles(); r.staticRectangle=new ActorSpatialRectangleData();
  r.staticRectangle.height=height;r.staticRectangle.width=height/(float)Math.sqrt(2);r.staticRectangle.orientation=315;
  return r;
 }
 public static void setHeight(SpatialHeightComponent spatial,float height){spatial.rectangles=rectangles(height);}
}
