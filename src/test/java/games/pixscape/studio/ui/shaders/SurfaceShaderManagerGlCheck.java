package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.SurfaceShader;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.ui.widget.ScrollableCodeEditor;
import org.junit.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
/** Actual dialog listeners and GPU compilation, followed by Studio's Runtime export. */
public final class SurfaceShaderManagerGlCheck {
 public static void verify(){Throwable[] failure={null};Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setInitialVisible(false);c.disableAudio(true);c.setWindowedMode(1100,900);c.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
 org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
 new Lwjgl3Application(new ApplicationAdapter(){public void create(){try{run();}catch(Throwable t){failure[0]=t;}finally{Gdx.app.exit();}}},c);if(failure[0]!=null)throw new AssertionError("Surface Shader Manager workflow",failure[0]);}
 private static void run()throws Exception{
  FileHandle root=new FileHandle(Files.createTempDirectory("surface-studio-").toFile());ProjectConfig previous=ProjectConfig.getInstance();Stage stage=null;
  try{
   FileHandle fixture=new FileHandle("src/test/resources/surface-project");Assert.assertTrue(fixture.child("project.json").exists());
   copyTree(fixture.child("scenes"),root.child("scenes"));copyTree(fixture.child("atlases"),root.child("atlases"));copyTree(fixture.child("shaders"),root.child("orig/shaders"));
   ProjectConfig cfg=new ProjectConfig();cfg.projectFileName="surface-studio";cfg.projectTitle="Surface integration";cfg.projectDirectoryPath=root.path();
   Json json=new Json();json.setIgnoreUnknownFields(true);JsonValue metadata=new JsonReader().parse(fixture.child("project.json"));
   for(JsonValue entry=metadata.get("scenes").child;entry!=null;entry=entry.next)cfg.getScenesMap().put(entry.name,json.readValue(SceneMeta.class,entry));
   cfg.setCurrentSceneByName("off");ProjectConfig.setInstance(cfg);ShaderRegistry.initDefaults(root,"orig/shaders");
   VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));stage=new Stage(new ScreenViewport());
   ShaderManagerDialog dialog=new ShaderManagerDialog(null);stage.addActor(dialog);dialog.setSize(1040,840);dialog.setPosition(30,30);
   call(dialog,"startNewShader");Group prompt=(Group)stage.getActors().peek();find(prompt,VisTextField.class).setText("ui_lit");((VisCheckBox)prompt.findActor("shader-lit-choice")).setChecked(true);
   capture(stage,"shader-create");button(prompt,"Create").fire(new ChangeEvent());Assert.assertNotNull(ShaderRegistry.getSurface(ShaderRegistry.indexOf("ui_lit")));
   ScrollableCodeEditor editor=(ScrollableCodeEditor)field(dialog,"surfaceEditor");editor.setText("return vec4(base.rgb * 0.65, base.a);");
   call(dialog,"doSaveAndRegister");closeMessages(stage,dialog);FileHandle body=root.child("orig/shaders/custom/material/ui_lit/surface.glsl");String good=body.readString("UTF-8");Object working=ShaderRegistry.get("ui_lit");
   editor.setText("return unknown_symbol;");call(dialog,"doSaveAndRegister");Assert.assertEquals(good,body.readString("UTF-8"));Assert.assertSame(working,ShaderRegistry.get("ui_lit"));closeMessages(stage,dialog);editor.setText(good);
   call(dialog,"doDuplicateShader");prompt=(Group)stage.getActors().peek();find(prompt,VisTextField.class).setText("ui_unlit_copy");button(prompt,"OK").fire(new ChangeEvent());Assert.assertEquals(SurfaceShader.Lighting.LIT,ShaderRegistry.getSurface(ShaderRegistry.indexOf("ui_unlit_copy")).lighting);
   int identity=ShaderRegistry.indexOf("ui_unlit_copy");call(dialog,"doRenameShader");prompt=(Group)stage.getActors().peek();find(prompt,VisTextField.class).setText("ui_lit_renamed");button(prompt,"OK").fire(new ChangeEvent());Assert.assertEquals(identity,ShaderRegistry.indexOf("ui_lit_renamed"));
   call(dialog,"startNewShader");prompt=(Group)stage.getActors().peek();find(prompt,VisTextField.class).setText("ui_unlit");button(prompt,"Create").fire(new ChangeEvent());Assert.assertEquals(SurfaceShader.Lighting.UNLIT,ShaderRegistry.getSurface(ShaderRegistry.indexOf("ui_unlit")).lighting);
   @SuppressWarnings("unchecked") VisSelectBox<String> select=(VisSelectBox<String>)field(dialog,"shaderBox");select.setSelected("ui_lit_renamed");Assert.assertEquals(good,editor.getText());capture(stage,"shader-manager");select.setSelected("ui_unlit");Assert.assertTrue(editor.getText().contains("return base"));select.setSelected("ui_lit");
   prepareSuppliedScene(root);
   FileHandle export=new FileHandle("build/surface-validation/studio-export");RuntimeExport.exportRuntime(cfg,root,export);
   Assert.assertTrue(export.child("pixscape-project/shaders/custom/material/ui_lit/surface.glsl").exists());
   Assert.assertFalse(new JsonReader().parse(export.child("pixscape-project/project.json")).get("scenes").get("off").getBoolean("lightingEnabled"));
   PixscapeEngine engine=new PixscapeEngine();try{engine.loadProject(export);engine.resize(320,240);var load=engine.beginLoadScene("off");while(!load.isReady()&&!load.isFailed())load.update();if(load.isFailed())throw new IllegalStateException(load.failure());Assert.assertFalse(engine.isLightingEnabled());engine.update(0);engine.render();
    Assert.assertEquals("glow_pulse",engine.api().entities().requireEntityId(engine.findEntityByStableId(2)).shader().shader());
    Assert.assertEquals("wave_uv",engine.api().entities().requireEntityId(engine.findEntityByStableId(3)).shader().shader());
    Assert.assertEquals(.12f,engine.api().entities().requireEntityId(engine.findEntityByStableId(3)).shader().getFloat("u_amplitude",0),0f);
    Assert.assertEquals("outline_soft",engine.api().entities().requireEntityId(engine.findEntityByStableId(4)).shader().shader());
    Assert.assertEquals(SurfaceShader.Lighting.UNLIT,ShaderRegistry.getSurface(ShaderRegistry.indexOf("ui_unlit")).lighting);}finally{engine.dispose();}
   System.out.println("SURFACE_STUDIO_PASS create Lit/Unlit, edit/save, failed compile rollback, duplicate, rename stable identity, selection, export and Runtime load");
  }finally{if(stage!=null)stage.dispose();if(VisUI.isLoaded())VisUI.dispose();ShaderRegistry.disposeAll();games.pixscape.runtime.render.InternalTextures.dispose();games.pixscape.runtime.service.TextureRegistry.clear();ProjectConfig.setInstance(previous);games.pixscape.studio.event.EventFlow.i().discardPending();root.deleteDirectory();}
 }
 private static void prepareSuppliedScene(FileHandle root) {
  String[] names={"glow_pulse","wave_uv","outline_soft","outline_soft"};
  for(FileHandle file:root.child("scenes").list()) {
   JsonValue json=new JsonReader().parse(file);
   for(JsonValue entity=json.get("entities").child;entity!=null;entity=entity.next) {
    JsonValue components=entity.get("components"),identity=components.get("PixscapeIdentityComponent");
    int stable=identity==null?-1:identity.getInt("stableId");if(stable<2||stable>5)continue;
    JsonValue material=components.get("RenderMaterialComponent");material.remove("shaderIdx");material.addChild("shaderIdx",new JsonValue(ShaderRegistry.indexOf(names[stable-2])));
    material.remove("blendModeId");material.addChild("blendModeId",new JsonValue(1));
    components.remove("ShaderParamsComponent");JsonValue parameters=new JsonValue(JsonValue.ValueType.object),values=new JsonValue(JsonValue.ValueType.array);parameters.addChild("floats",values);
    String param=stable==2?"u_boost":stable==3?"u_amplitude":"u_outlineSize";JsonValue value=new JsonValue(JsonValue.ValueType.object);value.addChild("name",new JsonValue(param));value.addChild("value",new JsonValue(stable==2?.3f:stable==3?.12f:.08f));values.addChild(value);components.addChild("ShaderParamsComponent",parameters);
   }
   file.writeString(json.toJson(JsonWriter.OutputType.json),false,"UTF-8");
  }
  Pixmap image=new Pixmap(2048,2048,Pixmap.Format.RGBA8888);image.setBlending(Pixmap.Blending.None);image.setColor(1,0,1,1);image.fill();
  for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
   float radius=(x-32)*(x-32)+(y-32)*(y-32),alpha=radius<400?1:radius<529?.4f:0;
   image.drawPixel(x,y,Color.rgba8888(.2f+.6f*x/64f,.2f+.6f*y/64f,(x/8)%2==0?.15f:.65f,alpha));
  }
  for(String name:new String[]{"white","off","legacy","forward"})PixmapIO.writePNG(root.child("atlases/"+name+".png"),image);
  image.dispose();
 }
 private static void copyTree(FileHandle source,FileHandle target){if(source.isDirectory()){target.mkdirs();for(FileHandle child:source.list())copyTree(child,target.child(child.name()));}else source.copyTo(target);}
 private static void capture(Stage s,String name){Gdx.gl.glViewport(0,0,1100,900);Gdx.gl.glClearColor(.08f,.08f,.08f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);s.act(0);s.draw();Pixmap p=Pixmap.createFromFrameBuffer(0,0,1100,900);PixmapIO.writePNG(new FileHandle("build/surface-validation/"+name+".png"),p,-1,true);p.dispose();}
 private static void closeMessages(Stage stage,Actor keep){for(Actor a:stage.getActors().toArray())if(a!=keep)a.remove();}
 private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private static void call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);try{m.invoke(o);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}}
 private static <T> T find(Group g,Class<T> type){for(Actor a:g.getChildren()){if(type.isInstance(a))return type.cast(a);if(a instanceof Group){T hit=find((Group)a,type);if(hit!=null)return hit;}}return null;}
 private static VisTextButton button(Group g,String text){for(Actor a:g.getChildren()){if(a instanceof VisTextButton && ((VisTextButton)a).getText().toString().equals(text))return (VisTextButton)a;if(a instanceof Group){VisTextButton hit=button((Group)a,text);if(hit!=null)return hit;}}return null;}
}
