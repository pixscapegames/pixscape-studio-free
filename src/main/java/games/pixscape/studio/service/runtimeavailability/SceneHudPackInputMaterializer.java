package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.Deflater;
import games.pixscape.studio.service.runtimeavailability.SceneHudPackInputPlan.*;
import games.pixscape.studio.service.runtimeavailability.SceneHudPackMaterializationManifest.*;

/** Pure CPU/file preparation. Does not pack, load textures or publish any live atlas.
 * Callers must serialize materializations to the same generated output directory. */
public final class SceneHudPackInputMaterializer {
    private static final byte[] PNG_SIGNATURE = {(byte)137, 80, 78, 71, 13, 10, 26, 10};

    public SceneHudPackMaterializationManifest materialize(
            FileHandle projectDirectory, FileHandle outputDirectory, SceneHudPackInputPlan plan) {
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(plan, "plan");
        validatePaths(projectDirectory, outputDirectory, plan);
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(outputDirectory);
        Throwable failure = null;
        try {
            List<MaterializedEntry> entries = new ArrayList<>();
            for (AtlasEntry entry : plan.entries()) {
                String stem = String.format(Locale.ROOT, "entry-%06d", entries.size());
                String path = stem + ".png";
                try {
                    entries.add(materializeEntry(projectDirectory, candidate.child(path), path, stem, entry));
                } catch (RuntimeException invalid) {
                    throw new IllegalStateException("Cannot materialize " + entry.key() + " from '" + entry.sourcePath()
                            + "': " + entry.provenance().stream().map(ProjectionProvenance::describe).toList(), invalid);
                }
            }
            List<BitmapFontMaterialization> fonts = new ArrayList<>();
            for (BitmapFontProjection requirement : plan.bitmapFonts()) {
                List<MaterializedEntry> pages = new ArrayList<>();
                for (AtlasKey key : requirement.keys()) {
                    MaterializedEntry physical = entries.stream().filter(entry -> entry.intendedKey().equals(key))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("Font requirement has no planned entry: " + key));
                    pages.add(physical);
                }
                fonts.add(new BitmapFontMaterialization(requirement, pages));
            }
            SceneHudPackMaterializationManifest manifest = new SceneHudPackMaterializationManifest(plan.rootIds(), entries, fonts);
            try (AtomicDirectoryPublication.Published published = AtomicDirectoryPublication.publish(candidate, outputDirectory)) {
                published.commit();
            }
            return manifest;
        } catch (RuntimeException | Error invalid) {
            failure = invalid;
            throw invalid;
        } finally {
            try { AtomicDirectoryPublication.discardCandidate(candidate); }
            catch (RuntimeException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private static MaterializedEntry materializeEntry(FileHandle project, FileHandle output,
                                                       String path, String stem, AtlasEntry entry) {
        int width;
        int height;
        boolean normalized = false;
        SemanticMetadata semantic;
        if (entry.materialKind() == MaterialKind.INTERNAL_WHITE_PIXEL) {
            Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            try {
                white.setBlending(Pixmap.Blending.None);
                white.drawPixel(0, 0, 0xffffffff);
                write(output, white);
            } finally { white.dispose(); }
            width = height = 1;
            semantic = new SemanticMetadata(1, 1, 0, 0, Map.of());
        } else if (entry.materialKind() == MaterialKind.INTERNAL_BUILT_IN_LABEL_FONT) {
            byte[] bytes = readBuiltInFontPage();
            Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
            try {
                width = pixels.getWidth();
                height = pixels.getHeight();
                write(output, pixels);
            } finally { pixels.dispose(); }
            semantic = new SemanticMetadata(width, height, 0, 0, Map.of());
        } else {
            FileHandle source = project.child(entry.sourcePath());
            // Snapshot ordinary images with the shared binary-copy helper, then validate exactly
            // those candidate bytes. A source changing after the copy cannot desynchronize geometry.
            if (entry.materialKind() == MaterialKind.FULL_IMAGE && !StudioIO.copyIfDifferent(source, output))
                throw new IllegalStateException("Source copy failed: " + source);
            byte[] bytes = (entry.materialKind() == MaterialKind.FULL_IMAGE ? output : source).readBytes();
            Pixmap pixels = new Pixmap(bytes, 0, bytes.length);
            try {
                if (entry.materialKind() == MaterialKind.FULL_IMAGE) {
                    width = pixels.getWidth();
                    height = pixels.getHeight();
                    // PNG bytes are already in their final candidate file; other decoded formats become PNG.
                    if (!isPng(bytes)) write(output, pixels);
                    semantic = new SemanticMetadata(width, height, 0, 0, Map.of());
                } else {
                    AtlasRegionMetadata region = entry.atlasRegion();
                    validateRegion(region, pixels);
                    width = region.width();
                    height = region.height();
                    normalized = region.rotate();
                    Pixmap crop = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                    try {
                        crop.setBlending(Pixmap.Blending.None);
                        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                            // TexturePacker stores 90-degree regions counterclockwise:
                            // canonical(x,y) -> stored(y,width-x-1). TextureAtlas swaps physical crop extents.
                            int sourceX = region.left() + (region.rotate() ? y : x);
                            int sourceY = region.top() + (region.rotate() ? width - x - 1 : y);
                            crop.drawPixel(x, y, pixels.getPixel(sourceX, sourceY));
                        }
                        write(output, crop);
                    } finally { crop.dispose(); }
                    semantic = new SemanticMetadata(region.originalWidth(), region.originalHeight(),
                            region.offsetX(), region.offsetY(), region.values());
                }
            } finally { pixels.dispose(); }
        }
        return new MaterializedEntry(path, new AtlasKey(stem, AtlasKey.UNINDEXED), entry,
                width, height, normalized, semantic);
    }

    private static void validateRegion(AtlasRegionMetadata region, Pixmap page) {
        if (region.flip()) throw new IllegalArgumentException("Flipped source atlas plans are unsupported; project with flip=false.");
        if ((region.degrees() != 0 && region.degrees() != 90) || region.rotate() != (region.degrees() == 90))
            throw new IllegalArgumentException("Unsupported or inconsistent source rotation: " + region.degrees());
        int packedWidth = region.rotate() ? region.height() : region.width();
        int packedHeight = region.rotate() ? region.width() : region.height();
        if (packedWidth <= 0 || packedHeight <= 0 || region.left() < 0 || region.top() < 0
                || (long) region.left() + packedWidth > page.getWidth()
                || (long) region.top() + packedHeight > page.getHeight())
            throw new IllegalArgumentException("Source atlas rectangle exceeds page pixels.");
        if (region.originalWidth() < region.width() || region.originalHeight() < region.height())
            throw new IllegalArgumentException("Source original size is smaller than packed geometry.");
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (bytes[i] != PNG_SIGNATURE[i]) return false;
        return true;
    }

    private static byte[] readBuiltInFontPage() {
        try (java.io.InputStream input = SceneHudPackInputMaterializer.class.getClassLoader()
                .getResourceAsStream(HudBuiltInLabelStyle.FONT_PAGE)) {
            if (input == null) {
                throw new IllegalStateException("LibGDX built-in Label font page is unavailable: "
                        + HudBuiltInLabelStyle.FONT_PAGE + ".");
            }
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read LibGDX built-in Label font page: "
                    + HudBuiltInLabelStyle.FONT_PAGE + ".", failure);
        }
    }
    private static void write(FileHandle output, Pixmap pixels) {
        PixmapIO.writePNG(output, pixels, Deflater.DEFAULT_COMPRESSION, false);
    }

    private static void validatePaths(FileHandle project, FileHandle output, SceneHudPackInputPlan plan) {
        Path target = output.file().toPath().toAbsolutePath().normalize();
        AtomicDirectoryPublication.rejectSymlinks(target);
        try {
            Path base = project.file().toPath().toRealPath();
            if (!Files.isDirectory(base)) throw new IllegalArgumentException("Project directory is missing.");
            if (base.startsWith(target)) throw new IllegalArgumentException("Output cannot replace the project or its ancestors.");
            List<String> sources = new ArrayList<>();
            for (AtlasEntry entry : plan.entries()) {
                if (entry.sourcePath() != null) sources.add(entry.sourcePath());
                if (entry.atlasRegion() != null) {
                    if (!entry.sourcePath().equals(entry.atlasRegion().sourcePagePath()))
                        throw new IllegalArgumentException("Region page path disagrees with entry source.");
                }
                for (ProjectionProvenance provenance : entry.provenance()) {
                    if (provenance.skinAtlasPath() != null) sources.add(provenance.skinAtlasPath());
                    if (provenance.bitmapFontDescriptorPath() != null) sources.add(provenance.bitmapFontDescriptorPath());
                }
            }
            for (BitmapFontProjection font : plan.bitmapFonts()) sources.add(font.descriptorPath());
            for (String source : sources) {
                Path relative = Path.of(source);
                if (relative.isAbsolute()) throw new IllegalArgumentException("Source must be project-relative: " + source);
                Path lexical = base.resolve(relative).normalize();
                if (!lexical.startsWith(base) || lexical.startsWith(target))
                    throw new IllegalArgumentException("Unsafe source/output overlap: " + source);
                // Resolve links if present; absent images fail while decoding inside the candidate transaction.
                if (Files.exists(lexical)) {
                    Path real = lexical.toRealPath();
                    if (!real.startsWith(base) || real.startsWith(target))
                        throw new IllegalArgumentException("Source escapes project or overlaps output: " + source);
                }
            }
        } catch (IOException failure) { throw new IllegalArgumentException("Cannot validate materialization paths.", failure); }
    }
}
