package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.binding;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.*;

public class StudioAssetVisualResolverTest {

    @After
    public void clearTextureRegistry() {
        TextureRegistry.clear();
    }

    @Test
    public void staticAssets_followAtlasFirstAndStandaloneTypeRules() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta image = register(db, AssetType.IMAGE, "images/hero", "orig/images/hero.webp");
        AssetMeta tile = register(db, AssetType.TILE, "tiles/ground/0", "orig/tiles/ground/0.png");
        AssetMeta noSource = register(db, AssetType.IMAGE, "images/empty", null);
        AssetMeta missing = register(db, AssetType.IMAGE, "images/missing", "orig/images/missing.png");
        AssetMeta directory = register(
                db,
                AssetType.IMAGE,
                "images/directory",
                "orig/images/folder"
        );
        AssetMeta tileset = register(db, AssetType.TILESET, "tiles/ground", "orig/tiles/ground.png");
        AssetMeta particle = register(db, AssetType.PARTICLE, "effects/fire", "orig/effects/fire.p");

        Texture imageTexture = texture(20, 30);
        Texture tileTexture = texture(16, 24);
        TrackingStandaloneAccess standalone = new TrackingStandaloneAccess();
        standalone.textures.put(image.sourceRelPath(), imageTexture);
        standalone.textures.put(tile.sourceRelPath(), tileTexture);

        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        StudioAssetVisualResolver resolver =
                new StudioAssetVisualResolver(atlas, db::findById, standalone);

        StudioAssetVisual imageStandalone = resolver.resolveFirst(image.id(), null);
        assertNotNull(imageStandalone);
        assertEquals(StudioAssetVisual.Source.STANDALONE, imageStandalone.source());
        assertSame(imageTexture, imageStandalone.texture());
        assertEquals(20, imageStandalone.pixelWidth());
        assertEquals(30, imageStandalone.pixelHeight());
        assertEquals(0f, imageStandalone.u1(), 0f);
        assertEquals(1f, imageStandalone.v2(), 0f);

        StudioAssetVisual tileStandalone = resolver.resolveFirst(tile.id(), " ");
        assertNotNull(tileStandalone);
        assertEquals(StudioAssetVisual.Source.STANDALONE, tileStandalone.source());
        assertSame(tileTexture, tileStandalone.texture());

        assertNull(resolver.resolveFirst(0, "main"));
        assertNull(resolver.resolveFirst(-4, "main"));
        assertNull(resolver.resolveFirst(9999, "main"));
        assertNull(resolver.resolveFirst(noSource.id(), "main"));
        assertNull(resolver.resolveFirst(missing.id(), "main"));
        assertNull(resolver.resolveFirst(directory.id(), "main"));
        assertNull(resolver.resolveFirst(tileset.id(), "main"));
        assertNull(resolver.resolveFirst(particle.id(), "main"));

        Texture atlasTexture = texture(40, 50);
        atlas.publish(
                new TextureAtlas(),
                binding(image.id(), "hero__a" + image.id(), atlasTexture)
        );
        StudioAssetVisual packed = resolver.resolveFirst(image.id(), "main");
        assertNotNull(packed);
        assertEquals(StudioAssetVisual.Source.ATLAS, packed.source());
        assertSame(atlasTexture, packed.texture());
        assertEquals(40, packed.pixelWidth());
        assertEquals(1, atlas.resolveCalls);
    }

    @Test
    public void animationFrames_clampAndReuseOrderedLazyViews() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AnimationAssetMeta animation = (AnimationAssetMeta) register(
                db,
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero"
        );
        Texture first = texture(10, 11);
        Texture second = texture(20, 21);
        TrackingStandaloneAccess standalone = new TrackingStandaloneAccess();
        standalone.frames.put(animation.sourceRelPath(), new String[]{
                "orig/animations/hero/01.png",
                "orig/animations/hero/02.png"
        });
        standalone.textures.put("orig/animations/hero/01.png", first);
        standalone.textures.put("orig/animations/hero/02.png", second);

        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        StudioAssetVisualResolver resolver =
                new StudioAssetVisualResolver(atlas, db::findById, standalone);

        StudioAssetVisual below = resolver.resolveFrame(animation.id(), null, -8);
        StudioAssetVisual above = resolver.resolveFrame(animation.id(), null, 99);
        assertSame(first, below.texture());
        assertEquals(0, below.frameIndex());
        assertSame(second, above.texture());
        assertEquals(1, above.frameIndex());
        assertSame(below, resolver.resolveFirst(animation.id(), null));
        assertSame(above, resolver.resolveFrame(animation.id(), null, 1));
        assertEquals(1, standalone.listAttempts);
        assertEquals(2, standalone.textureAttempts);

        Texture atlasFirst = texture(30, 31);
        Texture atlasSecond = texture(40, 41);
        atlas.publish(
                new TextureAtlas(),
                binding(
                        animation.id(),
                        "hero__a" + animation.id(),
                        atlasFirst,
                        atlasSecond
                )
        );
        StudioAssetVisual packed =
                resolver.resolveFrame(animation.id(), "main", 999);
        assertEquals(StudioAssetVisual.Source.ATLAS, packed.source());
        assertSame(atlasSecond, packed.texture());
        assertEquals(1, packed.frameIndex());
        assertEquals(2, standalone.textureAttempts);
    }

    @Test
    public void animationDirectories_cacheEmptyListingsAndMissingFrames() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AnimationAssetMeta empty = (AnimationAssetMeta) register(
                db,
                AssetType.ANIMATION,
                "animations/empty",
                "orig/animations/empty"
        );
        AnimationAssetMeta missingFrame = (AnimationAssetMeta) register(
                db,
                AssetType.ANIMATION,
                "animations/missing",
                "orig/animations/missing"
        );
        TrackingStandaloneAccess standalone = new TrackingStandaloneAccess();
        standalone.frames.put(
                missingFrame.sourceRelPath(),
                new String[]{"orig/animations/missing/01.png"}
        );
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                db::findById,
                standalone
        );

        assertNull(resolver.resolveFrame(empty.id(), null, 0));
        assertNull(resolver.resolveFrame(empty.id(), null, 0));
        assertNull(resolver.resolveFrame(missingFrame.id(), null, 0));
        assertNull(resolver.resolveFrame(missingFrame.id(), null, 0));

        assertEquals(2, standalone.listAttempts);
        assertEquals(1, standalone.textureAttempts);
    }

    @Test
    public void atlasCache_memoizesHitsMissesAndTracksAtlasIdentity() {
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                atlas,
                id -> null,
                new TrackingStandaloneAccess()
        );

        TextureAtlas firstAtlas = new TextureAtlas();
        Texture firstTexture = texture(8, 9);
        atlas.publish(firstAtlas, binding(7, "tile__a7", firstTexture));
        StudioAssetVisual first = resolver.resolveFirst(7, "main");
        for (int i = 0; i < 10_000; i++) {
            assertSame(first, resolver.resolveFirst(7, "main"));
        }
        assertEquals(1, resolver.atlasColdResolutions());
        assertEquals(1, atlas.resolveCalls);

        for (int i = 0; i < 10_000; i++) {
            assertNull(resolver.resolveFirst(8, "main"));
        }
        assertEquals(2, resolver.atlasColdResolutions());
        assertEquals(2, atlas.resolveCalls);

        Texture secondTexture = texture(18, 19);
        atlas.publish(
                new TextureAtlas(),
                binding(7, "tile__a7", secondTexture)
        );
        StudioAssetVisual replacement = resolver.resolveFirst(7, "main");
        assertNotSame(first, replacement);
        assertSame(secondTexture, replacement.texture());
        assertEquals(3, resolver.atlasColdResolutions());

        resolver.invalidateAtlasTag("main");
        assertNotSame(replacement, resolver.resolveFirst(7, "main"));
        resolver.invalidateAll();
        assertNotNull(resolver.resolveFirst(7, "main"));
        assertEquals(5, resolver.atlasColdResolutions());
    }

    @Test
    public void atlasCache_scalesByDistinctAssetCountInsteadOfResolutionCount() {
        int assetCount = 100;
        Texture sharedTexture = texture(8, 8);
        AtlasAssetBinding[] bindings = new AtlasAssetBinding[assetCount];
        for (int i = 0; i < assetCount; i++) {
            int assetId = i + 1;
            bindings[i] = binding(
                    assetId,
                    "asset-" + assetId + "__a" + assetId,
                    sharedTexture
            );
        }
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("main");
        atlas.publish(new TextureAtlas(), bindings);
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                atlas,
                id -> null,
                new TrackingStandaloneAccess()
        );

        for (int pass = 0; pass < 10_000; pass++) {
            for (int assetId = 1; assetId <= assetCount; assetId++) {
                assertNotNull(resolver.resolveFirst(assetId, "main"));
            }
        }

        assertEquals(assetCount, resolver.atlasColdResolutions());
        assertEquals(assetCount, atlas.resolveCalls);
    }

    @Test
    public void standaloneCache_memoizesHitsMissesAndObservesMetadataChanges() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta image = register(db, AssetType.IMAGE, "images/a", "orig/images/a.png");
        final AssetMeta[] lookup = {image};

        TrackingStandaloneAccess standalone = new TrackingStandaloneAccess();
        Texture firstTexture = texture(12, 13);
        Texture secondTexture = texture(22, 23);
        standalone.textures.put("orig/images/a.png", firstTexture);
        standalone.textures.put("orig/images/b.png", secondTexture);

        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                id -> id == image.id() ? lookup[0] : null,
                standalone
        );

        StudioAssetVisual first = resolver.resolveFirst(image.id(), null);
        for (int i = 0; i < 10_000; i++) {
            assertSame(first, resolver.resolveFirst(image.id(), null));
        }
        assertEquals(1, standalone.textureAttempts);
        assertEquals(1, resolver.standaloneColdResolutions());

        AssetMeta absentSource = new games.pixscape.studio.asset.ImageAssetMeta(
                image.id(),
                "images/missing",
                "orig/images/missing.png",
                AssetMeta.AssetScope.USER
        );
        lookup[0] = absentSource;
        for (int i = 0; i < 10_000; i++) {
            assertNull(resolver.resolveFirst(image.id(), null));
        }
        assertEquals(2, standalone.textureAttempts);

        lookup[0] = image;
        db.updateSourceRelPath(image.id(), "orig/images/b.png");
        StudioAssetVisual changedSource = resolver.resolveFirst(image.id(), null);
        assertSame(secondTexture, changedSource.texture());
        assertEquals(3, standalone.textureAttempts);

        resolver.invalidateStandalone();
        assertNotSame(changedSource, resolver.resolveFirst(image.id(), null));
        resolver.invalidateMetadata();
        assertNotNull(resolver.resolveFirst(image.id(), null));
        resolver.invalidateAll();
        assertNotNull(resolver.resolveFirst(image.id(), null));
        assertEquals(6, standalone.textureAttempts);
    }

    @Test
    public void projectAnimationListing_matchesExistingCaseInsensitivePngOrder()
            throws Exception {
        Path directory = Files.createTempDirectory("visual-animation-order");
        Files.createFile(directory.resolve("frame10.PNG"));
        Files.createFile(directory.resolve("frame2.png"));
        Files.createFile(directory.resolve("frame1.png"));
        Files.createFile(directory.resolve("ignored.jpg"));
        Files.createDirectory(directory.resolve("nested.png"));

        String[] paths = StudioAssetVisualResolver.listProjectPngFramePaths(
                new FileHandle(directory.toFile()),
                "orig/animations/run"
        );

        assertArrayEquals(new String[]{
                "orig/animations/run/frame1.png",
                "orig/animations/run/frame10.PNG",
                "orig/animations/run/frame2.png"
        }, paths);
    }

    private static AssetMeta register(AssetMetaDatabase db,
                                      AssetType type,
                                      String logicalPath,
                                      String sourcePath) {
        return db.registerIfAbsent(
                type,
                logicalPath,
                sourcePath,
                AssetMeta.AssetScope.USER
        );
    }

    private static final class TrackingStandaloneAccess
            implements StudioAssetVisualResolver.StandaloneAssetAccess {
        final ObjectMap<String, Texture> textures = new ObjectMap<>();
        final ObjectMap<String, String[]> frames = new ObjectMap<>();
        int textureAttempts;
        int listAttempts;

        @Override
        public Texture resolveTexture(String projectRelativePath) {
            textureAttempts++;
            return textures.get(projectRelativePath);
        }

        @Override
        public String[] listPngFramePaths(String projectRelativeDirectory) {
            listAttempts++;
            String[] paths = frames.get(projectRelativeDirectory);
            return paths != null ? paths : new String[0];
        }
    }
}
