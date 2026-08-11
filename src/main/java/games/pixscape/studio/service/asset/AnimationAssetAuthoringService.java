package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns authoritative mutations of authored Animation asset metadata. */
public final class AnimationAssetAuthoringService {
    private final Supplier<AssetMetaDatabase> databaseSupplier;
    private final Supplier<FileHandle> assetsFileSupplier;
    private final Consumer<AssetMetaDatabase> metadataPublisher;
    private final BiConsumer<AssetMetaDatabase, FileHandle> databaseSaver;

    public AnimationAssetAuthoringService(
            Supplier<AssetMetaDatabase> databaseSupplier,
            Supplier<FileHandle> assetsFileSupplier,
            Consumer<AssetMetaDatabase> metadataPublisher) {
        this(databaseSupplier, assetsFileSupplier, metadataPublisher, AssetMetaDatabase::save);
    }

    AnimationAssetAuthoringService(
            Supplier<AssetMetaDatabase> databaseSupplier,
            Supplier<FileHandle> assetsFileSupplier,
            Consumer<AssetMetaDatabase> metadataPublisher,
            BiConsumer<AssetMetaDatabase, FileHandle> databaseSaver) {
        this.databaseSupplier = Objects.requireNonNull(databaseSupplier, "databaseSupplier");
        this.assetsFileSupplier = Objects.requireNonNull(assetsFileSupplier, "assetsFileSupplier");
        this.metadataPublisher = Objects.requireNonNull(metadataPublisher, "metadataPublisher");
        this.databaseSaver = Objects.requireNonNull(databaseSaver, "databaseSaver");
    }

    public AnimationAssetMeta updateFps(int assetId, float fps) {
        if (!Float.isFinite(fps) || fps <= 0f) {
            throw new IllegalArgumentException(
                    "Animation FPS must be finite and greater than zero.");
        }

        AssetMetaDatabase database = databaseSupplier.get();
        if (database == null) {
            throw new IllegalStateException("Asset metadata is unavailable.");
        }
        FileHandle assetsFile = assetsFileSupplier.get();
        if (assetsFile == null) {
            throw new IllegalStateException("Asset metadata file is unavailable.");
        }

        AssetMeta meta = database.findById(assetId);
        if (!(meta instanceof AnimationAssetMeta animation)) {
            throw new IllegalStateException("Animation asset not found: " + assetId);
        }

        AnimationAssetMeta candidate = StudioAnimationAssets.copyOf(animation);
        candidate.fps = fps;
        StudioAnimationAssets.validate(candidate);

        float previousFps = animation.fps;
        animation.fps = fps;
        try {
            databaseSaver.accept(database, assetsFile);
        } catch (RuntimeException failure) {
            animation.fps = previousFps;
            throw failure;
        }
        metadataPublisher.accept(database);
        return animation;
    }
}
