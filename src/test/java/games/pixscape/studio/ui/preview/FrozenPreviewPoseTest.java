package games.pixscape.studio.ui.preview;

import org.junit.Test;
import static org.junit.Assert.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.lighting.*;

public class FrozenPreviewPoseTest {
    @Test public void forwardDiagnosticSourceExposesValuesWithoutReplacingNormalOutput(){
        String source=ForwardLightingRenderer.fragmentSource("#version 330 core\n");
        assertTrue(source.contains("result=vec4(t.rgb*(ambient+u_color*value),alpha);"));
        assertTrue(source.contains("if(u_pocDiagnostic==5)result=vec4(pocDirect,pocHorizon,pocVisibility,1.)"));
        assertTrue(source.contains("if(u_pocDiagnostic==6)result=vec4(p,1.)"));
        assertTrue(source.contains("if(u_pocDiagnostic==7)result=vec4(exclusionId,flags.y,flags.z,1.)"));
    }
    @Test public void restoresImmutableNormalMappingWithoutGL() throws Exception {
        var type=games.pixscape.runtime.service.NormalMapBinding.class;
        var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        var mapping=constructor.newInstance();
        var handle=type.getDeclaredField("textureHandle");handle.setAccessible(true);handle.set(mapping,27);
        var ux=type.getDeclaredField("ux");ux.setAccessible(true);ux.set(mapping,.375f);
        var json=FrozenPreviewPose.codec();var restored=json.fromJson(type,json.toJson(mapping));
        assertEquals(27,restored.textureHandle);assertEquals(.375f,restored.ux,0);
    }
    @Test public void roundTripRetainsPublishedDataAndActorStateWithoutGL(){
        FrozenPreviewPose p=new FrozenPreviewPose();p.queue=new FrameRenderQueue(4);p.queue.size=1;p.queue.textureHandle[0]=27;p.queue.sourceSlot[0]=940;
        p.queue.colorPacked[0]=Float.intBitsToFloat(0xfeaabbcc);p.queue.shaderParameters[0]=new games.pixscape.runtime.component.ShaderParamsComponent();
        p.layers=new LayerStateSOA(2);p.layers.touchLayerIndex(1);p.layers.enabled[1]=true;
        LightingSurface s=new LightingSurface(new float[48]);s.vertices[2]=158.66797f;s.receiverBlockIds=new int[]{22,22};s.receiverExclusionIds=new int[]{18,18};
        s.curveNormals=new float[]{1,2,3};s.forwardTopology=ForwardTopology.NATIVE;s.actorVolume=new ActorVolumeCaster();s.actorVolume.vertexCount=3;s.actorVolume.vertices[0]=12.5f;
        s.shadowVertices=s.actorVolume.vertices;s.shadowVertexCount=3;
        s.roundedCellKey=115964117016L;p.queue.lighting.tiles.put(940,s);
        LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=s;caster.texture=27;p.queue.lighting.casters.add(caster);p.queue.lighting.staticCasterCount=1;
        LightingFrame.Light light=new LightingFrame.Light();light.x=3178.87207f;light.resolution=512;light.quality=2;p.queue.lighting.lights.add(light);
        p.shaders.put(3,"example");p.properties.put("pixscape.poc.twoDepthSelfShadowExclusion","true");p.lightIntensities.put(90,2.58f);
        String encoded=FrozenPreviewPose.codec().toJson(p);
        FrozenPreviewPose restored=FrozenPreviewPose.codec().fromJson(FrozenPreviewPose.class,encoded);
        assertEquals(1,restored.queue.size);assertEquals(27,restored.queue.textureHandle[0]);assertEquals(0xfeaabbcc,Float.floatToRawIntBits(restored.queue.colorPacked[0]));
        LightingSurface r=restored.queue.lighting.tiles.get(940);assertArrayEquals(s.vertices,r.vertices,0);assertArrayEquals(s.receiverExclusionIds,r.receiverExclusionIds);
        assertEquals(s.roundedCellKey,r.roundedCellKey);assertEquals(6,r.forwardTopology.vertexCount);assertArrayEquals(s.forwardTopology.weights,r.forwardTopology.weights,0);
        assertEquals(3,r.shadowVertexCount);assertEquals(12.5f,r.shadowVertices[0],0);assertEquals(1,restored.layers.maxLayerIndex());
        assertTrue(Float.isNaN(restored.layers.parallaxX[0]));assertEquals(2,restored.queue.lighting.lights.first().quality);
        assertEquals(2.58f,restored.lightIntensities.get(90),0);assertFalse(encoded.contains("\"casterIndex\""));
    }
}
