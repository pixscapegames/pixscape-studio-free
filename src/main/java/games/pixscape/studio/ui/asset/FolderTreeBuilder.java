package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTree;

public final class FolderTreeBuilder {

    private FolderTreeBuilder() {
    }

    public static VisTree.Node buildFolders(
            VisTree tree,
            FileHandle rootDir,
            String rootLabel,
            AssetNode.Root root
    ) {
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) return null;

        VisTree.Node rootNode = createRootNode(rootLabel, root);
        tree.add(rootNode);
        tree.getSelection().add(rootNode);

        buildRecursive(rootDir, rootDir, rootNode, root);
        return rootNode;
    }

    public static VisTree.Node buildFolders(
            VisTree.Node parent,
            FileHandle rootDir,
            String rootLabel,
            AssetNode.Root root
    ) {
        if (parent == null || rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) return null;

        VisTree.Node rootNode = createRootNode(rootLabel, root);
        parent.add(rootNode);

        buildRecursive(rootDir, rootDir, rootNode, root);
        return rootNode;
    }

    private static VisTree.Node createRootNode(String rootLabel, AssetNode.Root root) {
        VisLabel label = new VisLabel(rootLabel);
        VisTree.Node rootNode = new VisTree.Node(label) {
        };

        AssetNode rootNodeData = new AssetNode(
                AssetNode.Kind.FOLDER,
                root,
                "",
                rootLabel,
                null
        );
        label.setUserObject(rootNodeData);
        return rootNode;
    }

    private static void buildRecursive(
            FileHandle rootDir,
            FileHandle currentDir,
            VisTree.Node parent,
            AssetNode.Root root
    ) {
        for (FileHandle child : currentDir.list()) {

            if (!child.isDirectory()) continue;

            String relPath = child.path()
                    .substring(rootDir.path().length() + 1)
                    .replace('\\', '/');

            AssetNode data = new AssetNode(
                    AssetNode.Kind.FOLDER,
                    root,
                    relPath,
                    child.name(),
                    null
            );

            // -------------------------------------------------
            // Tiles : detect tile size
            // -------------------------------------------------
            if (root == AssetNode.Root.TILES) {

                int[] size = resolveTileSize(child);

                if (size != null) {
                    data.tileWidth = size[0];
                    data.tileHeight = size[1];
                }
            }

            // -------------------------------------------------
            // Construire label
            // -------------------------------------------------
            String display = data.name;

            if (data.tileWidth > 0) {
                display += " (" + data.tileWidth + "X" + data.tileHeight + ")";
            }

            VisLabel label = new VisLabel(display);
            VisTree.Node node = new VisTree.Node(label) {
            };

            label.setUserObject(data);
            parent.add(node);

            buildRecursive(rootDir, child, node, root);
        }
    }

    private static int[] resolveTileSize(FileHandle dir) {

        FileHandle[] pngs = dir.list((d, name) -> name.toLowerCase().endsWith(".png"));
        if (pngs == null || pngs.length == 0) return null;

        try {
            Pixmap pm = new Pixmap(pngs[0]);
            int w = pm.getWidth();
            int h = pm.getHeight();
            pm.dispose();
            return new int[]{w, h};
        } catch (Exception e) {
            return null;
        }
    }
}
