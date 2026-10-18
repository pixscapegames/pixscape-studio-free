package games.pixscape.studio.service;

import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.render.lighting.ActorShadowProfile;

/** Non-persistent editor reference until the first explicit calibration edit. */
public final class ActorShadowEditingSupport {
    private ActorShadowEditingSupport() { }

    public static ActorShadowProfile resolve(SurfaceLightingComponent surface,int asset,String clip,ActorShadowProfile fallback) {
        if(surface==null || !surface.shadowCaster || surface.description!=null)return null;
        ActorShadowProfile authored=surface.shadowProfile(asset,clip);
        if(authored!=null)return authored;
        // Match the existing default sheet's receiver anchor; never author a profile on selection.
        fallback.leftU=0;fallback.rightU=1;fallback.leftV=fallback.rightV=surface.anchorV;fallback.thickness=0;
        return fallback;
    }
}
