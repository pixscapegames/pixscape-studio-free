package games.pixscape.studio.service.asset;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.animation.AnimationDefData;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;

public final class StudioAnimationAssets {
    private StudioAnimationAssets() {
    }

    public static AnimationRegistry buildRegistry(AssetMetaDatabase database) {
        AnimationRegistry registry = new AnimationRegistry();
        reloadRegistry(registry, database);
        return registry;
    }

    public static void reloadRegistry(AnimationRegistry registry, AssetMetaDatabase database) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        Array<AnimationDef> prepared = new Array<>();
        IntSet assetIds = new IntSet();
        ObjectSet<String> names = new ObjectSet<>();
        if (database != null) {
            for (int i = 0; i < database.size(); i++) {
                AssetMeta asset = database.assetAt(i);
                if (asset instanceof AnimationAssetMeta animation) {
                    AnimationDef definition = new AnimationDef(toRuntimeData(animation));
                    if (!assetIds.add(definition.assetId())) {
                        throw new IllegalArgumentException(
                                "Duplicate Animation asset id: " + definition.assetId());
                    }
                    if (!names.add(definition.name())) {
                        throw new IllegalArgumentException(
                                "Duplicate Animation name: " + definition.name());
                    }
                    prepared.add(definition);
                }
            }
        }
        registry.clear();
        for (AnimationDef definition : prepared) registry.put(definition);
    }

    public static AnimationDefData toRuntimeData(AnimationAssetMeta animation) {
        if (animation == null) throw new IllegalArgumentException("animation must not be null");
        AnimationDefData data = new AnimationDefData();
        data.assetId = animation.id();
        String baseName = RuntimeFs.baseName(animation.logicalPath());
        data.name = baseName != null && !baseName.isBlank()
                ? baseName
                : "animation_" + animation.id();
        data.fps = animation.fps;
        data.currentClip = animation.currentClip;
        data.frameCount = animation.frameCount;
        for (String clipName : orderedClipNames(animation)) {
            AnimationClipMeta clip = animation.clips.get(clipName);
            AnimationClipDefData clipData = new AnimationClipDefData();
            clipData.name = clipName;
            clipData.start = clip.start;
            clipData.end = clip.end;
            clipData.flipX = clip.flipX;
            data.clips.add(clipData);
        }
        return data;
    }

    public static String initialClip(AnimationAssetMeta animation) {
        if (animation == null || animation.clips == null || animation.clips.size == 0) return null;
        if (animation.currentClip != null
                && !animation.currentClip.isBlank()
                && animation.clips.get(animation.currentClip) != null) {
            return animation.currentClip;
        }
        Array<String> names = orderedClipNames(animation);
        return names.size > 0 ? names.first() : null;
    }

    public static Array<String> orderedClipNames(AnimationAssetMeta animation) {
        Array<String> names = new Array<>();
        if (animation == null || animation.clips == null) return names;
        for (String name : animation.clips.keys()) {
            if (name != null && !name.isBlank() && animation.clips.get(name) != null) {
                names.add(name);
            }
        }
        names.sort();
        return names;
    }
}
