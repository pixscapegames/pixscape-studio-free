package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.helper.RuntimeFs;
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
    private final FileHandle tileAnimationsFile;
    private final byte[] projectSnapshot;
    private final byte[] assetsSnapshot;
    private final byte[] tileAnimationsSnapshot;
    private final Array<ProjectFileCleanupService.FileSnapshotEntry> origTilesSnapshot;
    private final Array<ProjectFileCleanupService.FileSnapshotEntry> origImagesSnapshot;
    private final String previousCurrentSceneName;
    private final int previousNextSceneIndex;

    TmxSceneImportTransaction(ProjectConfig cfg, FileHandle projectDir, AssetMetaDatabase assetDb) {
        this.cfg = cfg;
        this.projectDir = projectDir;
        this.assetDb = assetDb;
        this.projectFile = StudioFs.requireStudioProjectFile(cfg);
        this.assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        this.tileAnimationsFile = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        this.projectSnapshot = ProjectFileCleanupService.snapshotFile(projectFile);
        this.assetsSnapshot = ProjectFileCleanupService.snapshotFile(assetsFile);
        this.tileAnimationsSnapshot = ProjectFileCleanupService.snapshotFile(tileAnimationsFile);
        this.origTilesSnapshot = ProjectFileCleanupService.snapshotDirectory(projectDir.child(StudioFs.DIR_ORIG_TILES));
        this.origImagesSnapshot = ProjectFileCleanupService.snapshotDirectory(projectDir.child(StudioFs.DIR_ORIG_IMAGES));
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
        ProjectFileCleanupService.restoreDirectoryFromSnapshot(
                projectDir.child(StudioFs.DIR_ORIG_IMAGES),
                origImagesSnapshot
        );

        if (createdSceneTag != null && !createdSceneTag.isBlank()) {
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, createdSceneTag);
            ProjectFileCleanupService.deleteSceneAtlasInput(projectDir, createdSceneTag);
        }

        ProjectFileCleanupService.restoreFileFromSnapshot(projectFile, projectSnapshot);
        ProjectFileCleanupService.restoreFileFromSnapshot(assetsFile, assetsSnapshot);
        ProjectFileCleanupService.restoreFileFromSnapshot(tileAnimationsFile, tileAnimationsSnapshot);
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
        assetDb.replaceStateFrom(restored);
    }
}
