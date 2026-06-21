package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.AnimationFieldFactory;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

public final class AnimationPanel extends CollapsibleWidget {

    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);

    private final VisSelectBox<String> clipBox = new VisSelectBox<>();
    private final FloatField fpsField;

    private final VisCheckBox playingBox = new VisCheckBox("Playing");
    private final VisCheckBox loopBox = new VisCheckBox("Loop");
    private final VisCheckBox flipBox = new VisCheckBox("Flip");
    private final VisTextButton editClipsBtn = new VisTextButton("Edit clips…");

    private final UiBinders.SelectBoxBinder<String> clipBinder;
    private final UiBinders.CheckBoxBinder playingBinder;
    private final UiBinders.CheckBoxBinder loopBinder;
    private final UiBinders.CheckBoxBinder flipBinder;

    private int entityId = -1;

    public AnimationPanel(EntityPropertiesContext ctx) {
        super();
        editClipsBtn.setColor(CommonLayout.BUTTON_COLOR);
        this.ctx = ctx;

        setTable(root);
        root.left().top().pad(5);
        root.defaults().top().left();

        AnimationFieldFactory animFactory = new AnimationFieldFactory(ctx.world);
        fpsField = animFactory.fps();

        playingBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                playingBox,
                ctx.mAnim::has,
                (int e) -> ctx.mAnim.get(e).playing,
                (Integer e, Boolean v) -> {
                    var a = ctx.mAnim.get(e);
                    if (a != null) a.playing = v;
                }
        );
        loopBinder = animFactory.loopBinder(loopBox);

        flipBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                flipBox,
                ctx.mAnim::has,
                (int e) -> {
                    var a = ctx.mAnim.get(e);
                    if (a == null) return false;
                    var c = a.getClip();
                    return c != null && c.flipX;
                },
                (Integer e, Boolean v) -> {
                    var a = ctx.mAnim.get(e);
                    if (a == null) return;
                    var c = a.getClip();
                    if (c == null) return;

                    c.flipX = Boolean.TRUE.equals(v);
                    a.stateTime = 0f;
                    a.frame = -1;
                }
        );

        clipBinder = animFactory.currentClipBinder(clipBox);

        editClipsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (entityId < 0) return;
                if (!ctx.mAnim.has(entityId)) return;

                AnimationComponent a = ctx.mAnim.get(entityId);
                if (a == null) return;

                AnimationClipsDialog dlg = new AnimationClipsDialog(a, () -> {
                    if (entityId < 0 || !ctx.mAnim.has(entityId)) return;

                    refreshClipItems(entityId);

                    AnimationComponent aa = ctx.mAnim.get(entityId);
                    if (aa != null && aa.currentClip != null && !aa.currentClip.isEmpty()) {
                        clipBox.setSelected(aa.currentClip);
                    }
                });
                dlg.show(getStage());
            }
        });

        root.add(new VisLabel("Clip:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(clipBox).width(CommonLayout.FIELD_WIDTH).left();
        root.add(editClipsBtn).left().row();

        root.add(new VisLabel("FPS:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(fpsField).width(CommonLayout.FIELD_WIDTH).left();
        root.add(new VisLabel("")).expandX().row();

        root.add(playingBox).left().colspan(3).row();
        root.add(loopBox).left().colspan(3).row();
        root.add(flipBox).left().colspan(3).row();
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        if (entityId < 0) return;

        fpsField.setEntityId(entityId);
        playingBinder.setEntityId(entityId);
        loopBinder.setEntityId(entityId);
        flipBinder.setEntityId(entityId);

        if (ctx.mAnim.has(entityId)) refreshClipItems(entityId);
        clipBinder.setEntityId(entityId);
    }

    private void refreshClipItems(int eid) {
        if (!ctx.mAnim.has(eid)) {
            clipBox.setItems();
            clipBox.setSelected(null);
            return;
        }

        AnimationComponent a = ctx.mAnim.get(eid);

        Array<String> items = new Array<>();
        if (a.clips != null) {
            for (var it = a.clips.keys(); it.hasNext; ) items.add(it.next());
            items.sort();
        }
        if (items.size == 0) items.add("default");

        clipBox.setItems(items);

        boolean ok = a.currentClip != null
                && !a.currentClip.isEmpty()
                && a.clips != null
                && a.clips.containsKey(a.currentClip);

        if (!ok) a.currentClip = items.first();
        clipBox.setSelected(a.currentClip);
    }
}
