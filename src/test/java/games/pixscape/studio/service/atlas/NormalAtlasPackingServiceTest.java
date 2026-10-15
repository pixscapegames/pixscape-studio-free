package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.asset.AssetMeta.AssetScope;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Set;

public class NormalAtlasPackingServiceTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void trimsUseColorAlphaPreserveRgbAndPadEachIdentity() throws Exception {
        FileHandle root=new FileHandle(temp.newFolder()),colors=root.child("input/scene"),out=root.child("output");colors.mkdirs();
        AssetMetaDatabase db=new AssetMetaDatabase();
        for(int i=0;i<2;i++) {
            String source="tile"+i+".png",normal="normal"+i+".png";
            TileAssetMeta tile=(TileAssetMeta)db.registerIfAbsent(AssetType.TILE,"tiles/tile"+i,source,AssetScope.USER);
            tile.normalSourceRelPath=normal;
            BufferedImage c=new BufferedImage(16,24,BufferedImage.TYPE_INT_ARGB),n=new BufferedImage(16,24,BufferedImage.TYPE_INT_ARGB);
            for(int y=8;y<20;y++)for(int x=4;x<11;x++)c.setRGB(x,y,0xff909090);
            for(int y=0;y<24;y++)for(int x=0;x<16;x++)n.setRGB(x,y,i==0?0x0012abef:0xffed3412);
            ImageIO.write(c,"png",root.child(source).file());ImageIO.write(c,"png",colors.child(source).file());ImageIO.write(n,"png",root.child(normal).file());
        }
        NormalAtlasPackingService.sync(root,colors,db,Set.of("tile0.png","tile1.png"));
        NormalAtlasPackingService.pack(colors,out,"scene");
        TextureAtlasData data=new TextureAtlasData(out.child("scene.normals.atlas"),out,false);
        Assert.assertEquals(2,data.getRegions().size);
        for(var r:data.getRegions()) {
            Assert.assertEquals(7,r.width);Assert.assertEquals(12,r.height);Assert.assertEquals(4,r.offsetX,0);Assert.assertEquals(4,r.offsetY,0);
            Assert.assertEquals(16,r.originalWidth);Assert.assertEquals(24,r.originalHeight);
            BufferedImage page=ImageIO.read(r.page.textureFile.file());
            int expected=r.name.equals("normal__a1")?0x12abef:0xed3412;
            Assert.assertEquals(expected,page.getRGB(r.left+3,r.top+3)&0xffffff);
            Assert.assertEquals(expected,page.getRGB(r.left-1,r.top+3)&0xffffff);
            Assert.assertEquals(expected,page.getRGB(r.left+r.width,r.top+3)&0xffffff);
        }
        Assert.assertFalse(NormalAtlasPackingService.sync(root,colors,db,Set.of("tile0.png","tile1.png")).changed());
    }
    @Test public void declaredMissingNormalFailsButAbsentAssociationDoesNot() throws Exception {
        FileHandle root=new FileHandle(temp.newFolder()),colors=root.child("scene");colors.mkdirs();
        AssetMetaDatabase db=new AssetMetaDatabase();
        TileAssetMeta tile=(TileAssetMeta)db.registerIfAbsent(AssetType.TILE,"tiles/a","a.png",AssetScope.USER);
        NormalAtlasPackingService.sync(root,colors,db,Set.of("a.png"));
        tile.normalSourceRelPath="missing.png";
        try{NormalAtlasPackingService.sync(root,colors,db,Set.of("a.png"));Assert.fail();}
        catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("asset "+tile.id()));}
    }
}
