package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.StudioTextureArrayUploadBridge;
import games.pixscape.runtime.service.TextureRegistry;

/** CPU-only, generation-scoped atlas and texture-array publication input. */
public final class PreparedAtlasPublication implements AutoCloseable {

    private final String sceneTag;
    private final long generation;
    private final int layerSize;
    private final TextureAtlas.TextureAtlasData atlasData;
    private final Array<PreparedPage> pages;
    private final Array<String> snapshotPageNames;
    private final Array<Pixmap> snapshotLayers;
    private final Array<Long> pagePreparationNs;
    private final long metadataPreparationNs;
    private final long pageFileReadNs;
    private final long pageDecodeNormalizeNs;
    private final long pagePixelsPreparationNs;
    private final long preparationNs;
    private final long byteSize;
    private boolean disposed;

    private PreparedAtlasPublication(String sceneTag,
                                     long generation,
                                     int layerSize,
                                     TextureAtlas.TextureAtlasData atlasData,
                                     Array<PreparedPage> pages,
                                     Array<String> snapshotPageNames,
                                     Array<Pixmap> snapshotLayers,
                                     Array<Long> pagePreparationNs,
                                     long metadataPreparationNs,
                                     long pageFileReadNs,
                                     long pageDecodeNormalizeNs,
                                     long pagePixelsPreparationNs,
                                     long preparationNs,
                                     long byteSize) {
        this.sceneTag = sceneTag;
        this.generation = generation;
        this.layerSize = layerSize;
        this.atlasData = atlasData;
        this.pages = pages;
        this.snapshotPageNames = snapshotPageNames;
        this.snapshotLayers = snapshotLayers;
        this.pagePreparationNs = pagePreparationNs;
        this.metadataPreparationNs = metadataPreparationNs;
        this.pageFileReadNs = pageFileReadNs;
        this.pageDecodeNormalizeNs = pageDecodeNormalizeNs;
        this.pagePixelsPreparationNs = pagePixelsPreparationNs;
        this.preparationNs = preparationNs;
        this.byteSize = byteSize;
    }

    public static PreparedAtlasPublication prepare(String sceneTag,
                                                   long generation,
                                                   FileHandle atlasFile) {
        return prepare(sceneTag, generation, atlasFile, AtlasRuntimeService.fixedLayerSize());
    }

    static PreparedAtlasPublication prepare(String sceneTag,
                                            long generation,
                                            FileHandle atlasFile,
                                            int layerSize) {
        if (atlasFile == null) throw new IllegalArgumentException("Atlas file must not be null.");
        long started = System.nanoTime();
        TextureAtlas.TextureAtlasData data = new TextureAtlas.TextureAtlasData(
                atlasFile,
                atlasFile.parent(),
                false
        );
        long metadataNs = System.nanoTime() - started;
        return prepareFromData(sceneTag, generation, data, layerSize, started, metadataNs);
    }

    static PreparedAtlasPublication prepareFromPages(String sceneTag,
                                                     long generation,
                                                     Array<FileHandle> pageFiles,
                                                     Array<String> pageNames,
                                                     int layerSize) {
        if (pageFiles == null || pageNames == null || pageFiles.size != pageNames.size) {
            throw new IllegalArgumentException("Page files and names must have matching sizes.");
        }
        TextureAtlas.TextureAtlasData data = new TextureAtlas.TextureAtlasData();
        for (int i = 0; i < pageFiles.size; i++) {
            TextureAtlas.TextureAtlasData.Page page = new TextureAtlas.TextureAtlasData.Page();
            page.name = pageNames.get(i);
            page.textureFile = pageFiles.get(i);
            page.format = Pixmap.Format.RGBA8888;
            page.minFilter = Texture.TextureFilter.Linear;
            page.magFilter = Texture.TextureFilter.Linear;
            data.getPages().add(page);

            TextureAtlas.TextureAtlasData.Region region = new TextureAtlas.TextureAtlasData.Region();
            region.page = page;
            region.name = "page-" + i;
            region.width = 1;
            region.height = 1;
            region.originalWidth = 1;
            region.originalHeight = 1;
            data.getRegions().add(region);
        }
        long started = System.nanoTime();
        return prepareFromData(sceneTag, generation, data, layerSize, started, 0L);
    }

    private static PreparedAtlasPublication prepareFromData(
            String sceneTag,
            long generation,
            TextureAtlas.TextureAtlasData data,
            int layerSize,
            long started,
            long metadataNs) {
        if (sceneTag == null || sceneTag.isBlank()) {
            throw new IllegalArgumentException("Scene tag must not be blank.");
        }
        if (generation <= 0L) throw new IllegalArgumentException("Generation must be positive.");
        if (layerSize <= 0) throw new IllegalArgumentException("Layer size must be positive.");

        Array<PreparedPage> pages = new Array<>(data.getPages().size);
        ObjectMap<TextureAtlas.TextureAtlasData.Page, PreparedPage> pageLookup = new ObjectMap<>();
        Array<Long> pagePreparationNs = new Array<>(data.getPages().size);
        Pixmap white = null;
        long pagePixelsNs = 0L;
        long pageFileReadNs = 0L;
        long pageDecodeNormalizeNs = 0L;
        try {
            white = new Pixmap(layerSize, layerSize, Pixmap.Format.RGBA8888);
            white.setBlending(Pixmap.Blending.None);
            white.setColor(1f, 1f, 1f, 1f);
            white.fill();

            for (TextureAtlas.TextureAtlasData.Page page : data.getPages()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Atlas publication preparation interrupted.");
                }
                long pageStarted = System.nanoTime();
                PreparedPage preparedPage = preparePage(page, layerSize);
                long elapsed = System.nanoTime() - pageStarted;
                pagePixelsNs += elapsed;
                pageFileReadNs += preparedPage.fileReadNs;
                pageDecodeNormalizeNs += preparedPage.decodeNormalizeNs;
                pagePreparationNs.add(elapsed);
                pages.add(preparedPage);
                pageLookup.put(page, preparedPage);
            }

            Array<String> snapshotPageNames = new Array<>();
            Array<Pixmap> snapshotLayers = new Array<>();
            snapshotLayers.add(white);
            ObjectSet<TextureAtlas.TextureAtlasData.Page> seenPages = new ObjectSet<>();
            for (TextureAtlas.TextureAtlasData.Region region : data.getRegions()) {
                TextureAtlas.TextureAtlasData.Page page = region.page;
                if (page == null || !seenPages.add(page)) continue;
                PreparedPage preparedPage = pageLookup.get(page);
                if (preparedPage == null) {
                    throw new IllegalStateException("Atlas region references an unprepared page: " + page.name);
                }
                snapshotPageNames.add(page.name);
                snapshotLayers.add(preparedPage.snapshotPixels);
            }

            long byteSize = white.getPixels().capacity();
            for (PreparedPage page : pages) {
                byteSize += page.pagePixels.getPixels().capacity();
                if (page.snapshotPixels != page.pagePixels) {
                    byteSize += page.snapshotPixels.getPixels().capacity();
                }
            }
            return new PreparedAtlasPublication(
                    sceneTag,
                    generation,
                    layerSize,
                    data,
                    pages,
                    snapshotPageNames,
                    snapshotLayers,
                    pagePreparationNs,
                    metadataNs,
                    pageFileReadNs,
                    pageDecodeNormalizeNs,
                    pagePixelsNs,
                    System.nanoTime() - started,
                    byteSize
            );
        } catch (RuntimeException failure) {
            if (white != null && !white.isDisposed()) white.dispose();
            disposePages(pages);
            throw failure;
        }
    }

    private static PreparedPage preparePage(TextureAtlas.TextureAtlasData.Page page, int layerSize) {
        if (page == null || page.textureFile == null) {
            throw new IllegalStateException("Atlas page has no source file.");
        }
        long readStarted = System.nanoTime();
        byte[] encoded = page.textureFile.readBytes();
        long fileReadNs = System.nanoTime() - readStarted;
        long decodeStarted = System.nanoTime();
        Pixmap decoded = new Pixmap(encoded, 0, encoded.length);
        Pixmap pagePixels = decoded;
        Pixmap snapshotPixels = null;
        try {
            if (decoded.getWidth() > layerSize || decoded.getHeight() > layerSize) {
                throw new IllegalStateException(
                        "Atlas page exceeds fixed size " + layerSize + "x" + layerSize
                                + ": " + page.textureFile.path()
                                + " (" + decoded.getWidth() + "x" + decoded.getHeight() + ")"
                );
            }
            if (decoded.getFormat() != page.format) {
                pagePixels = copyTo(decoded, decoded.getWidth(), decoded.getHeight(), page.format);
                decoded.dispose();
            }
            if (pagePixels.getWidth() == layerSize
                    && pagePixels.getHeight() == layerSize
                    && pagePixels.getFormat() == Pixmap.Format.RGBA8888) {
                snapshotPixels = pagePixels;
            } else {
                snapshotPixels = copyTo(pagePixels, layerSize, layerSize, Pixmap.Format.RGBA8888);
            }
            return new PreparedPage(
                    page,
                    pagePixels,
                    snapshotPixels,
                    fileReadNs,
                    System.nanoTime() - decodeStarted
            );
        } catch (RuntimeException failure) {
            if (snapshotPixels != null && snapshotPixels != pagePixels && !snapshotPixels.isDisposed()) {
                snapshotPixels.dispose();
            }
            if (pagePixels != null && !pagePixels.isDisposed()) pagePixels.dispose();
            throw failure;
        }
    }

    private static Pixmap copyTo(Pixmap source, int width, int height, Pixmap.Format format) {
        Pixmap copy = new Pixmap(width, height, format);
        copy.setBlending(Pixmap.Blending.None);
        copy.drawPixmap(source, 0, 0);
        return copy;
    }

    public String sceneTag() {
        return sceneTag;
    }

    public long generation() {
        return generation;
    }

    public int layerCount() {
        return snapshotLayers.size;
    }

    public int pageCount() {
        return snapshotPageNames.size;
    }

    String pageName(int index) {
        return snapshotPageNames.get(index);
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

    public long metadataPreparationNs() {
        return metadataPreparationNs;
    }

    public long pagePixelsPreparationNs() {
        return pagePixelsPreparationNs;
    }

    public long pageFileReadNs() {
        return pageFileReadNs;
    }

    public long pageDecodeNormalizeNs() {
        return pageDecodeNormalizeNs;
    }

    long pagePreparationNs(int index) {
        return pagePreparationNs.get(index);
    }

    boolean isDisposed() {
        return disposed;
    }

    Pixmap layer(int index) {
        return snapshotLayers.get(index);
    }

    Pixmap pagePixels(int index) {
        return pages.get(index).pagePixels;
    }

    Uploaded upload(FileHandle publishedImagesDir) {
        return upload(
                publishedImagesDir,
                PreparedAtlasPublication::uploadPageTexture,
                PreparedAtlasPublication::uploadTextureArray
        );
    }

    Uploaded upload(FileHandle publishedImagesDir,
                    PageTextureUploader pageUploader,
                    TextureArrayUploader arrayUploader) {
        if (disposed) throw new IllegalStateException("Prepared atlas publication is disposed.");
        if (publishedImagesDir == null) throw new IllegalArgumentException("Published images directory is null.");
        if (pageUploader == null) throw new IllegalArgumentException("Page texture uploader is null.");
        if (arrayUploader == null) throw new IllegalArgumentException("Texture-array uploader is null.");

        TextureArray textureArray = null;
        TextureAtlas atlas = null;
        Array<Texture> pageTextures = new Array<>(pages.size);
        long arrayUploadNs = 0L;
        long pageUploadNs = 0L;
        long atlasAssemblyNs = 0L;
        try {
            // Upload the array first. Managed page textures consume and dispose their
            // preloaded Pixmaps, so this ordering lets both GPU objects share one decode.
            long phaseStarted = System.nanoTime();
            textureArray = arrayUploader.upload(snapshotLayers);
            arrayUploadNs = System.nanoTime() - phaseStarted;
            if (textureArray == null) throw new IllegalStateException("Texture-array upload returned null.");

            phaseStarted = System.nanoTime();
            for (PreparedPage preparedPage : pages) {
                TextureAtlas.TextureAtlasData.Page page = preparedPage.metadata;
                FileHandle publishedPageFile = publishedImagesDir.child(page.name);
                Texture texture = pageUploader.upload(publishedPageFile, preparedPage.pagePixels, page);
                if (texture == null) throw new IllegalStateException("Page texture upload returned null: " + page.name);
                page.textureFile = publishedPageFile;
                page.texture = texture;
                pageTextures.add(texture);
            }
            pageUploadNs = System.nanoTime() - phaseStarted;

            phaseStarted = System.nanoTime();
            atlas = new TextureAtlas(atlasData);
            atlasAssemblyNs = System.nanoTime() - phaseStarted;
            Array<Texture> snapshotPageTextures = AtlasRuntimeService.getPageTextures(atlas);
            if (snapshotPageTextures.size != snapshotPageNames.size) {
                throw new IllegalStateException(
                        "Prepared page count " + snapshotPageNames.size
                                + " does not match assembled atlas page count " + snapshotPageTextures.size
                );
            }

            TextureAtlas transferredAtlas = atlas;
            TextureArray transferredArray = textureArray;
            atlas = null;
            textureArray = null;
            pageTextures.clear();
            return new Uploaded(
                    sceneTag,
                    generation,
                    new Array<>(snapshotPageNames),
                    snapshotPageTextures,
                    transferredAtlas,
                    transferredArray,
                    preparationNs,
                    metadataPreparationNs,
                    pageFileReadNs,
                    pageDecodeNormalizeNs,
                    pagePixelsPreparationNs,
                    arrayUploadNs,
                    pageUploadNs,
                    atlasAssemblyNs,
                    byteSize
            );
        } finally {
            if (atlas != null) {
                atlas.dispose();
            } else if (!pageTextures.isEmpty()) {
                for (Texture texture : pageTextures) {
                    if (texture != null) texture.dispose();
                }
            }
            if (textureArray != null) textureArray.dispose();
        }
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        if (!snapshotLayers.isEmpty()) {
            Pixmap white = snapshotLayers.first();
            if (white != null && !white.isDisposed()) white.dispose();
        }
        disposePages(pages);
        pages.clear();
        snapshotLayers.clear();
    }

    private static Texture uploadPageTexture(FileHandle publishedFile,
                                             Pixmap pagePixels,
                                             TextureAtlas.TextureAtlasData.Page page) {
        FileTextureData textureData = new FileTextureData(
                publishedFile,
                pagePixels,
                page.format,
                page.useMipMaps
        );
        return new Texture(textureData);
    }

    private static TextureArray uploadTextureArray(Array<Pixmap> layers) {
        return StudioTextureArrayUploadBridge.uploadBorrowed(layers);
    }

    private static void disposePages(Array<PreparedPage> pages) {
        for (PreparedPage page : pages) {
            if (page.snapshotPixels != page.pagePixels && !page.snapshotPixels.isDisposed()) {
                page.snapshotPixels.dispose();
            }
            if (!page.pagePixels.isDisposed()) page.pagePixels.dispose();
        }
    }

    interface PageTextureUploader {
        Texture upload(FileHandle publishedFile,
                       Pixmap pagePixels,
                       TextureAtlas.TextureAtlasData.Page page);
    }

    interface TextureArrayUploader {
        TextureArray upload(Array<Pixmap> layers);
    }

    private record PreparedPage(TextureAtlas.TextureAtlasData.Page metadata,
                                Pixmap pagePixels,
                                Pixmap snapshotPixels,
                                long fileReadNs,
                                long decodeNormalizeNs) {
    }

    public static final class Uploaded implements AutoCloseable {
        private final String sceneTag;
        private final long generation;
        private final Array<String> pageNames;
        private final Array<Texture> pageTextures;
        private final long preparationNs;
        private final long metadataPreparationNs;
        private final long pageFileReadNs;
        private final long pageDecodeNormalizeNs;
        private final long pagePixelsPreparationNs;
        private final long textureArrayUploadNs;
        private final long pageTextureUploadNs;
        private final long atlasAssemblyNs;
        private final long cpuByteSize;
        private TextureAtlas atlas;
        private TextureArray textureArray;

        private Uploaded(String sceneTag,
                         long generation,
                         Array<String> pageNames,
                         Array<Texture> pageTextures,
                         TextureAtlas atlas,
                         TextureArray textureArray,
                         long preparationNs,
                         long metadataPreparationNs,
                         long pageFileReadNs,
                         long pageDecodeNormalizeNs,
                         long pagePixelsPreparationNs,
                         long textureArrayUploadNs,
                         long pageTextureUploadNs,
                         long atlasAssemblyNs,
                         long cpuByteSize) {
            this.sceneTag = sceneTag;
            this.generation = generation;
            this.pageNames = pageNames;
            this.pageTextures = pageTextures;
            this.atlas = atlas;
            this.textureArray = textureArray;
            this.preparationNs = preparationNs;
            this.metadataPreparationNs = metadataPreparationNs;
            this.pageFileReadNs = pageFileReadNs;
            this.pageDecodeNormalizeNs = pageDecodeNormalizeNs;
            this.pagePixelsPreparationNs = pagePixelsPreparationNs;
            this.textureArrayUploadNs = textureArrayUploadNs;
            this.pageTextureUploadNs = pageTextureUploadNs;
            this.atlasAssemblyNs = atlasAssemblyNs;
            this.cpuByteSize = cpuByteSize;
        }

        public String sceneTag() {
            return sceneTag;
        }

        public long generation() {
            return generation;
        }

        public int pageCount() {
            return pageNames.size;
        }

        public long preparationNs() {
            return preparationNs;
        }

        public long metadataPreparationNs() {
            return metadataPreparationNs;
        }

        public long pagePixelsPreparationNs() {
            return pagePixelsPreparationNs;
        }

        public long pageFileReadNs() {
            return pageFileReadNs;
        }

        public long pageDecodeNormalizeNs() {
            return pageDecodeNormalizeNs;
        }

        public long textureArrayUploadNs() {
            return textureArrayUploadNs;
        }

        public long pageTextureUploadNs() {
            return pageTextureUploadNs;
        }

        public long atlasAssemblyNs() {
            return atlasAssemblyNs;
        }

        public long cpuByteSize() {
            return cpuByteSize;
        }

        TextureAtlas atlas() {
            return atlas;
        }

        public TextureAtlas takeAtlas() {
            if (atlas == null) throw new IllegalStateException("Prepared atlas is no longer owned.");
            TextureAtlas transferred = atlas;
            atlas = null;
            return transferred;
        }

        AtlasRuntimeService.TextureArrayBundle buildBundle() {
            if (textureArray == null) throw new IllegalStateException("Uploaded snapshot is no longer owned.");
            if (pageTextures.size != pageNames.size) {
                throw new IllegalStateException(
                        "Prepared page count " + pageNames.size
                                + " does not match loaded atlas page count " + pageTextures.size
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
            if (atlas != null) {
                atlas.dispose();
                atlas = null;
            }
            if (textureArray != null) {
                textureArray.dispose();
                textureArray = null;
            }
        }
    }
}
