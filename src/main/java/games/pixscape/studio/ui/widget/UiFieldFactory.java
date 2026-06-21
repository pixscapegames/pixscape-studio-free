package games.pixscape.studio.ui.widget;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.studio.component.CameraMetaComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;

public final class UiFieldFactory {
    private final World world;
    private final ComponentMapper<LayerMetaComponent> mLayerM;
    private final ComponentMapper<EntityMetaComponent> mEntityM;
    private final ComponentMapper<CameraMetaComponent> mCameraM;

    public UiFieldFactory(World world) {
        this.world = world;
        this.mLayerM = world.getMapper(LayerMetaComponent.class);
        this.mEntityM = world.getMapper(EntityMetaComponent.class);
        this.mCameraM = world.getMapper(CameraMetaComponent.class);

    }

    public TextField layerName() {
        TextField f = new TextField(
                world,
                (int e) -> {
                    LayerMetaComponent ui = mLayerM.get(e);
                    return ui != null ? (ui.name != null ? ui.name : "") : "";
                },
                (int e) -> true,                 // active even if component is missing
                true,
                TextField.nonEmpty(),
                TextField.safeNameFilter()
        );
        f.setApplier((eid, v) -> {
            LayerMetaComponent ui = mLayerM.has(eid) ? mLayerM.get(eid) : mLayerM.create(eid);
            ui.name = v;
        });
        return f;
    }

    public TextField layerDescription() {
        TextField f = new TextField(
                world,
                (int e) -> {
                    LayerMetaComponent ui = mLayerM.get(e);
                    return ui != null ? (ui.description != null ? ui.description : "") : "";
                },
                (int e) -> true,                 // create when missing
                true,
                TextField.acceptAll(),
                null
        );
        f.setApplier((eid, v) -> {
            LayerMetaComponent ui = mLayerM.has(eid) ? mLayerM.get(eid) : mLayerM.create(eid);
            ui.description = v;
        });
        return f;
    }

    public BoundTextArea entityNote() {
        BoundTextArea a = new BoundTextArea(
                world,
                (int e) -> {
                    EntityMetaComponent ui = mEntityM.get(e);
                    return ui != null ? (ui.note != null ? ui.note : "") : "";
                },
                (int e) -> true
        );

        a.setApplier((eid, v) -> {
            EntityMetaComponent ui = mEntityM.has(eid) ? mEntityM.get(eid) : mEntityM.create(eid);
            ui.note = v;
        });

        // optional
        a.setMaxLength(256);
        a.setTrimOnCommit(false); // preserve whitespace/new lines
        a.onFocusLostCommit().onEscapeRollback();

        return a;
    }


    public TextField cameraName() {
        TextField f = new TextField(
                world,
                (int e) -> {
                    CameraMetaComponent ui = mCameraM.get(e);
                    return ui != null ? (ui.name != null ? ui.name : "") : "";
                },
                (int e) -> true,
                true,
                TextField.nonEmpty(),
                TextField.safeNameFilter()
        );
        f.setApplier((eid, v) -> {
            CameraMetaComponent ui = mCameraM.has(eid) ? mCameraM.get(eid) : mCameraM.create(eid);
            ui.name = v;
        });
        return f;
    }
}

