package games.pixscape.studio.system;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import games.pixscape.studio.service.tiled.TiledPreviewService;
import org.junit.Test;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.binding;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.*;

public class TiledGhostPreviewSystemVisualTest {

    @Test
    public void canonicalVisualUvsMapToSpriteBatchVertexOrderWithoutChangingTint() {
        float[] quad = new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f};
        float[] vertices = new float[20];
        float color = Color.toFloatBits(0.2f, 0.4f, 0.6f, 0.55f);

        TiledGhostPreviewSystem.buildVertices(
                vertices,
                quad,
                0.1f, 0.8f,
                0.1f, 0.2f,
                0.7f, 0.2f,
                0.7f, 0.8f,
                color
        );

        assertVertex(vertices, 0, 1f, 2f, color, 0.1f, 0.8f);
        assertVertex(vertices, 5, 3f, 4f, color, 0.1f, 0.2f);
        assertVertex(vertices, 10, 5f, 6f, color, 0.7f, 0.2f);
        assertVertex(vertices, 15, 7f, 8f, color, 0.7f, 0.8f);
    }

    @Test
    public void ghostConsumesAtlasStandaloneAndNegativeResolverResults()
            throws Exception {
        int assetId = 7;
        TileAssetMeta tile = new TileAssetMeta(
                assetId,
                "tiles/ground/0",
                "orig/tiles/ground/0.png",
                AssetMeta.AssetScope.USER
        );
        Texture standaloneTexture = texture(24, 32);
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                atlas,
                id -> id == assetId ? tile : null,
                new StudioAssetVisualResolver.StandaloneAssetAccess() {
                    @Override
                    public Texture resolveTexture(String projectRelativePath) {
                        return standaloneTexture;
                    }

                    @Override
                    public String[] listPngFramePaths(
                            String projectRelativeDirectory) {
                        return new String[0];
                    }
                }
        );
        TiledGhostPreviewSystem ghost = new TiledGhostPreviewSystem(
                new StudioDrawContext(null, null, null),
                resolver,
                new TiledPreviewService(),
                id -> tile,
                null
        );

        StudioAssetVisual standalone = ghost.resolveVisual(assetId, "main");
        assertEquals(StudioAssetVisual.Source.STANDALONE, standalone.source());
        assertEquals(24, standalone.pixelWidth());
        assertEquals(32, standalone.pixelHeight());
        assertSame(standalone, ghost.resolveVisual(assetId, "main"));
        assertNull(ghost.resolveVisual(999, "main"));
        assertNull(ghost.resolveVisual(999, "main"));

        Texture atlasTexture = texture(40, 48);
        atlas.publish(
                new TextureAtlas(),
                binding(assetId, "ground__a" + assetId, atlasTexture)
        );
        StudioAssetVisual packed = ghost.resolveVisual(assetId, "main");
        assertEquals(StudioAssetVisual.Source.ATLAS, packed.source());
        assertSame(atlasTexture, packed.texture());
        assertEquals(40, packed.pixelWidth());
        assertEquals(48, packed.pixelHeight());
        assertSame(packed, ghost.resolveVisual(assetId, "main"));
    }

    private static void assertVertex(float[] vertices,
                                     int offset,
                                     float x,
                                     float y,
                                     float color,
                                     float u,
                                     float v) {
        assertEquals(x, vertices[offset], 0f);
        assertEquals(y, vertices[offset + 1], 0f);
        assertEquals(color, vertices[offset + 2], 0f);
        assertEquals(u, vertices[offset + 3], 0f);
        assertEquals(v, vertices[offset + 4], 0f);
    }
}
