package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.UiBinders;

public final class TiledMapProperties extends VisTable {

    private final World world;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final Runnable markCurrentSceneSaveRequired;

    private final VisLabel tiledWidthValue = new VisLabel();
    private final VisLabel tiledHeightValue = new VisLabel();
    private final VisLabel tiledProjectionValue = new VisLabel();
    private final VisLabel tiledTileWidthValue = new VisLabel();
    private final VisLabel tiledTileHeightValue = new VisLabel();

    private final IntSpinnerModel originXModel = new IntSpinnerModel(0, -100000, 100000, 1);
    private final IntSpinnerModel originYModel = new IntSpinnerModel(0, -100000, 100000, 1);

    private final Spinner tiledOriginXSpinner;
    private final Spinner tiledOriginYSpinner;

    private final UiBinders.IntSpinnerBinder tiledOriginXBinder;
    private final UiBinders.IntSpinnerBinder tiledOriginYBinder;

    public TiledMapProperties(World world, Runnable markCurrentSceneSaveRequired) {
        super(true);
        this.world = world;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;
        this.mTiled = world.getMapper(TiledLayerComponent.class);

        top().left();
        defaults().left().top().pad(1);

        tiledOriginXSpinner = new Spinner("", originXModel);
        tiledOriginXSpinner.getTextField().setTouchable(Touchable.disabled);

        tiledOriginYSpinner = new Spinner("", originYModel);
        tiledOriginYSpinner.getTextField().setTouchable(Touchable.disabled);

        tiledOriginXBinder = new UiBinders.IntSpinnerBinder(
                world,
                tiledOriginXSpinner,
                originXModel,
                mTiled::has,
                eid -> Math.round(mTiled.get(eid).originX),
                (eid, value) -> {
                    TiledLayerComponent t = mTiled.get(eid);
                    float v = value;
                    t.originX = v;
                    t.data.originX = v;
                    t.data.rebuildWithNewSize(t.mapWidthCells, t.mapHeightCells);
                    world.getSystem(DirtyTrackerSystem.class).layer(eid);
                    flagPreviewSaveRequired();
                }
        );

        tiledOriginYBinder = new UiBinders.IntSpinnerBinder(
                world,
                tiledOriginYSpinner,
                originYModel,
                mTiled::has,
                eid -> Math.round(mTiled.get(eid).originY),
                (eid, value) -> {
                    TiledLayerComponent t = mTiled.get(eid);
                    float v = value;
                    t.originY = v;
                    t.data.originY = v;
                    t.data.rebuildWithNewSize(t.mapWidthCells, t.mapHeightCells);
                    world.getSystem(DirtyTrackerSystem.class).layer(eid);
                    flagPreviewSaveRequired();
                }
        );

        add(new VisLabel("TILED MAP"))
                .colspan(2)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .row();

        add(new VisLabel("Projection:")).left();
        add(tiledProjectionValue).left().growX().row();

        add(new VisLabel("Cell Width:")).left();
        add(tiledTileWidthValue).left().row();

        add(new VisLabel("Cell Height:")).left();
        add(tiledTileHeightValue).left().row();

        add(new VisLabel("Origin X:")).left();
        add(tiledOriginXSpinner).width(80).left().growX().row();

        add(new VisLabel("Origin Y:")).left();
        add(tiledOriginYSpinner).width(80).left().growX().row();

        add(new VisLabel("Width (cells):")).left();
        add(tiledWidthValue).left().row();

        add(new VisLabel("Height (cells):")).left();
        add(tiledHeightValue).left().row();
    }

    public void setMapEntityId(int mapEntityId) {
        TiledLayerComponent t = mTiled.getSafe(mapEntityId, null);
        if (t != null) {
            tiledWidthValue.setText(String.valueOf(t.mapWidthCells));
            tiledHeightValue.setText(String.valueOf(t.mapHeightCells));

            tiledProjectionValue.setText(buildTiledProjectionLabel(t));
            tiledTileWidthValue.setText(Integer.toString(t.tileWidth));
            tiledTileHeightValue.setText(Integer.toString(t.tileHeight));

            originXModel.setStep(Math.max(1, t.tileWidth));
            originYModel.setStep(Math.max(1, t.tileHeight));

            tiledOriginXBinder.setEntityId(mapEntityId);
            tiledOriginYBinder.setEntityId(mapEntityId);
        } else {
            tiledWidthValue.setText("?");
            tiledHeightValue.setText("?");
            tiledProjectionValue.setText("Unknown");
            tiledTileWidthValue.setText("?");
            tiledTileHeightValue.setText("?");

            originXModel.setStep(1);
            originYModel.setStep(1);

            tiledOriginXBinder.setEntityId(-1);
            tiledOriginYBinder.setEntityId(-1);
        }

        invalidateHierarchy();
    }

    private void flagPreviewSaveRequired() {
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    private String buildTiledProjectionLabel(TiledLayerComponent tiled) {
        if (tiled == null || tiled.projection == null) {
            return "Unknown";
        }

        return switch (tiled.projection) {
            case ISO -> "Isometric";
            case ORTHO -> "Orthogonal";
        };
    }
}
