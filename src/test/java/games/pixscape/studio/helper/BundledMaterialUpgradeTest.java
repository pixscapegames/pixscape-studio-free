package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BundledMaterialUpgradeTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Files previous;
    @Before public void files() { previous=Gdx.files;Gdx.files=new Lwjgl3Files(); }
    @After public void restore() { Gdx.files=previous; }
    private FileHandle project() throws Exception {
        FileHandle root=new FileHandle(temp.newFolder());
        copy(new FileHandle("src/test/resources/supplied-shader-before"),root);
        root.child("shader-indices.json").writeString("{nextIndex:30,names:{glow_pulse:9,wave_uv:22,outline_soft:27}}",false);
        return root;
    }
    @Test public void knownCopiesUpgradeOnceWithoutChangingIdentityOrRoles() throws Exception {
        FileHandle root=project();String ids=root.child("shader-indices.json").readString();
        BundledMaterialUpgrade.upgrade(root);
        for(String name:new String[]{"glow_pulse","wave_uv","outline_soft"}) {
            FileHandle dir=root.child("examples/material/"+name);
            assertTrue(dir.child("surface.glsl").exists());
            assertEquals("surface-v1",new JsonReader().parse(dir.child("shader.json")).getString("contract"));
            assertFalse(root.child("examples/material/desktop-gl30/"+name+".frag").exists());
            assertFalse(root.child("examples/material/es3-webgl2/"+name+".frag").exists());
        }
        assertTrue(new JsonReader().parse(root.child("examples/params.json")).has("camera_postfx"));
        assertFalse(root.child("examples/surface/lit-pulse").exists());
        assertTrue(root.child("examples/material/lit-pulse/surface.glsl").exists());
        String upgraded=root.child("examples/params.json").readString();
        BundledMaterialUpgrade.upgrade(root);
        assertEquals(upgraded,root.child("examples/params.json").readString());assertEquals(ids,root.child("shader-indices.json").readString());
    }
    @Test public void changedCodeDependenciesAndDefaultsArePreserved() throws Exception {
        for(String changed:new String[]{"examples/material/es3-webgl2/wave_uv.frag","includes/pixscape_common.glsl","examples/params.json"}) {
            FileHandle root=project();FileHandle file=root.child(changed);
            String original=file.readString("UTF-8");String edited=changed.endsWith("json")?original.replace("0.02","0.07"):original+"\n// authored change\n";
            file.writeString(edited,false,"UTF-8");
            BundledMaterialUpgrade.upgrade(root);
            assertTrue(root.child("examples/material/desktop-gl30/wave_uv.frag").exists());
            assertFalse(root.child("examples/material/wave_uv/surface.glsl").exists());
            if(!changed.endsWith("json"))assertEquals(edited,file.readString("UTF-8"));
            else assertEquals(.07f,new JsonReader().parse(file).get("wave_uv").getFloat("u_amplitude"),0f);
        }
    }
    @Test public void exactStructuredCopyKeepsItsAuthoredNameAndMetadata() throws Exception {
        FileHandle root=project(),dir=root.child("custom/material/my_wave");
        JsonValue metadata=new JsonReader().parse(Gdx.files.internal("shaders/examples/material/wave_uv/shader.json"));
        metadata.remove("contract");metadata.remove("lighting");metadata.remove("name");metadata.addChild("name",new JsonValue("my_wave"));metadata.addChild("note",new JsonValue("keep"));
        dir.child("shader.json").writeString(metadata.toJson(JsonWriter.OutputType.json),false);
        for(String v:new String[]{"desktop-gl30","es3-webgl2"}) {
            root.child("examples/material/"+v+"/wave_uv.frag").copyTo(dir.child(v+".frag"));
            root.child("core/"+v+"/texture-array.vert").copyTo(dir.child(v+".vert"));
        }
        BundledMaterialUpgrade.upgrade(root);
        JsonValue result=new JsonReader().parse(dir.child("shader.json"));
        assertEquals("my_wave",result.getString("name"));assertEquals("keep",result.getString("note"));assertEquals("LIT",result.getString("lighting"));
        assertTrue(dir.child("surface.glsl").exists());assertFalse(dir.child("desktop-gl30.frag").exists());
    }
    @Test public void authoredSurfaceAndNonMaterialRoleAreNotConverted() throws Exception {
        FileHandle root=project(),surface=root.child("examples/surface/lit-pulse/surface.glsl");
        String edited=surface.readString("UTF-8")+"\n// user source\n";surface.writeString(edited,false,"UTF-8");
        FileHandle dir=root.child("custom/material/special_fx");
        JsonValue metadata=new JsonReader().parse(Gdx.files.internal("shaders/examples/material/wave_uv/shader.json"));
        metadata.remove("contract");metadata.remove("lighting");metadata.remove("kind");metadata.addChild("kind",new JsonValue("FX"));
        dir.child("shader.json").writeString(metadata.toJson(JsonWriter.OutputType.json),false);
        for(String v:new String[]{"desktop-gl30","es3-webgl2"}) {
            root.child("examples/material/"+v+"/wave_uv.frag").copyTo(dir.child(v+".frag"));
            root.child("core/"+v+"/texture-array.vert").copyTo(dir.child(v+".vert"));
        }
        BundledMaterialUpgrade.upgrade(root);
        assertEquals(edited,surface.readString("UTF-8"));assertFalse(root.child("examples/material/lit-pulse").exists());
        assertTrue(dir.child("desktop-gl30.frag").exists());assertFalse(dir.child("surface.glsl").exists());
    }
    private static void copy(FileHandle from,FileHandle to) {
        if(from.isDirectory()){to.mkdirs();for(FileHandle child:from.list())copy(child,to.child(child.name()));}
        else from.copyTo(to);
    }
}
