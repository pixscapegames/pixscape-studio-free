package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.io.AtlasRegionPixels;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Converts a text atlas into ordinary authored PNG assets; packing remains scene-driven. */
public final class TextureAtlasAssetImportService {
    private final AssetMetaDatabase database;

    public TextureAtlasAssetImportService(AssetMetaDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public List<AssetMeta> importNew(FileHandle descriptor, FileHandle projectDir) {
        TextureAtlasData data = read(descriptor);
        List<Group> groups = groups(data);
        Map<Page, Pixmap> pages = new IdentityHashMap<>();
        FileHandle candidate = null;
        List<AssetMeta> created = new ArrayList<>();
        List<Move> published = new ArrayList<>();
        try {
            for (Page page : data.getPages()) pages.put(page, readPage(descriptor, page));
            for (Group group : groups) for (Region region : group.regions)
                validateRegion(region, pages.get(region.page));
            // All pixels are prepared before any catalog identity becomes visible.
            candidate = AtomicDirectoryPublication.createCandidate(projectDir.child("orig/atlas-import"));
            for (int i = 0; i < groups.size(); i++) {
                Group group = groups.get(i);
                for (int frame = 0; frame < group.regions.size(); frame++) {
                    Region region = group.regions.get(frame);
                    Pixmap restored = new Pixmap(region.originalWidth, region.originalHeight, Pixmap.Format.RGBA8888);
                    try {
                        AtlasRegionPixels.copy(pages.get(region.page), restored, region.left, region.top,
                                region.width, region.height, region.rotate, (int) region.offsetX,
                                region.originalHeight - region.height - (int) region.offsetY);
                        PixmapIO.writePNG(candidate.child(i + "/" + frame + ".png"), restored);
                    } finally { restored.dispose(); }
                }
            }
            for (int i = 0; i < groups.size(); i++) {
                Group group = groups.get(i);
                AssetMeta meta = database.registerIfAbsent(group.type, group.logicalPath(), null, AssetMeta.AssetScope.USER);
                created.add(meta);
                String stem = group.name + "__a" + meta.id();
                String relative;
                FileHandle staged = candidate.child(Integer.toString(i));
                if (meta instanceof AnimationAssetMeta animation) {
                    int digits = Math.max(4, Integer.toString(group.regions.size() - 1).length());
                    for (int frame = 0; frame < group.regions.size(); frame++)
                        move(staged.child(frame + ".png"), staged.child(stem + "_" + String.format(Locale.ROOT, "%0" + digits + "d", frame) + ".png"));
                    StudioAnimationAssets.initializeImportedFrames(animation, group.regions.size());
                    relative = StudioFs.DIR_ORIG_ANIMATIONS + "/" + stem;
                } else {
                    relative = StudioFs.DIR_ORIG_IMAGES + "/" + stem + ".png";
                    staged = staged.child("0.png");
                }
                FileHandle target = projectDir.child(relative);
                AtomicDirectoryPublication.rejectSymlinks(target.file().toPath());
                target.parent().mkdirs();
                move(staged, target);
                published.add(new Move(staged, target));
                database.updateSourceRelPath(meta.id(), relative);
            }
            AtomicDirectoryPublication.discardCandidate(candidate);
            candidate = null;
            return List.copyOf(created);
        } catch (RuntimeException | Error failure) {
            for (int i = published.size() - 1; i >= 0; i--) {
                Move publication = published.get(i);
                try {
                    publication.staged.parent().mkdirs();
                    move(publication.target, publication.staged);
                }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            for (AssetMeta meta : created) database.removeById(meta.id()); // Allocated IDs remain consumed.
            throw failure;
        } finally {
            for (Pixmap page : pages.values()) page.dispose();
            if (candidate != null) AtomicDirectoryPublication.discardCandidate(candidate);
        }
    }

    private List<Group> groups(TextureAtlasData data) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Set<String> animationNames = new HashSet<>();
        for (int i = 0; i < database.size(); i++)
            if (database.assetAt(i) instanceof AnimationAssetMeta animation)
                animationNames.add(games.pixscape.runtime.helper.RuntimeFs.baseName(animation.logicalPath()));
        for (Region region : data.getRegions()) {
            validateName(region.name);
            if (region.index < -1) throw invalid(region, "index must be -1 or nonnegative");
            AssetType type = region.index >= 0 ? AssetType.ANIMATION : AssetType.IMAGE;
            String key = type + "/" + region.name;
            Group group = groups.get(key);
            if (group == null) {
                group = new Group(region.name, type, new ArrayList<>(), new HashSet<>());
                groups.put(key, group);
                if (type == AssetType.ANIMATION
                        && !animationNames.add(games.pixscape.runtime.helper.RuntimeFs.baseName(group.logicalPath())))
                    throw invalid(region, "Animation name already exists under the Runtime naming convention. Rename the source region before importing");
            }
            if (database.findByLogicalPath(group.logicalPath()) != null)
                throw invalid(region, "Asset already exists: " + group.logicalPath() + ". Rename the source region before importing");
            if (!group.indices.add(region.index))
                throw invalid(region, "duplicate (name, index): (" + region.name + ", " + region.index + ")");
            if (!group.regions.isEmpty()) {
                Region previous = group.regions.getFirst();
                if (previous.originalWidth != region.originalWidth || previous.originalHeight != region.originalHeight)
                    throw invalid(region, "animation frames must have identical original dimensions");
            }
            group.regions.add(region);
        }
        for (Group group : groups.values()) group.regions.sort(Comparator.comparingInt(region -> region.index));
        return List.copyOf(groups.values());
    }

    private static TextureAtlasData read(FileHandle descriptor) {
        if (descriptor == null || !descriptor.exists() || descriptor.isDirectory()
                || !"atlas".equalsIgnoreCase(descriptor.extension()))
            throw new IllegalArgumentException("A readable LibGDX .atlas descriptor is required.");
        TextureAtlasData data;
        try { data = new TextureAtlasData(descriptor, descriptor.parent(), false); }
        catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid LibGDX atlas '" + descriptor.name() + "': " + failure.getMessage(), failure);
        }
        if (data.getPages().isEmpty() || data.getRegions().isEmpty())
            throw new IllegalArgumentException("Atlas declares no pages or regions: " + descriptor.name());
        return data;
    }

    private static Pixmap readPage(FileHandle descriptor, Page page) {
        Path root = descriptor.parent().file().toPath().toAbsolutePath().normalize();
        Path path = page.textureFile.file().toPath().toAbsolutePath().normalize();
        if (!path.startsWith(root) || Path.of(page.name).isAbsolute())
            throw new IllegalArgumentException("Atlas page escapes the descriptor folder: " + page.name);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Atlas page is missing: " + page.name);
        try {
            if (!path.toRealPath().startsWith(root.toRealPath()))
                throw new IllegalArgumentException("Atlas page escapes the descriptor folder: " + page.name);
        } catch (IOException failure) { throw new IllegalArgumentException("Cannot read atlas page: " + page.name, failure); }
        if (!"png".equalsIgnoreCase(page.textureFile.extension()))
            throw new IllegalArgumentException("Atlas page must be a PNG: " + page.name);
        if (page.pma) throw new IllegalArgumentException("Premultiplied-alpha atlas pages are unsupported: " + page.name);
        byte[] bytes = page.textureFile.readBytes();
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < signature.length || !Arrays.equals(signature, Arrays.copyOf(bytes, signature.length)))
            throw new IllegalArgumentException("Atlas page is not a PNG: " + page.name);
        Pixmap pixels;
        try { pixels = new Pixmap(bytes, 0, bytes.length); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Cannot decode PNG atlas page: " + page.name, failure); }
        // An omitted page size is legal in LibGDX; a declared size must match the actual PNG.
        if ((page.width != 0 || page.height != 0)
                && (page.width != pixels.getWidth() || page.height != pixels.getHeight())) {
            pixels.dispose();
            throw new IllegalArgumentException("Declared atlas page size does not match PNG: " + page.name);
        }
        return pixels;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.startsWith(".")
                || name.endsWith(".") || !name.equals(name.strip())
                || name.chars().anyMatch(c -> c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0))
            throw new IllegalArgumentException("Unsafe atlas Asset name: '" + name + "'. Use a single file name without path separators.");
    }

    private static void validateRegion(Region region, Pixmap page) {
        if (region.degrees != 0 && region.degrees != 90)
            throw invalid(region, "unsupported packing rotation " + region.degrees + " (expected 0 or 90)");
        if (region.names != null && region.names.length > 0)
            throw invalid(region, "unsupported region metadata: " + String.join(", ", region.names));
        int packedWidth = region.rotate ? region.height : region.width;
        int packedHeight = region.rotate ? region.width : region.height;
        if (region.width <= 0 || region.height <= 0 || region.left < 0 || region.top < 0
                || (long) region.left + packedWidth > page.getWidth()
                || (long) region.top + packedHeight > page.getHeight())
            throw invalid(region, "rectangle is invalid or exceeds page '" + region.page.name + "'");
        if (region.originalWidth <= 0 || region.originalHeight <= 0
                || (long) region.originalWidth * region.originalHeight > Integer.MAX_VALUE / 4
                || region.offsetX < 0 || region.offsetY < 0
                || (double) region.offsetX + region.width > region.originalWidth
                || (double) region.offsetY + region.height > region.originalHeight)
            throw invalid(region, "invalid original dimensions or trim offsets");
    }

    private static IllegalArgumentException invalid(Region region, String message) {
        return new IllegalArgumentException("Atlas region '" + region.name + "' (index " + region.index + "): " + message + ".");
    }

    private static void move(FileHandle source, FileHandle target) {
        try { Files.move(source.file().toPath(), target.file().toPath()); }
        catch (IOException failure) { throw new IllegalStateException("Cannot publish atlas Asset: " + target, failure); }
    }

    private record Move(FileHandle staged, FileHandle target) { }
    private record Group(String name, AssetType type, List<Region> regions, Set<Integer> indices) {
        String logicalPath() { return (type == AssetType.ANIMATION ? StudioFs.PREFIX_ANIMATIONS : StudioFs.PREFIX_IMAGES) + name; }
    }
}
