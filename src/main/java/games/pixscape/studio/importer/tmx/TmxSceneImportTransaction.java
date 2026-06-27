package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.ProjectFileCleanupService;

final class TmxSceneImportTransaction {

    private final ProjectConfig cfg;
    private final FileHandle projectDir;
    private final AssetMetaDatabase assetDb;
    private final FileHandle projectFile;
    private final FileHandle assetsFile;
    private final byte[] projectSnapshot;
    private final byte[] assetsSnapshot;
    private final Array<ProjectFileCleanupService.FileSnapshotEntry> origTilesSnapshot;
    private final String previousCurrentSceneName;
    private final int previousNextSceneIndex;

    TmxSceneImportTransaction(ProjectConfig cfg, FileHandle projectDir, AssetMetaDatabase assetDb) {
        this.cfg = cfg;
        this.projectDir = projectDir;
        this.assetDb = assetDb;
        this.projectFile = StudioFs.requireStudioProjectFile(cfg);
        this.assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        this.projectSnapshot = ProjectFileCleanupService.snapshotFile(projectFile);
        this.assetsSnapshot = ProjectFileCleanupService.snapshotFile(assetsFile);
        this.origTilesSnapshot = ProjectFileCleanupService.snapshotDirectory(projectDir.child(StudioFs.DIR_ORIG_TILES));
        this.previousCurrentSceneName = cfg.getCurrentSceneName();
        this.previousNextSceneIndex = cfg.nextSceneIndex;
    }

    void rollback(String createdSceneName, String createdSceneFileName, String createdSceneTag) {
        rollbackConfig(createdSceneName);

        if (createdSceneFileName != null && !createdSceneFileName.isBlank()) {
            ProjectFileCleanupService.deleteFileAndBackups(
                    projectDir.child(StudioFs.DIR_SCENES).child(createdSceneFileName)
            );
        }

        ProjectFileCleanupService.restoreDirectoryFromSnapshot(
                projectDir.child(StudioFs.DIR_ORIG_TILES),
                origTilesSnapshot
        );

        if (createdSceneTag != null && !createdSceneTag.isBlank()) {
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, createdSceneTag);
            ProjectFileCleanupService.deleteSceneAtlasInput(projectDir, createdSceneTag);
        }

        ProjectFileCleanupService.restoreFileFromSnapshot(projectFile, projectSnapshot);
        ProjectFileCleanupService.restoreFileFromSnapshot(assetsFile, assetsSnapshot);
        restoreAssetDbFromSnapshot();
    }

    private void rollbackConfig(String createdSceneName) {
        if (createdSceneName != null && !createdSceneName.isBlank()) {
            cfg.removeSceneMeta(createdSceneName);
        }
        cfg.nextSceneIndex = previousNextSceneIndex;
        if (previousCurrentSceneName != null && !previousCurrentSceneName.isBlank()) {
            cfg.setCurrentSceneByName(previousCurrentSceneName);
        }
    }

    private void restoreAssetDbFromSnapshot() {
        AssetMetaDatabase restored = AssetMetaDatabase.load(assetsFile);
        assetDb.version = restored.version;
        assetDb.nextId = restored.nextId;
        assetDb.assets = restored.assets;
    }
}
