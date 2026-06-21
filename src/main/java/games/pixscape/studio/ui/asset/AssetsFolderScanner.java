package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;

public final class AssetsFolderScanner {

    private AssetsFolderScanner() {
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public static Array<AssetNode> scan(AssetNode folder) {

        Array<AssetNode> out = new Array<>();

        if (folder == null || folder.kind != AssetNode.Kind.FOLDER)
            return out;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null)
            return out;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle metaFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        AssetMetaDatabase db = AssetMetaDatabase.load(metaFile);

        FileHandle baseDir = switch (folder.root) {
            case IMAGES -> projectDir.child(StudioFs.DIR_ORIG_IMAGES);
            case ANIMATIONS -> projectDir.child(StudioFs.DIR_ORIG_ANIMATIONS);
            case PARTICLES -> projectDir.child(StudioFs.DIR_ORIG_EFFECTS);
            case TILES -> projectDir.child(StudioFs.DIR_ORIG_TILES);
            case PREFABS -> projectDir.child(StudioFs.DIR_PREFABS);
        };

        if (!baseDir.exists() || !baseDir.isDirectory())
            return out;

        FileHandle startDir = (folder.path == null || folder.path.isEmpty())
                ? baseDir
                : baseDir.child(folder.path);

        if (!startDir.exists() || !startDir.isDirectory())
            return out;

        // --------------------------------------------------------------------
        // Special case: Animations folder itself
        // --------------------------------------------------------------------

        if (folder.root == AssetNode.Root.ANIMATIONS
                && folder.path != null
                && !folder.path.isEmpty()) {

            String sourceRel = StudioFs.DIR_ORIG_ANIMATIONS + "/" + folder.path;
            AssetMeta meta = db.findBySourceRelPath(sourceRel);

            if (isUserVisible(meta)) {
                out.add(new AssetNode(
                        AssetNode.Kind.ANIMATION,
                        AssetNode.Root.ANIMATIONS,
                        folder.path,
                        startDir.name(),
                        null
                ));
            }
        }

        // --------------------------------------------------------------------
        // Special case: Tiles root = aggregate children content
        // --------------------------------------------------------------------

        if (folder.root == AssetNode.Root.TILES
                && (folder.path == null || folder.path.isEmpty())) {

            for (FileHandle child : baseDir.list()) {
                if (child.isDirectory()) {
                    scanRecursive(cfg, db, baseDir, child, folder.root, out);
                }
            }

            return out;
        }

        // --------------------------------------------------------------------
        // Normal recursive scan
        // --------------------------------------------------------------------

        scanRecursive(cfg, db, baseDir, startDir, folder.root, out);
        return out;
    }

    // ------------------------------------------------------------------------
    // Recursive scan
    // ------------------------------------------------------------------------

    private static void scanRecursive(
            ProjectConfig cfg,
            AssetMetaDatabase db,
            FileHandle baseDir,
            FileHandle current,
            AssetNode.Root root,
            Array<AssetNode> out
    ) {

        for (FileHandle f : current.list()) {

            if (f.isDirectory()) {

                // Animation folders are assets
                if (root == AssetNode.Root.ANIMATIONS) {

                    String relDir = relativePath(baseDir, f);
                    String sourceRel = StudioFs.DIR_ORIG_ANIMATIONS + "/" + relDir;

                    AssetMeta meta = db.findBySourceRelPath(sourceRel);

                    if (isUserVisible(meta)) {
                        out.add(new AssetNode(
                                AssetNode.Kind.ANIMATION,
                                AssetNode.Root.ANIMATIONS,
                                relDir,
                                f.name(),
                                null
                        ));
                    }
                }

                // Continue recursion
                scanRecursive(cfg, db, baseDir, f, root, out);
                continue;
            }

            String ext = f.extension().toLowerCase();
            String rel = relativePath(baseDir, f);

            switch (root) {

                case IMAGES -> {
                    if (!isImage(ext)) continue;

                    AssetMeta meta = db.findBySourceRelPath(
                            StudioFs.DIR_ORIG_IMAGES + "/" + rel
                    );

                    if (!isUserVisible(meta)) continue;

                    out.add(new AssetNode(
                            AssetNode.Kind.IMAGE,
                            AssetNode.Root.IMAGES,
                            rel,
                            f.name(),
                            null
                    ));
                }

                case TILES -> {
                    if (!isImage(ext)) continue;

                    AssetMeta meta = db.findBySourceRelPath(
                            StudioFs.DIR_ORIG_TILES + "/" + rel
                    );

                    if (!isUserVisible(meta)) continue;

                    out.add(new AssetNode(
                            AssetNode.Kind.IMAGE,
                            AssetNode.Root.TILES,
                            rel,
                            f.name(),
                            null
                    ));
                }

                case PARTICLES -> {
                    if (!ext.equals("p")) continue;

                    AssetMeta meta = db.findBySourceRelPath(
                            StudioFs.DIR_ORIG_EFFECTS + "/" + rel
                    );

                    if (!isUserVisible(meta)) continue;

                    out.add(new AssetNode(
                            AssetNode.Kind.PARTICLE,
                            AssetNode.Root.PARTICLES,
                            rel,
                            f.nameWithoutExtension(),
                            null
                    ));
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static boolean isUserVisible(AssetMeta meta) {
        return meta != null && meta.isUserVisible();
    }

    private static boolean isImage(String ext) {
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg");
    }

    private static String relativePath(FileHandle baseDir, FileHandle file) {
        return file.path()
                .substring(baseDir.path().length() + 1)
                .replace('\\', '/');
    }
}