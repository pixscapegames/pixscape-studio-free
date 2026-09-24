package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Validates and atomically imports one text BMFont descriptor and all of its PNG pages. */
public final class BitmapFontAssetImportService {
    private final AssetMetaDatabase database;

    public BitmapFontAssetImportService(AssetMetaDatabase database) {
        if (database == null) throw new IllegalArgumentException("Asset database is required.");
        this.database = database;
    }

    public AssetMeta importNew(FileHandle descriptor, FileHandle projectDir) {
        FontBundle bundle = validate(descriptor);
        FileHandle fontsRoot = projectDir.child(StudioFs.DIR_ORIG_FONTS);
        fontsRoot.mkdirs();
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(fontsRoot);
        AssetMeta created = null;
        try {
            copyBundle(bundle, candidate);
            String base = descriptor.nameWithoutExtension();
            String logical = uniqueLogicalPath(base);
            created = database.registerIfAbsent(AssetType.FONT, logical, null,
                    AssetMeta.AssetScope.USER);
            FileHandle target = fontsRoot.child(Integer.toString(created.id()));
            moveDirectory(candidate, target);
            database.updateSourceRelPath(created.id(), StudioFs.DIR_ORIG_FONTS + "/"
                    + created.id() + "/" + descriptor.name());
            return created;
        } catch (RuntimeException failure) {
            if (created != null) database.removeById(created.id());
            if (candidate.exists()) AtomicDirectoryPublication.discardCandidate(candidate);
            throw failure;
        }
    }

    public AssetMeta reimport(int assetId, FileHandle descriptor, FileHandle projectDir) {
        AssetMeta asset = database.findById(assetId);
        if (asset == null || asset.type() != AssetType.FONT) {
            throw new IllegalArgumentException("Font Asset " + assetId + " does not exist.");
        }
        FontBundle bundle = validate(descriptor);
        FileHandle fontsRoot = projectDir.child(StudioFs.DIR_ORIG_FONTS);
        fontsRoot.mkdirs();
        FileHandle target = fontsRoot.child(Integer.toString(assetId));
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(target);
        try {
            copyBundle(bundle, candidate);
            try (AtomicDirectoryPublication.Published publication =
                         AtomicDirectoryPublication.publish(candidate, target)) {
                database.updateSourceRelPath(assetId, StudioFs.DIR_ORIG_FONTS + "/" + assetId
                        + "/" + descriptor.name());
                publication.commit();
            }
            return asset;
        } finally {
            if (candidate.exists()) AtomicDirectoryPublication.discardCandidate(candidate);
        }
    }

    private String uniqueLogicalPath(String base) {
        String initial = StudioFs.PREFIX_FONTS + base;
        if (database.findByLogicalPath(initial) == null) return initial;
        for (int suffix = database.nextId(); ; suffix++) {
            String candidate = initial + "-" + suffix;
            if (database.findByLogicalPath(candidate) == null) return candidate;
        }
    }

    private static FontBundle validate(FileHandle descriptor) {
        if (descriptor == null || !descriptor.exists() || descriptor.isDirectory()
                || !"fnt".equalsIgnoreCase(descriptor.extension())) {
            throw new IllegalArgumentException("A readable .fnt bitmap-font descriptor is required.");
        }
        BitmapFontData data;
        try {
            data = new BitmapFontData(descriptor, false);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid text BMFont descriptor '"
                    + descriptor.name() + "'.", failure);
        }
        String[] imagePaths = data.getImagePaths();
        if (imagePaths == null || imagePaths.length == 0) {
            throw new IllegalArgumentException("Bitmap-font descriptor '" + descriptor.name()
                    + "' declares no texture pages.");
        }
        Path root = descriptor.parent().file().toPath().toAbsolutePath().normalize();
        List<Page> pages = new ArrayList<>();
        for (int index = 0; index < imagePaths.length; index++) {
            FileHandle page = new FileHandle(imagePaths[index]);
            Path path = page.file().toPath().toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("Bitmap-font page " + index
                        + " escapes the descriptor folder: " + imagePaths[index] + ".");
            }
            if (!page.exists() || page.isDirectory()) {
                throw new IllegalArgumentException("Bitmap-font page " + index
                        + " is missing: " + root.relativize(path).toString() + ".");
            }
            if (!"png".equalsIgnoreCase(page.extension())) {
                throw new IllegalArgumentException("Bitmap-font page " + index
                        + " must be a PNG: " + page.name() + ".");
            }
            try {
                Pixmap image = new Pixmap(page);
                image.dispose();
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Bitmap-font page " + index
                        + " is not a readable PNG: " + page.name() + ".", failure);
            }
            pages.add(new Page(page, root.relativize(path)));
        }
        return new FontBundle(descriptor, List.copyOf(pages));
    }

    private static void copyBundle(FontBundle bundle, FileHandle candidate) {
        bundle.descriptor.copyTo(candidate.child(bundle.descriptor.name()));
        for (Page page : bundle.pages) {
            FileHandle target = new FileHandle(candidate.file().toPath().resolve(page.relative).toFile());
            target.parent().mkdirs();
            page.file.copyTo(target);
        }
    }

    private static void moveDirectory(FileHandle source, FileHandle target) {
        if (target.exists()) throw new IllegalStateException("Font import target already exists: " + target.path());
        try {
            try {
                Files.move(source.file().toPath(), target.file().toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(source.file().toPath(), target.file().toPath());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to publish bitmap-font Asset.", failure);
        }
    }

    private record Page(FileHandle file, Path relative) { }
    private record FontBundle(FileHandle descriptor, List<Page> pages) { }
}
