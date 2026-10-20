package games.pixscape.studio.asset;

import org.junit.Test;
import static org.junit.Assert.*;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.service.AlphaBoundsCatalog;
import games.pixscape.studio.configuration.*;
import games.pixscape.studio.io.StudioFs;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;

public class AlphaBoundsPreparationTest {
 @Test public void sourceReplacementThresholdAndRepackHaveDistinctInvalidation()throws Exception{
  Path p=Files.createTempDirectory("alpha-bounds");FileHandle root=new FileHandle(p.toFile());BufferedImage pixels=new BufferedImage(16,20,BufferedImage.TYPE_INT_ARGB);pixels.setRGB(3,5,0x80ffffff);pixels.setRGB(11,15,0xffffffff);ImageIO.write(pixels,"png",p.resolve("image.png").toFile());
  AssetMetaDatabase db=new AssetMetaDatabase();AssetMeta asset=db.registerIfAbsent(AssetType.IMAGE,"images/example","image.png",AssetMeta.AssetScope.USER);
  assertEquals(1,AlphaBoundsPreparation.prepare(db,root));var a=asset.alphaRegions.first().bounds;assertEquals(3/16f,a.minU,0);assertEquals(12/16f,a.maxU,0);assertEquals(4/20f,a.minV,.000001f);assertEquals(15/20f,a.maxV,0);
  assertEquals(0,AlphaBoundsPreparation.prepare(db,root));root.child("atlases").mkdirs();root.child("atlases/repacked.atlas").writeString("different packing",false);assertEquals(0,AlphaBoundsPreparation.prepare(db,root));
  assertEquals(1,AlphaBoundsPreparation.prepare(db,root,.9f));assertEquals(11/16f,asset.alphaRegions.first().bounds.minU,0);
  pixels.setRGB(0,0,0xffffffff);ImageIO.write(pixels,"png",p.resolve("image.png").toFile());assertEquals(1,AlphaBoundsPreparation.prepare(db,root,.9f));assertEquals(0,asset.alphaRegions.first().bounds.minU,0);
 }
 @Test public void saveReloadExportAndAnimationFramesPreserveBoundsWithoutSourcePixelsInRuntime()throws Exception{
  Path p=Files.createTempDirectory("alpha-export"),export=Files.createTempDirectory("alpha-output");FileHandle root=new FileHandle(p.toFile());root.child("frames").mkdirs();
  BufferedImage empty=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),opaque=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);opaque.setRGB(2,4,0xffffffff);ImageIO.write(empty,"png",root.child("frames/a_0.png").file());ImageIO.write(opaque,"png",root.child("frames/a_1.png").file());
  AssetMetaDatabase db=new AssetMetaDatabase();AnimationAssetMeta asset=(AnimationAssetMeta)db.registerIfAbsent(AssetType.ANIMATION,"animations/a","frames",AssetMeta.AssetScope.USER);asset.frameCount=2;asset.fps=12;asset.currentClip="default";asset.clips.put("default",new AnimationClipMeta(0,1));db.save(root.child(StudioFs.FILE_ASSETS_JSON));
  var loaded=AssetMetaDatabase.load(root.child(StudioFs.FILE_ASSETS_JSON));var regions=loaded.findById(asset.id()).alphaRegions;assertEquals(2,regions.size);assertTrue(regions.first().bounds.empty);assertFalse(regions.get(1).bounds.empty);assertEquals(0,AlphaBoundsPreparation.prepare(loaded,root));
  ProjectConfig cfg=new ProjectConfig();cfg.projectFileName="alpha-test";cfg.createSceneMeta("Main");root.child(StudioFs.DIR_SCENES).mkdirs();root.child(StudioFs.DIR_SCENES+"/scene1.json").writeString("{}",false);RuntimeExport.exportRuntime(cfg,root,new FileHandle(export.toFile()));
  FileHandle runtime=new FileHandle(export.resolve(RuntimeExport.RUNTIME_DIR_NAME).toFile());var catalog=AlphaBoundsCatalog.load(runtime);assertTrue(catalog.get(asset.id(),0,.5f).empty);assertEquals(2/8f,catalog.get(asset.id(),1,.5f).minU,0);assertNull(catalog.get(asset.id(),1,.3f));assertNull(catalog.get(asset.id(),4,.5f));assertFalse(runtime.child("frames").exists());
 }
 @Test public void oldAssetsStayUnknownUntilColdPreparation(){AssetMetaDatabase db=AssetMetaDatabase.fromSnapshotJson("{assets:[{id:1,type:image,logicalPath:images/a,sourceRelPath:a.png}],nextId:2}");assertNull(db.findById(1).alphaRegions);}
}
