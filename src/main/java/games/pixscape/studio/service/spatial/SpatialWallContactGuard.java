package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.*;

/** Swept axis-aligned contact query over the gesture's immutable external candidates. */
final class SpatialWallContactGuard {
    static final float ARM_PIXELS = 12f, DISARM_PIXELS = 6f;
    final Array<SpatialBlockData> external = new Array<>(SpatialBlockData.class);
    int referenceId, movingId, face;
    boolean ambiguous, armed;
    float amount = 1f;
    float metricX = 1f, metricY = 1f;

    SpatialWallContactGuard(SpatialBlocksComponent source, SpatialBlockData seed) {
        SpatialWallGeometry.LinkedCellBounds region = new SpatialWallGeometry.LinkedCellBounds();
        for (int i=0;i<source.blocks.size;i++) { SpatialBlockData member=source.blocks.get(i); if (member.structureId == seed.structureId) {
            SpatialWallGeometry.extractLinkedCellBounds(member, region);
            for (int j=0;j<source.blocks.size;j++) { SpatialBlockData wall=source.blocks.get(j); if (wall.structureId != seed.structureId
                    && wall.x + wall.width >= region.minGx && wall.x <= region.maxGxExclusive
                    && wall.y + wall.depth >= region.minGy && wall.y <= region.maxGyExclusive
                    && !external.contains(wall, true)) external.add(wall); }
        } }
    }

    void metrics(float xx, float xy, float yx, float yy) {
        float determinant = Math.abs(xx * yy - xy * yx);
        metricX = determinant / (float)Math.sqrt(yx*yx + yy*yy);
        metricY = determinant / (float)Math.sqrt(xx*xx + xy*xy);
    }

    /** Each rectangle side is affine along this segment: no event-size dependent sampling. */
    void sweep(Array<SpatialBlockData> from, Array<SpatialBlockData> to, int structure) {
        amount = 1f; referenceId = movingId = face = 0; ambiguous = false;
        for (int i=0;i<from.size;i++) {
            SpatialBlockData a=from.get(i), b=to.get(i);
            if (a.structureId != structure) continue;
            for (SpatialBlockData fixed : external) {
                float enter=0, exit=1; int hitFace=0;
                float[] gaps={fixed.x-(a.x+a.width), a.x-(fixed.x+fixed.width),
                        fixed.y-(a.y+a.depth), a.y-(fixed.y+fixed.depth)};
                float[] end={fixed.x-(b.x+b.width), b.x-(fixed.x+fixed.width),
                        fixed.y-(b.y+b.depth), b.y-(fixed.y+fixed.depth)};
                boolean possible=true;
                for(int axis=0;axis<4;axis++) {
                    float delta=end[axis]-gaps[axis];
                    if (Math.abs(delta)<1e-7f) { if(gaps[axis]>=-1e-7f) {possible=false;break;} }
                    else if(delta<0) {
                        float t=-gaps[axis]/delta;
                        if(t>=enter-1e-6f) { enter=Math.max(enter,t); hitFace=new int[]{2,1,8,4}[axis]; }
                    } else exit=Math.min(exit,-gaps[axis]/delta);
                }
                if(!possible || hitFace==0 || enter<0 || enter>1 || exit<=enter+1e-7f) continue;
                if(enter<amount-1e-6f || referenceId==0 && enter<=amount) {
                    amount=enter; referenceId=fixed.id; movingId=a.id; face=hitFace; ambiguous=false;
                } else if(Math.abs(enter-amount)<=1e-6f && (referenceId!=fixed.id || face!=hitFace)) ambiguous=true;
            }
        }
    }

    float overshoot(SpatialBlockData requested, SpatialBlockData blocked) {
        float sign=face==2 || face==8 ? 1f : -1f;
        return sign*(SpatialBlockLinks.coordinate(requested,face)-SpatialBlockLinks.coordinate(blocked,face))
                *(face<=2 ? metricX : metricY);
    }

    void intention(float pixels, int selectedId, int manipulatedFaces) {
        // A propagated member can stop the edit, but is not the face pushed by this handle.
        if(ambiguous || selectedId>0 && movingId!=selectedId || (face & manipulatedFaces)==0) armed=false;
        else if(!armed && pixels>=ARM_PIXELS-0.001f) armed=true;
        else if(armed && pixels<=DISARM_PIXELS) armed=false;
    }
}
