package games.pixscape.studio.service;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.studio.service.atlas.AtlasStudioService;

public final class SnapshotBuilder {

    private final AtlasStudioService atlasStudioService;

    public static final class BuildResult {
        public final AtlasRuntimeService.TextureArrayBundle bundle;
        public final int packedCount;
        public final int totalLayers;
        public final int whiteLayer;
        public final long internalTextureInitNs;
        public final long atlasLookupNs;
        public final long pageTextureDiscoveryNs;
        public final long textureArrayBuildNs;
        public final long whiteLayerLookupNs;

        BuildResult(AtlasRuntimeService.TextureArrayBundle bundle,
                    int packedCount,
                    int totalLayers,
                    int whiteLayer,
                    long internalTextureInitNs,
                    long atlasLookupNs,
                    long pageTextureDiscoveryNs,
                    long textureArrayBuildNs,
                    long whiteLayerLookupNs) {
            this.bundle = bundle;
            this.packedCount = packedCount;
            this.totalLayers = totalLayers;
            this.whiteLayer = whiteLayer;
            this.internalTextureInitNs = internalTextureInitNs;
            this.atlasLookupNs = atlasLookupNs;
            this.pageTextureDiscoveryNs = pageTextureDiscoveryNs;
            this.textureArrayBuildNs = textureArrayBuildNs;
            this.whiteLayerLookupNs = whiteLayerLookupNs;
        }
    }

    public SnapshotBuilder(AtlasStudioService atlasStudioService) {
        this.atlasStudioService = atlasStudioService;
    }

    public BuildResult buildSnapshot(String sceneTag) {
        return buildSnapshot(sceneTag, false);
    }

    public BuildResult buildSnapshot(String sceneTag, boolean diagnosticsEnabled) {
        long phaseStart = diagnosticsEnabled ? System.nanoTime() : 0L;
        InternalTextures.initIfNeeded();
        long internalTextureInitNs = diagnosticsEnabled ? System.nanoTime() - phaseStart : 0L;

        phaseStart = diagnosticsEnabled ? System.nanoTime() : 0L;
        TextureAtlas atlas = (sceneTag != null && !sceneTag.isEmpty())
                ? atlasStudioService.getAtlas(sceneTag)
                : null;
        long atlasLookupNs = diagnosticsEnabled ? System.nanoTime() - phaseStart : 0L;

        Array<Texture> atlasPages = new Array<>();
        int packedCount = 0;

        phaseStart = diagnosticsEnabled ? System.nanoTime() : 0L;
        if (atlas != null) {
            atlasPages = AtlasRuntimeService.getPageTextures(atlas);
            packedCount = atlasPages.size;
        }
        long pageTextureDiscoveryNs = diagnosticsEnabled ? System.nanoTime() - phaseStart : 0L;

        phaseStart = diagnosticsEnabled ? System.nanoTime() : 0L;
        AtlasRuntimeService.TextureArrayBundle bundle =
                AtlasRuntimeService.buildTextureArrayFromTextures(atlasPages);
        long textureArrayBuildNs = diagnosticsEnabled ? System.nanoTime() - phaseStart : 0L;

        phaseStart = diagnosticsEnabled ? System.nanoTime() : 0L;
        int whiteLayer = bundle.handle2layer.get(InternalTextures.whiteHandle(), -1);
        long whiteLayerLookupNs = diagnosticsEnabled ? System.nanoTime() - phaseStart : 0L;

        return new BuildResult(
                bundle,
                packedCount,
                bundle.handle2layer.size,
                whiteLayer,
                internalTextureInitNs,
                atlasLookupNs,
                pageTextureDiscoveryNs,
                textureArrayBuildNs,
                whiteLayerLookupNs
        );
    }
}
