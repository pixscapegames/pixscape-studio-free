package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import games.pixscape.runtime.service.NormalMapAtlas;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

/** CPU-only preparation of scene normal inputs and independently packed, alpha-bounded pages. */
public final class NormalAtlasPackingService {
    private NormalAtlasPackingService() {}
    public static FileHandle input(FileHandle colorInput) { return colorInput.sibling(colorInput.name()+NormalMapAtlas.SUFFIX); }

    public static AtlasInputSyncResult sync(FileHandle project,FileHandle colors,AssetMetaDatabase db,Set<String> requiredPaths) {
        FileHandle normals=input(colors);normals.mkdirs();Set<String> required=new HashSet<>();
        int copied=0,deleted=0;StringBuilder manifest=new StringBuilder();
        if(db!=null)for(int i=0;i<db.size();i++){
            if(!(db.assetAt(i) instanceof TileAssetMeta tile) || tile.normalSourceRelPath==null || tile.sourceRelPath()==null)continue;
            if(requiredPaths!=null ? !requiredPaths.contains(tile.sourceRelPath()) : !colors.child(new FileHandle(tile.sourceRelPath()).name()).exists())continue;
            FileHandle source=project.child(tile.normalSourceRelPath);
            if(!source.exists() || source.isDirectory())throw new IllegalArgumentException("Declared normal resource is missing: asset "+tile.id()+" at "+source);
            String name="normal__a"+tile.id()+".png";required.add(name);
            FileHandle dest=normals.child(name);
            try {if(!dest.exists() || java.nio.file.Files.mismatch(source.file().toPath(),dest.file().toPath())!=-1){source.copyTo(dest);copied++;}}
            catch(IOException failure){throw new IllegalStateException("Cannot copy normal for asset "+tile.id(),failure);}
            manifest.append(tile.id()).append('\t').append(new FileHandle(tile.sourceRelPath()).name()).append('\n');
        }
        for(FileHandle file:normals.list())if(file.extension().equalsIgnoreCase("png") && !required.contains(file.name())){
            if(!file.delete())throw new IllegalStateException("Cannot retire unused normal input: "+file);deleted++;
        }
        FileHandle list=normals.child("sources.tsv");String data=manifest.toString();
        if(!list.exists() || !list.readString("UTF-8").equals(data)){list.writeString(data,false,"UTF-8");copied++;}
        return new AtlasInputSyncResult(copied!=0 || deleted!=0,copied,deleted);
    }

    public static void pack(FileHandle colors,FileHandle output,String tag) {
        FileHandle normals=input(colors),manifest=normals.child("sources.tsv");
        if(!manifest.exists() || manifest.readString("UTF-8").isBlank())return;
        TexturePacker.Settings settings=new TexturePacker.Settings();
        settings.minWidth=settings.minHeight=16;settings.maxWidth=settings.maxHeight=2048;
        settings.paddingX=settings.paddingY=4;settings.duplicatePadding=true;settings.edgePadding=true;
        settings.rotation=false;settings.stripWhitespaceX=settings.stripWhitespaceY=false;
        settings.premultiplyAlpha=false;settings.bleed=false;settings.silent=true;
        TexturePacker packer=new TexturePacker(settings);Map<String,int[]> bounds=new HashMap<>();
        for(String line:manifest.readString("UTF-8").split("\n")){
            String[] entry=line.split("\t",2);int id=Integer.parseInt(entry[0]);String name="normal__a"+id;
            try {
                BufferedImage color=ImageIO.read(colors.child(entry[1]).file()),normal=ImageIO.read(normals.child(name+".png").file());
                if(color==null || normal==null || color.getWidth()!=normal.getWidth() || color.getHeight()!=normal.getHeight())
                    throw new IllegalArgumentException("Invalid normal/color dimensions: asset "+id);
                int x0=color.getWidth(),y0=color.getHeight(),x1=0,y1=0;
                for(int y=0;y<color.getHeight();y++)for(int x=0;x<color.getWidth();x++)if((color.getRGB(x,y)>>>24)!=0){
                    x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x+1);y1=Math.max(y1,y+1);
                }
                if(x1<=x0 || y1<=y0)continue;
                bounds.put(name,new int[]{x0,color.getHeight()-y1,color.getWidth(),color.getHeight()});
                // Cropping is representation only: the source and every retained RGB byte stay untouched.
                BufferedImage cropped=new BufferedImage(x1-x0,y1-y0,BufferedImage.TYPE_INT_RGB);
                java.awt.image.Raster raster=normal.getRaster();
                java.awt.image.IndexColorModel palette=normal.getColorModel() instanceof java.awt.image.IndexColorModel indexed?indexed:null;
                if(palette==null && (raster.getNumBands()<3 || raster.getSampleModel().getSampleSize(0)!=8 || normal.isAlphaPremultiplied()))
                    throw new IllegalArgumentException("Normal data requires unpremultiplied 8-bit RGB: asset "+id);
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
                    int rgb;
                    if(palette!=null){int index=raster.getSample(x,y,0);rgb=palette.getRed(index)<<16|palette.getGreen(index)<<8|palette.getBlue(index);}
                    else rgb=raster.getSample(x,y,0)<<16|raster.getSample(x,y,1)<<8|raster.getSample(x,y,2);
                    cropped.setRGB(x-x0,y-y0,rgb);
                }
                // Normal alpha is never coverage. Opaque storage prevents Java2D alpha compositing
                // from changing data RGB, including source pixels whose normal alpha is zero.
                packer.addImage(cropped,name);
            } catch(IOException failure){throw new IllegalArgumentException("Cannot prepare normal for asset "+id,failure);}
        }
        if(bounds.isEmpty())return;
        output.mkdirs();String atlasName=tag+NormalMapAtlas.SUFFIX;packer.pack(output.file(),atlasName);
        FileHandle descriptor=output.child(atlasName+".atlas");TextureAtlasData data=new TextureAtlasData(descriptor,output,false);
        StringBuilder text=new StringBuilder();
        Map<TextureAtlasData.Page,List<TextureAtlasData.Region>> regionsByPage=new IdentityHashMap<>();
        for(TextureAtlasData.Region region:data.getRegions())regionsByPage.computeIfAbsent(region.page,key->new ArrayList<>()).add(region);
        for(TextureAtlasData.Page page:data.getPages()){
            text.append(page.textureFile.name()).append("\nsize:").append((int)page.width).append(',').append((int)page.height)
                    .append("\nformat:RGBA8888\nfilter:Linear,Linear\nrepeat:none\n");
            for(TextureAtlasData.Region region:regionsByPage.getOrDefault(page,Collections.emptyList())){
                int[] crop=bounds.get(region.name);if(crop==null)throw new IllegalStateException("Unowned packed normal: "+region.name);
                text.append(region.name).append("\nbounds:").append(region.left).append(',').append(region.top).append(',').append(region.width).append(',').append(region.height)
                        .append("\noffsets:").append(crop[0]).append(',').append(crop[1]).append(',').append(crop[2]).append(',').append(crop[3]).append('\n');
            }
            text.append('\n');
        }
        descriptor.writeString(text.toString(),false,"UTF-8");
        new TextureAtlasData(descriptor,output,false);
    }
}
