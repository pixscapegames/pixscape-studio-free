package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.render.lighting.AlphaBounds;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;

/** Cold import/save preparation from source pixels only. Repacking does not invalidate this cache. */
public final class AlphaBoundsPreparation {
    public static final float CASTER_THRESHOLD=.5f;
    private AlphaBoundsPreparation() {}
    public static int prepare(AssetMetaDatabase db,FileHandle project){return prepare(db,project,CASTER_THRESHOLD);}
    public static int prepare(AssetMetaDatabase db,FileHandle project,float threshold){
        if(!Float.isFinite(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("Invalid caster threshold");
        Map<AssetMeta,Array<AlphaRegionMeta>> prepared=new LinkedHashMap<>();int scans=0;
        for(int i=0;i<db.size();i++){
            AssetMeta asset=db.assetAt(i);if(!(asset instanceof ImageAssetMeta||asset instanceof TileAssetMeta||asset instanceof AnimationAssetMeta)||asset.sourceRelPath()==null)continue;
            FileHandle source=project.child(asset.sourceRelPath());FileHandle[] files;
            if(asset instanceof AnimationAssetMeta){if(!source.isDirectory()){prepared.put(asset,new Array<>());continue;}files=source.list();Arrays.sort(files,Comparator.comparing(FileHandle::name));}
            else files=new FileHandle[]{source};
            Array<AlphaRegionMeta> regions=new Array<>();Set<Integer> frames=new HashSet<>();Map<String,AlphaRegionMeta> old=new HashMap<>();
            if(asset.alphaRegions!=null)for(var r:asset.alphaRegions)if(old.put(r.sourceKey,r)!=null)throw new IllegalArgumentException("Duplicate alpha region source");
            for(FileHandle file:files){
                if(file.isDirectory()||!file.exists()||!Set.of("png","jpg","jpeg","bmp").contains(file.extension().toLowerCase(Locale.ROOT)))continue;
                int frame=0;if(asset instanceof AnimationAssetMeta){String n=file.nameWithoutExtension();int at=n.lastIndexOf('_');try{frame=Integer.parseInt(n.substring(at+1));}catch(NumberFormatException e){throw new IllegalArgumentException("Animation source frame has no numeric suffix: "+file,e);}if(frame<0||!frames.add(frame))throw new IllegalArgumentException("Duplicate alpha animation frame");}
                try{
                    byte[] bytes=file.readBytes();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));String key=file.name();AlphaRegionMeta previous=old.get(key);
                    if(previous!=null&&hash.equals(previous.sourceHash)&&previous.frame==frame&&previous.bounds!=null&&previous.bounds.usable(threshold)){regions.add(previous);continue;}
                    BufferedImage pixels=ImageIO.read(new ByteArrayInputStream(bytes));if(pixels==null)throw new IllegalArgumentException("Unsupported image pixels: "+file);
                    AlphaRegionMeta region=new AlphaRegionMeta();region.frame=frame;region.sourceKey=key;region.sourceHash=hash;region.bounds=scan(pixels,threshold);regions.add(region);scans++;
                }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("Cannot prepare source alpha bounds: "+file,e);}
            }
            regions.sort(Comparator.comparingInt(r->r.frame));prepared.put(asset,regions);
        }
        // Publish only after every source could be prepared successfully.
        for(var e:prepared.entrySet())e.getKey().alphaRegions=e.getValue();return scans;
    }
    public static AlphaBounds scan(BufferedImage pixels,float threshold){
        int w=pixels.getWidth(),h=pixels.getHeight(),left=w,top=h,right=-1,bottom=-1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(((pixels.getRGB(x,y)>>>24)&255)/255f>=threshold){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        AlphaBounds b=new AlphaBounds();b.sourceWidth=w;b.sourceHeight=h;b.threshold=threshold;b.empty=right<0;
        if(b.empty){b.minU=b.minV=b.maxU=b.maxV=0;}else{b.minU=left/(float)w;b.maxU=(right+1)/(float)w;b.minV=1-(bottom+1)/(float)h;b.maxV=1-top/(float)h;}return b;
    }
}
