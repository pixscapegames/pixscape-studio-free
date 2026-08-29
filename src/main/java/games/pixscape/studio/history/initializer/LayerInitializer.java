package games.pixscape.studio.history.initializer;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

/**
 * Initializer specialized for layer entities.
 */
public final class LayerInitializer extends AbstractCommonInitializer {

    private boolean hasLayerIndex;
    private int layerIndex;

    private boolean hasLayerMeta;
    private String layerName = "Layer";
    private String layerDescription = "";
    private boolean layerLocked = false;

    private boolean hasParallax;
    private float parallaxX = 1f;
    private float parallaxY = 1f;

    // --- LayerComponent ---
    private boolean hasLayerType;
    private int layerType = LayerComponent.TYPE_CLASSIC;
    private boolean layerSpatialEnabled = false;

    private final TiledAllocatorService tiledAllocatorService;
    private TiledMapInitializer tiledMapInitializer;

    public LayerInitializer(World world,
                            TiledAllocatorService tiledAllocatorService) {
        super(world);
        this.tiledAllocatorService = tiledAllocatorService;
    }

    @Override
    public void syncFrom(int e) {
        super.syncFrom(e);

        ComponentMapper<LayerComponent> mLayer = world.getMapper(LayerComponent.class);
        ComponentMapper<LayerMetaComponent> mMeta = world.getMapper(LayerMetaComponent.class);
        ComponentMapper<LayerParallaxComponent> mPar = world.getMapper(LayerParallaxComponent.class);

        if (mLayer.has(e)) {
            LayerComponent li = mLayer.get(e);
            hasLayerIndex = true;
            layerIndex = li.layerIndex;
            hasLayerType = true;
            layerType = li.type;
            layerSpatialEnabled = li.spatialEnabled;
        } else {
            hasLayerIndex = false;
            hasLayerType = false;
            layerSpatialEnabled = false;
        }

        if (mMeta.has(e)) {
            LayerMetaComponent meta = mMeta.get(e);
            hasLayerMeta = true;
            layerName = meta.name;
            layerDescription = meta.description;
            layerLocked = meta.locked;
        } else {
            hasLayerMeta = false;
        }

        if (mPar.has(e)) {
            LayerParallaxComponent par = mPar.get(e);
            hasParallax = true;
            parallaxX = par.factorX;
            parallaxY = par.factorY;
        } else {
            hasParallax = false;
        }

    }

    @Override
    public void init(int e) {
        super.init(e);

        ComponentMapper<LayerComponent> mLayer = world.getMapper(LayerComponent.class);
        ComponentMapper<LayerMetaComponent> mMeta = world.getMapper(LayerMetaComponent.class);
        ComponentMapper<LayerParallaxComponent> mPar = world.getMapper(LayerParallaxComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        if (hasLayerIndex) {
            LayerComponent li = mLayer.has(e) ? mLayer.get(e) : mLayer.create(e);
            li.layerIndex = layerIndex;
            if (hasLayerType) {
                li.type = layerType;
            } else {
                li.type = LayerComponent.TYPE_CLASSIC;
            }
            li.spatialEnabled = layerSpatialEnabled;
            if (dirty != null) {
                dirty.layer(e);
                dirty.order(e);
            }
        }

        if (hasLayerMeta) {
            LayerMetaComponent meta = mMeta.has(e) ? mMeta.get(e) : mMeta.create(e);
            meta.name = layerName;
            meta.description = layerDescription;
            meta.locked = layerLocked;
        }

        if (hasParallax) {
            LayerParallaxComponent par = mPar.has(e) ? mPar.get(e) : mPar.create(e);
            par.factorX = parallaxX;
            par.factorY = parallaxY;
        }
    }

    @Override
    public String label() {
        return "Layer";
    }

    public LayerInitializer configureNewLayer(String name, int index) {
        return configureLayerDefaults(name, index, LayerComponent.TYPE_CLASSIC);
    }

    private LayerInitializer configureLayerDefaults(String name, int index, int type) {
        hasLayerIndex = true;
        layerIndex = index;

        hasMeta = true;
        metaKind = EntityKind.LAYER;

        hasLayerMeta = true;
        layerName = name;
        layerDescription = "";
        layerLocked = false;

        hasVisibility = true;
        visible = true;

        hasParallax = false;

        hasLayerType = true;
        layerType = type;
        layerSpatialEnabled = false;

        return this;
    }

    public LayerInitializer configureNewTiledLayer(
            String name,
            int index,
            int width,
            int height
    ) {
        configureLayerDefaults(name, index, LayerComponent.TYPE_TILED);

        ProjectConfig cfg = ProjectConfig.getInstance();
        String atlasTag = cfg != null ? cfg.canonicalSceneTagCurrent() : "main";
        tiledMapInitializer = new TiledMapInitializer(world, tiledAllocatorService)
                .configureNew(index, width, height, atlasTag);

        return this;
    }

    public TiledMapInitializer tiledMapInitializer() {
        return tiledMapInitializer;
    }

    public void setTiledMapInitializer(TiledMapInitializer tiledMapInitializer) {
        this.tiledMapInitializer = tiledMapInitializer;
    }
}
