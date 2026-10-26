package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import games.pixscape.runtime.render.lighting.LightingFrame;
import games.pixscape.runtime.render.lighting.LightingSurface;
import games.pixscape.runtime.system.DirtyTrackerSystem;

/** Explicit bounded capture tool. Never enabled by the product launcher; no authored writes. */
final class JunctionOrbitDiagnostic {
    private final FileHandle directory;
    private int pose=-1;
    private long changed;
    private boolean captured;
    private int mode;
    private final float[][] centers={{2496,2112},{3648,3136},{3456,4224}};
    static JunctionOrbitDiagnostic requested() {
        String path=System.getProperty("pixscape.poc.junctionCapture");
        return path==null?null:new JunctionOrbitDiagnostic(Gdx.files.absolute(path));
    }
    private JunctionOrbitDiagnostic(FileHandle path){directory=path;directory.mkdirs();}
    void before(PixscapeEngine engine) {
        if(pose>=35&&captured)return;
        if(pose>=0&&(!captured||System.nanoTime()-changed<500_000_000L))return;
        pose++;captured=false;changed=System.nanoTime();
        int corner=pose/12,angle=(pose%12)*30;float height=corner<2?40:200;
        float cx=centers[corner][0],cy=centers[corner][1];
        float x=cx+320*(float)Math.cos(Math.toRadians(angle)),y=cy+320*(float)Math.sin(Math.toRadians(angle));
        int entity=engine.findEntityByStableId(680);
        if(entity<0)throw new IllegalStateException("Diagnostic light 680 is absent");
        var world=engine.getWorld();var transform=world.getMapper(TransformComponent.class).get(entity);
        var light=world.getMapper(PointLightComponent.class).get(entity);
        transform.x=LightingCoordinates.screenX(x,y);transform.y=LightingCoordinates.screenY(x,y,0);light.height=height;
        world.getSystem(DirtyTrackerSystem.class).geometry(entity,GeometryDirty.POSITION);
        var camera=engine.getCamera();camera.position.set(cx-cy,(cx+cy)*.5f+80,0);camera.zoom=corner<2?.65f:1.25f;camera.update();
        directory.child("poses.log").writeString("pose="+pose+" corner="+corner+" angle="+angle+" lightXYZ="+x+","+y+","+height+"\n",true,"UTF-8");
        mode=0;request();
    }
    private void request(){
        System.setProperty("pixscape.poc.witnessReadback",directory.child(String.format(java.util.Locale.ROOT,"pose-%02d-mode-%d",pose,mode)).path());
        if(mode==1)System.setProperty("pixscape.poc.forceShadowVisibility","true");else System.clearProperty("pixscape.poc.forceShadowVisibility");
        if(mode>=2)System.setProperty("pixscape.poc.lightingDiagnostic",Integer.toString(mode));else System.clearProperty("pixscape.poc.lightingDiagnostic");
    }
    void after(PixscapeEngine engine) {
        if(captured||pose<0||System.nanoTime()-changed<150_000_000L)return;
        Pixmap pixels=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
        try {if(mode<5)png.write(directory.child(String.format(java.util.Locale.ROOT,"pose-%02d-mode-%d.png",pose,mode)),pixels);}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
        finally{png.dispose();pixels.dispose();}
        if(mode==0&&pose%12==0)dump(engine,pose/12);
        if(mode==7){
            System.clearProperty("pixscape.poc.forceShadowVisibility");System.clearProperty("pixscape.poc.lightingDiagnostic");System.clearProperty("pixscape.poc.witnessReadback");captured=true;
            if(pose==35)Gdx.app.log("JunctionOrbitDiagnostic","COMPLETE 36 poses, 8 diagnostic states and GPU readbacks; awaiting watchdog");
        }else{mode++;request();}
    }
    private void dump(PixscapeEngine engine,int corner) {
        LightingFrame frame=engine.getFrameQueue().lighting;
        StringBuilder out=new StringBuilder("kind\tref\tblock\tcornerKey\tidentity\ttriangle\txyz_uv_normal\n");
        for(var entry:frame.tiles)append(out,"receiver",entry.key,entry.value,corner,false);
        for(var caster:frame.casters)append(out,"caster",caster.tiledRef,caster.surface,corner,true);
        directory.child("geometry-"+corner+".tsv").writeString(out.toString(),false,"UTF-8");
        var queue=engine.getFrameQueue();StringBuilder order=new StringBuilder("order\tdomain\tref\tentity\tlayer\tblend\tquad\n");
        for(int i=0;i<queue.size;i++)order.append(i).append('\t').append(queue.sourceDomain[i]).append('\t').append(queue.sourceSlot[i]).append('\t').append(queue.sourceEntity[i]).append('\t').append(queue.layerIndex[i]).append('\t').append(queue.blend[i]).append('\t').append(queue.x1[i]).append(',').append(queue.y1[i]).append(',').append(queue.x2[i]).append(',').append(queue.y2[i]).append(',').append(queue.x3[i]).append(',').append(queue.y3[i]).append(',').append(queue.x4[i]).append(',').append(queue.y4[i]).append('\n');
        directory.child("queue-"+corner+".tsv").writeString(order.toString(),false,"UTF-8");
        StringBuilder provenance=new StringBuilder("ref\townerEntity\ttriangle\tcompleted\ttwoSided\n");
        for(var entry:frame.tiles)for(int o=0;o<entry.value.vertices.length;o+=24){float[] p=entry.value.vertices;provenance.append(entry.key).append('\t').append(entry.value.ownerEntity).append('\t').append(o/24).append('\t').append(p[o+5]==0&&p[o+6]==0&&p[o+7]==0).append('\t').append(entry.value.twoSided).append('\n');}
        directory.child("provenance-"+corner+".tsv").writeString(provenance.toString(),false,"UTF-8");
        StringBuilder lights=new StringBuilder();for(var light:frame.lights)lights.append("actualLightXYZ=").append(light.x).append(',').append(light.y).append(',').append(light.z).append(" quality=").append(light.quality).append(" resolution=").append(light.resolution).append('\n');
        directory.child("poses.log").writeString(lights.toString(),true,"UTF-8");
    }
    private void append(StringBuilder out,String kind,int ref,LightingSurface surface,int corner,boolean caster) {
        float[] p=!caster&&surface.receiverVertices!=null?surface.receiverVertices:surface.vertices;
        for(int o=0;o<p.length;o+=24){
            boolean near=false;for(int i=0;i<3;i++)if(Math.abs(p[o+i*8]-centers[corner][0])<200&&Math.abs(p[o+i*8+1]-centers[corner][1])<200)near=true;
            // Full scene dump also includes the other storey and off-camera casters.
            out.append(kind).append('\t').append(ref).append('\t').append(!caster&&surface.receiverBlockIds!=null?surface.receiverBlockIds[o/24]:surface.pocSourceBlockId).append('\t')
                .append(surface.roundedIdentity?Long.toString(surface.roundedCellKey):"-").append('\t').append(caster?surface.selfShadowExclusionId:surface.receiverExclusionId(o)).append('\t').append(o/24).append('\t');
            for(int i=0;i<24;i++)out.append(p[o+i]).append(i==23?'\n':',');
        }
    }
}
