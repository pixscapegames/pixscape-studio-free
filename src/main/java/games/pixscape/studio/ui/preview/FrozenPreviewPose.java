package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.NormalMapBinding;
import java.lang.reflect.Constructor;

/** Versioned draw-ready state. No ECS simulation or current project is needed for replay. */
public final class FrozenPreviewPose {
    public int version=1,width,height,logicalWidth,logicalHeight,samples;
    public String arrayMin="Nearest",arrayMag="Nearest";
    public boolean forward,arrayBatch,roundedLighting=true;
    public String glDriver,javaHome,javaVendor;
    public String scene,project,javaVersion,runtime,session;
    public long previewPid;
    public String previewStarted;
    public Vector3 cameraPosition=new Vector3(),cameraDirection=new Vector3(),cameraUp=new Vector3();
    public float viewportWidth,viewportHeight,zoom,near,far,time;
    public float[] ambient=new float[3];
    public FrameRenderQueue queue;
    public LayerStateSOA layers;
    public IntMap<String> shaders=new IntMap<>();
    public IntMap<Float> lightIntensities=new IntMap<>();
    public Array<TextureInfo> textures=new Array<>();
    public ObjectMap<String,String> properties=new ObjectMap<>();
    public static final class TextureInfo {
        public int handle,width,height;
        public String file,min,mag,u,v;
    }
    public static Json codec(){
        Json json=new Json(){
            @Override protected Object newInstance(Class type){
                try {
                    if(type==LightingSurface.class)return new LightingSurface(new float[0]);
                    if(type==ForwardTopology.class){Constructor<?> c=type.getDeclaredConstructor(int[].class,float[].class);c.setAccessible(true);return c.newInstance(new int[0],new float[0]);}
                    if(type==NormalMapBinding.class){Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
                }catch(ReflectiveOperationException e){throw new SerializationException("Cannot restore frozen geometry",e);}
                return super.newInstance(type);
            }
        };
        json.setOutputType(JsonWriter.OutputType.json);json.setUsePrototypes(false);
        json.setDeprecated(LightingFrame.class,"casterIndex",true);
        json.setDeprecated(LightingFrame.class,"timer",true);json.setIgnoreDeprecated(true);
        return json;
    }
}
