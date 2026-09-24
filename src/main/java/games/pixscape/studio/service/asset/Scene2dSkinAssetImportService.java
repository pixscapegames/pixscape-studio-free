package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.runtimeavailability.SceneHudDependencyClosure;
import games.pixscape.studio.service.runtimeavailability.SceneHudDependencyCollector;

/** Validates and atomically imports one native Scene2D Skin bundle. */
public final class Scene2dSkinAssetImportService {
    private final AssetMetaDatabase database;

    public Scene2dSkinAssetImportService(AssetMetaDatabase database) {
        if (database == null) throw new IllegalArgumentException("Asset database is required.");
        this.database = database;
    }

    public AssetMeta importNew(FileHandle descriptor, FileHandle projectDir) {
        SkinBundle bundle = validate(descriptor);
        FileHandle skinsRoot = projectDir.child(StudioFs.DIR_ORIG_SKINS);
        skinsRoot.mkdirs();
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(skinsRoot);
        AssetMeta created = null;
        try {
            copyBundle(bundle, candidate, descriptor.name());
            created = database.registerIfAbsent(AssetType.SKIN,
                    uniqueLogicalPath(descriptor.nameWithoutExtension()), null,
                    AssetMeta.AssetScope.USER);
            FileHandle target = skinsRoot.child(Integer.toString(created.id()));
            AtomicDirectoryPublication.move(candidate, target);
            database.updateSourceRelPath(created.id(), StudioFs.DIR_ORIG_SKINS + "/"
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
        if (asset == null || asset.type() != AssetType.SKIN) {
            throw new IllegalArgumentException("Skin Asset " + assetId + " does not exist.");
        }
        SkinBundle bundle = validate(descriptor);
        FileHandle skinsRoot = projectDir.child(StudioFs.DIR_ORIG_SKINS);
        FileHandle target = skinsRoot.child(Integer.toString(assetId));
        String storedDescriptorName = new FileHandle(asset.sourceRelPath()).name();
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(target);
        try {
            copyBundle(bundle, candidate, storedDescriptorName);
            try (AtomicDirectoryPublication.Published publication =
                         AtomicDirectoryPublication.publish(candidate, target)) {
                publication.commit();
            }
            return database.findById(assetId);
        } finally {
            if (candidate.exists()) AtomicDirectoryPublication.discardCandidate(candidate);
        }
    }

    private String uniqueLogicalPath(String base) {
        String initial = StudioFs.PREFIX_SKINS + base;
        if (database.findByLogicalPath(initial) == null) return initial;
        for (int suffix = database.nextId(); ; suffix++) {
            String candidate = initial + "-" + suffix;
            if (database.findByLogicalPath(candidate) == null) return candidate;
        }
    }

    private static SkinBundle validate(FileHandle descriptor) {
        if (descriptor == null || !descriptor.exists() || descriptor.isDirectory()
                || !"json".equalsIgnoreCase(descriptor.extension())) {
            throw new IllegalArgumentException("A readable Scene2D Skin JSON file is required.");
        }
        JsonValue json;
        try {
            json = new JsonReader().parse(descriptor);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid Skin JSON '" + descriptor.name() + "'.", failure);
        }
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("Skin JSON '" + descriptor.name() + "' must be an object.");
        }
        validateNativeSections(json, descriptor.name());
        SceneHudDependencyClosure.SkinDependency dependency;
        try {
            dependency = SceneHudDependencyCollector.inspectSkinBundle(
                    descriptor.parent(), descriptor.name());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Skin bundle '" + descriptor.name()
                    + "' is incomplete or incompatible: " + message(failure), failure);
        }
        validateAtlasPolicy(descriptor);
        validateNativeLoad(descriptor);
        return new SkinBundle(descriptor.parent(), dependency);
    }

    private static void validateAtlasPolicy(FileHandle descriptor) {
        FileHandle atlasFile = descriptor.sibling(descriptor.nameWithoutExtension() + ".atlas");
        if (!atlasFile.exists()) return;
        TextureAtlas.TextureAtlasData data;
        try {
            data = new TextureAtlas.TextureAtlasData(atlasFile, atlasFile.parent(), false);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Skin atlas '" + atlasFile.name()
                    + "' is invalid: " + message(failure), failure);
        }
        for (TextureAtlas.TextureAtlasData.Page page : data.getPages()) {
            if (page.pma) {
                throw new IllegalArgumentException("Skin atlas page '" + page.textureFile.name()
                        + "' uses premultiplied alpha; HUD packing requires pma: false.");
            }
        }
    }

    private static void validateNativeSections(JsonValue json, String name) {
        Skin tagSource = new Skin();
        try {
            ObjectMap<String, Class> tags = tagSource.getJsonClassTags();
            for (JsonValue section = json.child; section != null; section = section.next) {
                if (!isSupportedNativeSection(section.name, tags)) {
                    throw new IllegalArgumentException("Skin '" + name + "' declares unsupported class '"
                            + section.name + "'; only native Scene2D HUD resources are importable.");
                }
            }
        } finally {
            tagSource.dispose();
        }
    }

    private static boolean isSupportedNativeSection(String declaredType,
                                                    ObjectMap<String, Class> tags) {
        Class<?> taggedType = tags.get(declaredType);
        if (isSupportedHudSkinType(taggedType)) return true;
        for (ObjectMap.Entry<String, Class> entry : tags) {
            if (entry.value.getName().equals(declaredType)) {
                return isSupportedHudSkinType(entry.value);
            }
        }
        return false;
    }

    private static boolean isSupportedHudSkinType(Class<?> type) {
        return type == Color.class
                || type == BitmapFont.class
                || type == Skin.TintedDrawable.class
                || type == NinePatchDrawable.class
                || type == SpriteDrawable.class
                || type == TextureRegionDrawable.class
                || type == TiledDrawable.class
                || type == Label.LabelStyle.class
                || type == TextButton.TextButtonStyle.class
                || type == ImageButton.ImageButtonStyle.class
                || type == ImageTextButton.ImageTextButtonStyle.class
                || type == TextField.TextFieldStyle.class
                || type == SelectBox.SelectBoxStyle.class
                || type == CheckBox.CheckBoxStyle.class
                || type == Slider.SliderStyle.class
                || type == ProgressBar.ProgressBarStyle.class
                || type == List.ListStyle.class
                || type == ScrollPane.ScrollPaneStyle.class
                || type == TextTooltip.TextTooltipStyle.class
                || type == Window.WindowStyle.class;
    }

    private static void validateNativeLoad(FileHandle descriptor) {
        FileHandle atlasFile = descriptor.sibling(descriptor.nameWithoutExtension() + ".atlas");
        Skin skin = null;
        try {
            if (atlasFile.exists()) {
                skin = new Skin(new TextureAtlas(atlasFile));
            } else {
                skin = new Skin();
            }
            skin.load(descriptor);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Skin '" + descriptor.name()
                    + "' cannot be loaded by Scene2D: " + message(failure), failure);
        } finally {
            if (skin != null) skin.dispose();
        }
    }

    private static void copyBundle(SkinBundle bundle, FileHandle candidate,
                                   String targetDescriptorName) {
        String sourceDescriptorName = bundle.dependency.skinId();
        String sourceBase = new FileHandle(sourceDescriptorName).nameWithoutExtension();
        String targetBase = new FileHandle(targetDescriptorName).nameWithoutExtension();
        for (SceneHudDependencyClosure.FileDependency file : bundle.dependency.files()) {
            FileHandle source = bundle.root.child(file.projectRelativePath());
            String relative = file.projectRelativePath();
            if (relative.equals(sourceDescriptorName)) {
                relative = targetDescriptorName;
            } else if (relative.equals(sourceBase + ".atlas")) {
                relative = targetBase + ".atlas";
            }
            FileHandle target = candidate.child(relative);
            target.parent().mkdirs();
            source.copyTo(target);
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null && !failure.getMessage().isBlank()
                ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    private record SkinBundle(FileHandle root,
                              SceneHudDependencyClosure.SkinDependency dependency) { }
}
