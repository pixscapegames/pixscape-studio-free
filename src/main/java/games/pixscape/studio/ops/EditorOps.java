package games.pixscape.studio.ops;

import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.service.spatial.SpatialBlockPlacementTarget;

public interface EditorOps {
    interface AssetsChangedListener {
        void onSceneAtlasChanged(String sceneName);
    }

    default int createSpriteFromAtlas(String atlasTag, String regionPath, float worldX, float worldY) {
        return createSpriteFromAtlas(atlasTag, regionPath, worldX, worldY, null);
    }

    int createSpriteFromAtlas(String atlasTag, String regionPath, float worldX, float worldY, String metaName);

    default int createStandaloneSprite(String relativePath, float worldX, float worldY) {
        return createStandaloneSprite(relativePath, worldX, worldY, null);
    }

    int createStandaloneSprite(String relativePath, float worldX, float worldY, String metaName);

    default int createAnimationSprite(String animationsRelPath, float worldX, float worldY) {
        return createAnimationSprite(animationsRelPath, worldX, worldY, null);
    }

    int createAnimationSprite(String animationsRelPath, float worldX, float worldY, String metaName);

    default int createParticleEffect(String effectPath, float worldX, float worldY) {
        return createParticleEffect(effectPath, worldX, worldY, null);
    }

    int createParticleEffect(String effectPath, float worldX, float worldY, String metaName);

    int createPointLight(float worldX, float worldY);

    int createConeLight(float worldX, float worldY);

    int createJoint(int type, int aEntityId, int bEntityId, float worldX, float worldY);

    int createGearJoint(int joint1EntityId, int joint2EntityId);

    void deleteJoint(int jointEntityId);

    void deleteFixture(int bodyEid, int physicsShapeId);

    void addBoxFixture(int bodyEid, float worldX, float worldY);

    void addCircleFixture(int bodyEid, float worldX, float worldY);

    void beginAddPolygonFixture(int bodyEid);

    void beginEditPolygonFixture(int bodyEid, int physicsShapeId);

    void addSpatialBlock(int layerEntityId, float worldX, float worldY);

    void addSpatialBlock(int layerEntityId, SpatialBlockPlacementTarget target);

    void createSpatialBlockFromSelectedTiles();

    void clearSpatialTileSelection();

    void deleteSelectedSpatialBlock();

    default void setAssetsChangedListener(AssetsChangedListener listener) {
    }

    void deleteEntities(IntArray entities);

    void applyTransform(IntArray entities,
                        Float x, Float y, Float dx, Float dy,
                        Float rotRad, Float scaleX, Float scaleY,
                        Float originX, Float originY);

    void applyZIndex(IntArray entities, Integer set, Integer dz);

    void applyLayer(IntArray entities, int layerEntityId);
}
