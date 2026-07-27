package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.BlockPhysicsBindingRepository;
import games.pixscape.runtime.service.IdentityRegistry;

public final class SceneSaveTestSupport {
    private SceneSaveTestSupport() {
    }

    public static void save(
            World world, FileHandle file, SceneMetaRuntime sceneMeta) {
        IdentityRegistry identities = new IdentityRegistry();
        BlockPhysicsBindingRepository bindings =
                new BlockPhysicsBindingRepository();
        identities.bind(world, sceneMeta);
        bindings.bind(world, identities);
        try {
            identities.rebuild();
            SceneService.saveScene(world, file, false, sceneMeta, bindings);
        } finally {
            bindings.clear();
            identities.bind(null, null);
        }
    }
}
