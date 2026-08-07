package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.FileTextureArrayData;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;

/** CPU-only, generation-scoped texture-array input prepared by the atlas worker. */
public final class PreparedGpuSnapshot implements AutoCloseable {

    private final String sceneTag;
    private final long generation;
    private final int layerSize;
    private final Array<String> pageNames;
    private final Array<Pixmap> layers;
    private final long preparationNs;
    private final long byteSize;
    private boolean disposed;

    private PreparedGpuSnapshot(String sceneTag,
                                long generation,
                                int layerSize,
                                Array<String> pageNames,
                                Array<Pixmap> layers,
                                long preparationNs) {
        this.sceneTag = sceneTag;
        this.generation = generation;
        this.layerSize = layerSize;
        this.pageNames = pageNames;
        this.layers = layers;
        this.preparationNs = preparationNs;
        this.byteSize = (long) layers.size * layerSize * layerSize * 4L;
    }

    public static PreparedGpuSnapshot prepare(String sceneTag,
                                              long generation,
                                              FileHandle atlasFile) {
        if (atlasFile == null) throw new IllegalArgumentException("Atlas file must not be null.");
        TextureAtlas.TextureAtlasData data = new TextureAtlas.TextureAtlasData(
                atlasFile,
                atlasFile.parent(),
                false
        );
        Array<FileHandle> pageFiles = new Array<>();
        Array<String> pageNames = new Array<>();
        ObjectSet<TextureAtlas.TextureAtlasData.Page> seenPages = new ObjectSet<>();
        for (TextureAtlas.TextureAtlasData.Region region : data.getRegions()) {
            TextureAtlas.TextureAtlasData.Page page = region.page;
            if (page != null && seenPages.add(page)) {
                pageFiles.add(page.textureFile);
                pageNames.add(page.textureFile.name());
            }
        }
        return prepareFromPages(
                sceneTag,
                generation,
                pageFiles,
                pageNames,
                AtlasRuntimeService.fixedLayerSize()
        );
    }

    static PreparedGpuSnapshot prepareFromPages(String sceneTag,
                                                long generation,
                                                Array<FileHandle> pageFiles,
                                                Array<String> pageNames,
                                                int layerSize) {
        if (sceneTag == null || sceneTag.isBlank()) {
            throw new IllegalArgumentException("Scene tag must not be blank.");
        }
        if (generation <= 0L) throw new IllegalArgumentException("Generation must be positive.");
        if (layerSize <= 0) throw new IllegalArgumentException("Layer size must be positive.");
        if (pageFiles == null || pageNames == null || pageFiles.size != pageNames.size) {
            throw new IllegalArgumentException("Page files and names must have matching sizes.");
        }

        long started = System.nanoTime();
        Array<Pixmap> layers = new Array<>(1 + pageFiles.size);
        try {
            Pixmap white = new Pixmap(layerSize, layerSize, Pixmap.Format.RGBA8888);
            white.setBlending(Pixmap.Blending.None);
            white.setColor(1f, 1f, 1f, 1f);
            white.fill();
            layers.add(white);

            for (FileHandle pageFile : pageFiles) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("GPU snapshot preparation interrupted.");
                }
                Pixmap source = new Pixmap(pageFile);
                try {
                    if (source.getWidth() > layerSize || source.getHeight() > layerSize) {
                        throw new IllegalStateException(
                                "Atlas page exceeds fixed size " + layerSize + "x" + layerSize
                                        + ": " + pageFile.path()
                                        + " (" + source.getWidth() + "x" + source.getHeight() + ")"
                        );
                    }
                    if (source.getWidth() == layerSize
                            && source.getHeight() == layerSize
                            && source.getFormat() == Pixmap.Format.RGBA8888) {
                        layers.add(source);
                        source = null;
                    } else {
                        Pixmap normalized = new Pixmap(layerSize, layerSize, Pixmap.Format.RGBA8888);
                        normalized.setBlending(Pixmap.Blending.None);
                        normalized.drawPixmap(source, 0, 0);
                        layers.add(normalized);
                    }
                } finally {
                    if (source != null) source.dispose();
                }
            }

            return new PreparedGpuSnapshot(
                    sceneTag,
                    generation,
                    layerSize,
                    new Array<>(pageNames),
                    layers,
                    System.nanoTime() - started
            );
        } catch (RuntimeException failure) {
            disposePixmaps(layers);
            throw failure;
        }
    }

    public String sceneTag() {
        return sceneTag;
    }

    public long generation() {
        return generation;
    }

    public int layerCount() {
        return layers.size;
    }

    public int pageCount() {
        return pageNames.size;
    }

    String pageName(int index) {
        return pageNames.get(index);
    }

    public int layerSize() {
        return layerSize;
    }

    public long byteSize() {
        return byteSize;
    }

    public long preparationNs() {
        return preparationNs;
    }

    boolean isDisposed() {
        return disposed;
    }

    Pixmap layer(int index) {
        return layers.get(index);
    }

    Uploaded upload() {
        return upload(PreparedGpuSnapshot::uploadTextureArray);
    }

    Uploaded upload(TextureArrayUploader uploader) {
        if (disposed) throw new IllegalStateException("Prepared GPU snapshot is disposed.");
        if (uploader == null) throw new IllegalArgumentException("Texture-array uploader is null.");
        long started = System.nanoTime();
        TextureArray textureArray = uploader.upload(layers);
        if (textureArray == null) throw new IllegalStateException("Texture-array upload returned null.");
        return new Uploaded(
                sceneTag,
                generation,
                new Array<>(pageNames),
                textureArray,
                preparationNs,
                System.nanoTime() - started,
                byteSize
        );
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        disposePixmaps(layers);
        layers.clear();
    }

    private static TextureArray uploadTextureArray(Array<Pixmap> layers) {
        TextureData[] textureData = new TextureData[layers.size];
        for (int i = 0; i < layers.size; i++) {
            textureData[i] = new PixmapTextureData(
                    layers.get(i),
                    Pixmap.Format.RGBA8888,
                    false,
                    false
            );
        }
        TextureArrayData arrayData = new FileTextureArrayData(
                Pixmap.Format.RGBA8888,
                false,
                textureData
        );
        TextureArray textureArray = new TextureArray(arrayData);
        textureArray.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textureArray.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return textureArray;
    }

    private static void disposePixmaps(Array<Pixmap> pixmaps) {
        for (Pixmap pixmap : pixmaps) {
            if (pixmap != null && !pixmap.isDisposed()) pixmap.dispose();
        }
    }

    interface TextureArrayUploader {
        TextureArray upload(Array<Pixmap> layers);
    }

    public static final class Uploaded implements AutoCloseable {
        private final String sceneTag;
        private final long generation;
        private final Array<String> pageNames;
        private final long preparationNs;
        private final long uploadNs;
        private final long cpuByteSize;
        private TextureArray textureArray;

        private Uploaded(String sceneTag,
                         long generation,
                         Array<String> pageNames,
                         TextureArray textureArray,
                         long preparationNs,
                         long uploadNs,
                         long cpuByteSize) {
            this.sceneTag = sceneTag;
            this.generation = generation;
            this.pageNames = pageNames;
            this.textureArray = textureArray;
            this.preparationNs = preparationNs;
            this.uploadNs = uploadNs;
            this.cpuByteSize = cpuByteSize;
        }

        public String sceneTag() {
            return sceneTag;
        }

        public long generation() {
            return generation;
        }

        public long preparationNs() {
            return preparationNs;
        }

        public long uploadNs() {
            return uploadNs;
        }

        public long cpuByteSize() {
            return cpuByteSize;
        }

        AtlasRuntimeService.TextureArrayBundle buildBundle(Array<Texture> pageTextures) {
            if (textureArray == null) throw new IllegalStateException("Uploaded snapshot is no longer owned.");
            if (pageTextures == null || pageTextures.size != pageNames.size) {
                throw new IllegalStateException(
                        "Prepared page count " + pageNames.size
                                + " does not match loaded atlas page count "
                                + (pageTextures != null ? pageTextures.size : 0)
                );
            }

            InternalTextures.initIfNeeded();
            IntIntMap handleToLayer = new IntIntMap();
            handleToLayer.put(InternalTextures.whiteHandle(), 0);
            for (int i = 0; i < pageTextures.size; i++) {
                handleToLayer.put(TextureRegistry.handleOf(pageTextures.get(i)), i + 1);
            }

            TextureArray transferred = textureArray;
            textureArray = null;
            return new AtlasRuntimeService.TextureArrayBundle(transferred, handleToLayer);
        }

        @Override
        public void close() {
            if (textureArray == null) return;
            textureArray.dispose();
            textureArray = null;
        }
    }
}
