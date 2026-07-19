package games.pixscape.studio.importer.tmx;

public final class TmxSceneImportRollback {

    private final TmxSceneImportTransaction transaction;
    private final String sceneName;
    private final String sceneFileName;
    private final String sceneTag;
    private boolean rolledBack;

    TmxSceneImportRollback(TmxSceneImportTransaction transaction,
                           String sceneName,
                           String sceneFileName,
                           String sceneTag) {
        this.transaction = transaction;
        this.sceneName = sceneName;
        this.sceneFileName = sceneFileName;
        this.sceneTag = sceneTag;
    }

    public void rollback() {
        if (rolledBack) {
            return;
        }
        if (transaction == null) {
            throw new IllegalStateException("TMX import rollback transaction is not available.");
        }
        transaction.rollback(sceneName, sceneFileName, sceneTag);
        rolledBack = true;
    }
}
