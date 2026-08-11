package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;

public final class SelectionService {

    public enum SelectionSource {
        VIEWPORT,
        TREE
    }

    private final World world;
    private final ComponentMapper<LayerComponent> mLayer;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<VisibilityComponent> mVis;

    // ✅ Joint base only (no type mapper here)
    private final ComponentMapper<PhysicsJointComponent> mJointBase;

    private final LayerService layerService;
    private final StudioEditingModeService studioEditingModeService;

    private final IntSet selection = new IntSet();
    private int firstSelectedEntityId = -1;

    private int activelayerId = 0;
    private int hoveredEntityId = -1;
    private final int MY_TAG = EventFlow.tag(this);

    public SelectionService(World world, LayerService layerService) {
        this(world, layerService, null);
    }

    public SelectionService(World world,
                            LayerService layerService,
                            StudioEditingModeService studioEditingModeService) {
        this.world = world;
        this.mLayer = world.getMapper(LayerComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mVis = world.getMapper(VisibilityComponent.class);

        this.mJointBase = world.getMapper(PhysicsJointComponent.class);

        this.layerService = layerService;
        this.studioEditingModeService = studioEditingModeService;
    }

    public int getActivelayerId() {
        return activelayerId;
    }

    public int getActiveLayerIndex() {
        if (!mLayer.has(activelayerId)) return 0;
        return mLayer.get(activelayerId).layerIndex;
    }

    public void setActivelayerId(int layer) {
        setActivelayerId(layer, SelectionSource.VIEWPORT);
    }

    public void setActivelayerId(int layer, SelectionSource source) {
        setActivelayerIdInternal(layer, false, source);
    }

    public void setActivelayerIdForPhysicsContext(int layer) {
        setActivelayerIdForPhysicsContext(layer, SelectionSource.VIEWPORT);
    }

    public void setActivelayerIdForPhysicsContext(int layer, SelectionSource source) {
        setActivelayerIdInternal(layer, true, source);
    }

    private void setActivelayerIdInternal(int layer, boolean forceEntityMode, SelectionSource source) {
        this.activelayerId = layer;

        int type = layerService.getLayerTypeByEntity(layer);
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg.getCurrentSceneMeta();

        if (!forceEntityMode && type == LayerComponent.TYPE_TILED) {
            meta.editorMode = SceneMeta.EditorMode.TILE;
        } else {
            meta.editorMode = SceneMeta.EditorMode.ENTITY;
        }

        EventFlow.i().publish(new EventFlow.CurrentLayerChanged(layer, source, MY_TAG));

        int layerType = layerService.getLayerTypeByEntity(layer);

        boolean isTiled = !forceEntityMode && layerType == LayerComponent.TYPE_TILED;

        if (studioEditingModeService != null) {
            studioEditingModeService.setModeActive(StudioEditingMode.TILED, isTiled, MY_TAG);
        }

        EventFlow.i().publish(
                new EventFlow.EditorModeChanged(
                        isTiled
                                ? EventFlow.EditorMode.TILE
                                : EventFlow.EditorMode.ENTITY,
                        EventFlow.tag(this)
                )
        );
    }

    public IntSet getSelectionSet() {
        return selection;
    }

    public int getFirstSelectedEntityId() {
        return firstSelectedEntityId;
    }

    public int getHoveredEntityId() {
        return hoveredEntityId;
    }

    public void setHoveredEntityId(int entityId) {
        this.hoveredEntityId = entityId;
    }

    public void clearHoveredEntity() {
        this.hoveredEntityId = -1;
    }

    public IntArray getSelectionSnapshot() {
        IntArray out = new IntArray(selection.size);
        for (IntSet.IntSetIterator it = selection.iterator(); it.hasNext; ) out.add(it.next());
        return out;
    }

    public void clearSelection() {
        clearSelection(SelectionSource.VIEWPORT);
    }

    public void clearSelection(SelectionSource source) {
        selection.clear();
        firstSelectedEntityId = -1;
        publish(source);
    }

    public void selectAdd(int e) {
        selectAdd(e, SelectionSource.VIEWPORT);
    }

    public void selectAdd(int e, SelectionSource source) {
        if (!isEntityActive(e)) return;
        if (!passesGatesForSelection(e, source)) return;

        boolean wasEmpty = selection.size == 0;
        if (selection.add(e)) {
            if (wasEmpty || firstSelectedEntityId == -1) {
                firstSelectedEntityId = e;
            }
            publish(source);
        }
    }

    public void selectOnly(int e) {
        selectOnly(e, SelectionSource.VIEWPORT);
    }

    public void selectOnly(int e, SelectionSource source) {
        if (!isEntityActive(e)) {
            clearSelection(source);
            return;
        }
        if (!passesGatesForSelection(e, source)) {
            if (source == SelectionSource.VIEWPORT) clearSelection(source);
            return;
        }
        selection.clear();
        selection.add(e);
        firstSelectedEntityId = e;
        publish(source);
    }

    public void toggle(int e) {
        toggle(e, SelectionSource.VIEWPORT);
    }

    public void toggle(int e, SelectionSource source) {
        if (!isEntityActive(e)) return;
        if (!passesGatesForSelection(e, source)) return;

        if (selection.contains(e)) {
            selection.remove(e);
            if (firstSelectedEntityId == e) {
                firstSelectedEntityId = firstRemainingSelectedEntityId();
            }
        } else {
            boolean wasEmpty = selection.size == 0;
            selection.add(e);
            if (wasEmpty || firstSelectedEntityId == -1) {
                firstSelectedEntityId = e;
            }
        }
        publish(source);
    }

    private int firstRemainingSelectedEntityId() {
        for (IntSet.IntSetIterator it = selection.iterator(); it.hasNext; ) {
            return it.next();
        }
        return -1;
    }

    public int getValidFirstSelectedEntityId() {
        if (firstSelectedEntityId != -1 && selection.contains(firstSelectedEntityId)) {
            return firstSelectedEntityId;
        }
        firstSelectedEntityId = firstRemainingSelectedEntityId();
        return firstSelectedEntityId;
    }

    public void selectFromTree(int e) {
        selectAdd(e, SelectionSource.TREE);
    }

    public void deleteSelection() {
        for (IntSet.IntSetIterator it = selection.iterator(); it.hasNext; ) {
            world.delete(it.next());
        }
        selection.clear();
        firstSelectedEntityId = -1;
        publish(SelectionSource.VIEWPORT);
    }

    // ------------------------------------------------------------------------
    // Viewport gates
    // ------------------------------------------------------------------------

    public boolean isSelectableInViewport(int e) {
        //  JOINT: gates derived from its bodies (no Visibility/EntityIndex required on joint entity)
        if (mJointBase.has(e)) return isJointSelectableInViewport(e);

        // Default: tolerate entities without explicit VisibilityComponent
        // (e.g. tiled collision bodies created as layer children).
        if (mVis.has(e) && !mVis.get(e).isVisible()) return false;
        return isLayerVisible(e);
    }

    private boolean passesGatesForSelection(int e, SelectionSource source) {
        // Layer lock gate must also work for joints (derived)
        if (isLayerLocked(e)) return false;

        if (source == SelectionSource.TREE) {
            return true;
        }
        return isSelectableInViewport(e);
    }

    // ------------------------------------------------------------------------
    // JOINT selection rules (base only)
    // ------------------------------------------------------------------------

    private boolean isJointSelectableInViewport(int jointEid) {
        PhysicsJointComponent j = mJointBase.getSafe(jointEid, null);
        if (j == null) return false;

        int a = j.aEid;
        int b = j.bEid;

        // strict: joint selectable only if A and B are active
        // (vu ton invariant, c’est logique)
        if (!isEntityActive(a)) return false;
        if (!isEntityActive(b)) return false;

        // Visibility: joint visible if at least one body is visible + layer visible
        boolean aVisible = isBodyVisibleForJoint(a);
        boolean bVisible = isBodyVisibleForJoint(b);

        return aVisible || bVisible;
    }

    private boolean isBodyVisibleForJoint(int bodyEid) {
        if (!mVis.has(bodyEid) || !mVis.get(bodyEid).isVisible()) return false;
        return isLayerVisible(bodyEid);
    }

    // ------------------------------------------------------------------------
    // Layer visibility / lock
    // ------------------------------------------------------------------------

    private boolean isLayerVisible(int e) {
        int layerEntityId = resolveLayerEntityId(e);
        if (layerEntityId == -1) return true;

        VisibilityComponent layerVis = mVis.getSafe(layerEntityId, null);
        return layerVis == null || layerVis.isVisible();
    }

    private int resolveLayerEntityId(int e) {
        if (mEntityIndex.has(e)) {
            int layerIndex = mEntityIndex.get(e).getLayerIndex();
            if (layerService != null) return layerService.getLayerEntity(layerIndex);
        }
        return -1;
    }

    private boolean isLayerLocked(int e) {
        if (layerService == null) return false;

        // JOINT: locked if A or B is in a locked layer
        if (mJointBase.has(e)) {
            PhysicsJointComponent j = mJointBase.getSafe(e, null);
            if (j == null) return false;

            int a = j.aEid;
            int b = j.bEid;

            return isBodyLayerLocked(a) || isBodyLayerLocked(b);
        }

        // original entity rules
        if (mEntityIndex.has(e)) {
            int layerIndex = mEntityIndex.get(e).getLayerIndex();
            LayerMetaComponent meta = layerService.meta(layerIndex);
            return meta != null && meta.locked;
        }

        if (mLayer.has(e)) {
            int layerIndex = mLayer.get(e).layerIndex;
            LayerMetaComponent meta = layerService.meta(layerIndex);
            return meta != null && meta.locked;
        }

        return false;
    }

    private boolean isBodyLayerLocked(int e) {
        if (!isEntityActive(e)) return false;

        if (mEntityIndex.has(e)) {
            int layerIndex = mEntityIndex.get(e).getLayerIndex();
            LayerMetaComponent meta = layerService.meta(layerIndex);
            return meta != null && meta.locked;
        }

        if (mLayer.has(e)) {
            int layerIndex = mLayer.get(e).layerIndex;
            LayerMetaComponent meta = layerService.meta(layerIndex);
            return meta != null && meta.locked;
        }
        return false;
    }

    private boolean isEntityActive(int e) {
        return e >= 0 && world.getEntityManager().isActive(e);
    }

    private void publish(SelectionSource source) {
        EventFlow.i().publish(
                new EventFlow.SelectionChanged(
                        getSelectionSnapshot(),
                        getValidFirstSelectedEntityId(),
                        source,
                        MY_TAG
                )
        );
    }
}
