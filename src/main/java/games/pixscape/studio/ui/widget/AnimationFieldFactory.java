package games.pixscape.studio.ui.widget;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.runtime.component.AnimationComponent;

public final class AnimationFieldFactory {

    private final World world;
    private final ComponentMapper<AnimationComponent> mAnim;

    public AnimationFieldFactory(World world) {
        this.world = world;
        this.mAnim = world.getMapper(AnimationComponent.class);
    }

    public FloatField fps() {
        FloatField f = new FloatField(world, (int e) -> mAnim.get(e).fps, mAnim::has);
        f.setDisplayDecimals(2);
        f.setApplier((eid, v) -> {
            AnimationComponent a = mAnim.get(eid);
            if (a == null) return;
            a.fps = Math.max(0.1f, v);
        });
        return f;
    }

    public UiBinders.CheckBoxBinder loopBinder(VisCheckBox cb) {
        return new UiBinders.CheckBoxBinder(
                world,
                cb,
                mAnim::has,
                (int e) -> mAnim.get(e).loop,
                (Integer e, Boolean v) -> {
                    AnimationComponent a = mAnim.get(e);
                    if (a != null) a.loop = v != null && v;
                }
        );
    }

    /**
     * Returns clip names (sorted) to populate a selectbox.
     */
    public Array<String> clipNames(int eid) {
        Array<String> out = new Array<>();
        if (!mAnim.has(eid)) return out;

        AnimationComponent a = mAnim.get(eid);
        if (a == null || a.clips == null) return out;

        for (var it = a.clips.keys(); it.hasNext; ) {
            out.add(it.next());
        }
        out.sort(String::compareTo);
        return out;
    }

    public UiBinders.SelectBoxBinder<String> currentClipBinder(VisSelectBox<String> sb) {
        return new UiBinders.SelectBoxBinder<>(
                world,
                sb,
                mAnim::has,
                (int e) -> {
                    AnimationComponent a = mAnim.get(e);
                    return (a != null && a.currentClip != null) ? a.currentClip : "";
                },
                (Integer e, String oldV, String newV) -> {
                    if (e == null) return;
                    AnimationComponent a = mAnim.get(e);
                    if (a == null) return;
                    if (newV == null) newV = "";
                    // ignore if the clip does not exist
                    if (newV.isEmpty() || (a.clips != null && a.clips.containsKey(newV))) {
                        a.currentClip = newV;
                        a.stateTime = 0f;
                        a.frame = -1; // force update frame next tick
                    }
                }
        );
    }
}
