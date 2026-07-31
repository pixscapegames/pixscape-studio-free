package games.pixscape.studio.service.asset;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;

import java.lang.reflect.Constructor;

public final class VisualResolverTestSupport {

    private VisualResolverTestSupport() {
    }

    public static Texture texture(int width, int height) {
        return new FakeTexture(width, height);
    }

    public static AtlasAssetBinding binding(int assetId,
                                            String group,
                                            Texture... textures) {
        try {
            Array<TextureAtlas.AtlasRegion> regions = new Array<>();
            for (int i = 0; i < textures.length; i++) {
                Texture texture = textures[i];
                TextureAtlas.AtlasRegion region = new TextureAtlas.AtlasRegion(
                        texture,
                        0,
                        0,
                        texture.getWidth(),
                        texture.getHeight()
                );
                region.name = group;
                region.index = textures.length == 1 ? -1 : i;
                regions.add(region);
            }

            TextureAtlas.AtlasRegion first = regions.first();
            Constructor<AtlasRegionMetadata> metadataConstructor =
                    AtlasRegionMetadata.class.getDeclaredConstructor(
                            String.class,
                            float.class,
                            float.class,
                            float.class,
                            float.class,
                            int.class,
                            int.class,
                            int.class
                    );
            metadataConstructor.setAccessible(true);
            AtlasRegionMetadata metadata = metadataConstructor.newInstance(
                    group,
                    first.getU(),
                    first.getV(),
                    first.getU2(),
                    first.getV2(),
                    TextureRegistry.handleOf(first.getTexture()),
                    first.getRegionWidth(),
                    first.getRegionHeight()
            );

            Constructor<AtlasAssetBinding> bindingConstructor =
                    AtlasAssetBinding.class.getDeclaredConstructor(
                            int.class,
                            String.class,
                            TextureAtlas.AtlasRegion.class,
                            Array.class,
                            AtlasRegionMetadata.class
                    );
            bindingConstructor.setAccessible(true);
            return bindingConstructor.newInstance(
                    assetId,
                    group,
                    first,
                    regions,
                    metadata
            );
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    public static final class TrackingAtlasService extends AtlasRuntimeService {
        private final String tag;
        private TextureAtlas atlas;
        private final IntMap<AtlasAssetBinding> bindings = new IntMap<>();
        public int resolveCalls;

        public TrackingAtlasService(String tag) {
            this.tag = tag;
        }

        public void publish(TextureAtlas atlas, AtlasAssetBinding... bindings) {
            this.atlas = atlas;
            this.bindings.clear();
            if (bindings == null) return;
            for (AtlasAssetBinding binding : bindings) {
                if (binding != null) {
                    this.bindings.put(binding.assetId(), binding);
                }
            }
        }

        @Override
        public TextureAtlas getAtlas(String requestedTag) {
            return tag.equals(requestedTag) ? atlas : null;
        }

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String requestedTag) {
            resolveCalls++;
            if (!tag.equals(requestedTag)) return null;
            return bindings.get(assetId);
        }
    }

    private static final class FakeTexture extends Texture {
        private final int width;
        private final int height;

        FakeTexture(int width, int height) {
            super();
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }
    }
}
