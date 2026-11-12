package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.loading.WorldConfigFactory;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.*;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.batch.BatchFactoryStudio;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.service.asset.StudioAnimationPreviewRefresher;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.system.StudioRenderSubmitSystem;

/**
 * One saved-asset capture through the editor's normal hierarchy/geometry/sort/submit pipeline.
 * The temporary World never attaches to the canvas and has no input, UI, scripts or simulation.
 * Only the framebuffer survives capture; source textures remain borrowed from Studio's caches.
 * Capture resolution is bounded to 1024 pixels per side. The existing standalone material pass
 * is used, as in an unpacked GameObject editor: no lighting, normal maps or atlas-only shaders.
 */
public final class GameObjectPreviewCapture implements Disposable {
    private static final int MAX_CAPTURE_SIZE = 1024;
    private FrameBuffer framebuffer;
    private TextureRegion region;
    private float width, height;

    public TextureRegion region() {
        return region;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public static GameObjectPreviewCapture capture(GameObjectAsset asset, FileHandle project,
                                                   AssetMetaDatabase database, AtlasRuntimeService atlases, StudioAssetVisualResolver visuals) {
        GameObjectPreviewCapture result = new GameObjectPreviewCapture();
        CaptureState state = new CaptureState();
        World world = null;
        IdentityRegistry identities = new IdentityRegistry();
        games.pixscape.runtime.render.batch.MetricsBatch batch = null;
        try {
            var factory = BatchFactoryStudio.create(atlases);
            batch = factory.batch;
            var camera = new OrthographicCamera(1, 1);
            var dynamic = new DynamicEntityRenderState();
            var layers = new LayerStateSOA();
            layers.setCapacity(1);
            var queue = new FrameRenderQueue(false);
            var tiled = new TiledMapRenderState();
            var stats = new RenderStats();
            var settings = new SceneMeta();
            settings.lightingEnabled = false;
            settings.physicsEnabled = false;
            var submit = new StudioRenderSubmitSystem(layers, queue, camera, batch, stats, new RenderStatsSink(.5f))
                    .setSceneSettings(settings).setRenderDomains(dynamic, tiled);
            world = WorldConfigFactory.buildWorld(camera, dynamic, layers, new DrawList(), queue,
                    new VfxRenderState(), tiled, stats, ShaderRegistry.indexOf(factory.defaultShaderName),
                    atlases, project.child("orig/effects"), () -> submit, settings, 0, null,
                    new AnimationRegistry(), null, null).getWorld();
            // A zero delta alone does not prevent animation initialization/physics side effects.
            world.getSystem(Box2dSyncSystem.class).setEnabled(false);
            world.getSystem(GameObjectPhysicsWritebackSystem.class).setEnabled(false);
            world.getSystem(PhysicsSpatialFootprintSyncSystem.class).setEnabled(false);
            world.getSystem(AnimationSystem.class).setEnabled(false);
            world.getSystem(TiledEntityAnimationSystem.class).setEnabled(false);
            world.getSystem(TiledAnimationSystem.class).setEnabled(false);
            world.getSystem(RenderParticleSyncSystem.class).setEnabled(false);
            identities.bind(world, settings);
            world.getMapper(LayerComponent.class).create(world.create()).layerIndex = 0;
            int root = materialize(asset, project, database, visuals, world, identities);
            world.setDelta(0);
            world.process(); // Resolve hierarchy and geometry; submission is still disabled.
            var composition = world.getSystem(GameObjectCompositionSystem.class).state();
            if (!composition.boundsResolved[root]) return result;
            float minX = composition.minX[root], minY = composition.minY[root];
            float maxX = composition.maxX[root], maxY = composition.maxY[root];
            result.width = maxX - minX + 8;
            result.height = maxY - minY + 8;
            if (!Float.isFinite(result.width) || !Float.isFinite(result.height)
                    || result.width <= 8 || result.height <= 8)
                throw new IllegalArgumentException("Invalid composition bounds");
            float scale = Math.min(1f, MAX_CAPTURE_SIZE / Math.max(result.width, result.height));
            int pixelWidth = Math.min(MAX_CAPTURE_SIZE, Math.max(1, (int) Math.ceil(result.width * scale)));
            int pixelHeight = Math.min(MAX_CAPTURE_SIZE, Math.max(1, (int) Math.ceil(result.height * scale)));
            // Keep capture pixels square, including rounding at the framebuffer boundary.
            result.width = pixelWidth / scale;
            result.height = pixelHeight / scale;
            camera.viewportWidth = result.width;
            camera.viewportHeight = result.height;
            camera.position.set(minX + (maxX - minX) / 2, minY + (maxY - minY) / 2, 0);
            camera.update();
            result.framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, pixelWidth, pixelHeight, false);
            result.framebuffer.begin();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glClearColor(.12f, .13f, .15f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            submit.beginFrameSubmission();
            try {
                world.process();
            } finally {
                submit.endFrameSubmission();
            }
            result.framebuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            result.region = new TextureRegion(result.framebuffer.getColorBufferTexture());
            result.region.flip(false, true);
            return result;
        } catch (RuntimeException | Error failure) {
            result.dispose();
            throw failure;
        } finally {
            try {
                try {
                    identities.bind(null, null);
                    if (world != null) world.dispose();
                } finally {
                    if (batch != null) batch.close();
                }
            } catch (RuntimeException | Error failure) {
                result.dispose();
                throw failure;
            } finally {
                state.restore();
            }
        }
    }

    private static int materialize(GameObjectAsset asset, FileHandle project, AssetMetaDatabase database,
                                   StudioAssetVisualResolver visuals, World world, IdentityRegistry identities) {
        IntIntMap stableIds = new IntIntMap();
        for (var data : asset.entities) stableIds.put(data.sourceEntityId, identities.allocateStableId());
        var mapper = new GameObjectEntityDataMapper();
        IntSet validated = new IntSet();
        int root = -1;
        for (var data : asset.entities) {
            var snapshot = mapper.toGraphEntry(world, data).initializer().toSnapshotData(data.sourceEntityId);
            // These are disposable copies. Physics, joints and custom behavior never enter this World.
            snapshot.hasPhysicsBody = false;
            snapshot.shapes.clear();
            var generic = new GenericEntityInitializer(world).applySnapshotData(snapshot);
            generic.setIdentityStableId(stableIds.get(data.sourceEntityId, -1));
            generic.overrideLayerIndex(0);
            int entity = world.create();
            new GameObjectAssetEntityInitializer(world, generic, data,
                    stableIds.get(data.parentSourceEntityId, -1), "", null).init(entity);
            if (data.sourceEntityId == asset.rootSourceEntityId) root = entity;
            if (data.assetRef == null) continue;
            int id = data.assetRef.assetId;
            var meta = database.findById(id);
            if (validated.add(id)) {
                if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()
                        || !project.child(meta.sourceRelPath()).exists())
                    throw new IllegalArgumentException("Composition resource is missing");
                FileHandle source = project.child(meta.sourceRelPath());
                if (!source.isDirectory()) validatePixels(source);
            }
            // Saved animation frame, without advancing its clock or changing the shared texture.
            AnimationClipMeta clip = data.animation != null && meta instanceof AnimationAssetMeta animation
                    && animation.clips != null ? animation.clips.get(data.animation.currentClip) : null;
            int frame = data.animation != null && data.animation.frame >= 0 ? data.animation.frame : clip != null ? clip.start : 0;
            var visual = visuals.resolveFrame(id, data.assetRef.atlasTag, frame);
            if (visual == null || visual.texture().getTextureObjectHandle() == 0)
                throw new IllegalArgumentException("Composition texture is unavailable");
            if (meta instanceof AnimationAssetMeta && visual.texture().getTextureData()
                    instanceof com.badlogic.gdx.graphics.glutils.FileTextureData textureData)
                validatePixels(textureData.getFileHandle());
            var region = world.getMapper(TextureRegionComponent.class).create(entity);
            RenderRebindHelper.applyVisual(region, world.getMapper(RenderMaterialComponent.class).create(entity),
                    visual, data.assetRef.atlasTag);
            if (clip != null)
                StudioAnimationPreviewRefresher.applyFrame(world, entity, clip, visual, null, data.assetRef.atlasTag);
        }
        return root;
    }

    private static void validatePixels(FileHandle source) {
        // Caches can outlive a removed or invalid source. Inspection must not mask that failure.
        Pixmap pixels = new Pixmap(source);
        pixels.dispose();
    }

    @Override
    public void dispose() {
        region = null;
        if (framebuffer != null) framebuffer.dispose();
        framebuffer = null;
    }

    /**
     * Captures run outside Scene2D drawing, but may be nested in the canvas refresh boundary.
     */
    private static final class CaptureState {
        private final java.nio.IntBuffer integers = BufferUtils.newIntBuffer(4);
        private final java.nio.FloatBuffer clear = BufferUtils.newFloatBuffer(4);
        private final int framebuffer = read(GL20.GL_FRAMEBUFFER_BINDING);
        private final int[] viewport = viewport();
        private final int[] colorMask = readFour(GL20.GL_COLOR_WRITEMASK);
        private final boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        private final int program = read(GL20.GL_CURRENT_PROGRAM);
        private final int activeTexture = read(GL20.GL_ACTIVE_TEXTURE);
        private final int texture = read(GL20.GL_TEXTURE_BINDING_2D);
        private final int vertexArray = read(com.badlogic.gdx.graphics.GL30.GL_VERTEX_ARRAY_BINDING);
        private final int arrayBuffer = read(GL20.GL_ARRAY_BUFFER_BINDING);
        private final int elementBuffer = read(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        private final int texture0;
        private final DirectWorldPass pass = new DirectWorldPass();

        CaptureState() {
            Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, clear);
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            texture0 = read(GL20.GL_TEXTURE_BINDING_2D);
            Gdx.gl.glActiveTexture(activeTexture);
            pass.begin();
            Gdx.gl.glColorMask(true, true, true, true);
        }

        int read(int name) {
            integers.clear();
            Gdx.gl.glGetIntegerv(name, integers);
            return integers.get(0);
        }

        int[] viewport() {
            return readFour(GL20.GL_VIEWPORT);
        }

        int[] readFour(int name) {
            integers.clear();
            Gdx.gl.glGetIntegerv(name, integers);
            return new int[]{integers.get(0), integers.get(1), integers.get(2), integers.get(3)};
        }

        void restore() {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
            Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
            Gdx.gl.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            Gdx.gl.glUseProgram(program);
            Gdx.gl30.glBindVertexArray(vertexArray);
            Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
            Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture0);
            Gdx.gl.glActiveTexture(activeTexture);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
            pass.end();
        }
    }
}
