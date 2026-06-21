package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class UiBinders {
    private UiBinders() {
    }

    public static class CheckBoxBinder {
        private final World world;
        private final VisCheckBox box;
        private final IntPredicate applicable;
        private final IntFunction<Boolean> reader;
        private final BiConsumer<Integer, Boolean> applier;
        private boolean internalRefresh;
        private int entityId = -1;

        public CheckBoxBinder(World world, VisCheckBox box,
                              IntPredicate applicable, IntFunction<Boolean> reader,
                              BiConsumer<Integer, Boolean> applier) {
            this.world = world;
            this.box = box;
            this.applicable = applicable;
            this.reader = reader;
            this.applier = applier;

            box.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (internalRefresh) return;
                    if (entityId < 0 || !world.getEntityManager().isActive(entityId) || !applicable.test(entityId))
                        return;
                    applier.accept(entityId, box.isChecked());
                }
            });
        }

        public void setEntityId(int eid) {
            this.entityId = eid;
            refresh();
        }

        public void refresh() {
            internalRefresh = true;
            try {
                boolean ok = entityId >= 0 && world.getEntityManager().isActive(entityId) && applicable.test(entityId);
                box.setDisabled(!ok);
                if (!ok) {
                    box.setChecked(false);
                    return;
                }
                box.setChecked(Boolean.TRUE.equals(reader.apply(entityId)));
            } finally {
                internalRefresh = false;
            }
        }
    }

    public static class IntSpinnerBinder {
        private final World world;
        private final Spinner spinner;
        private final IntSpinnerModel model;
        private final IntPredicate applicable;
        private final IntFunction<Integer> reader;
        private final BiConsumer<Integer, Integer> applier;
        private boolean internalRefresh;
        private int entityId = -1;
        private int cached;

        public IntSpinnerBinder(World w, Spinner sp, IntSpinnerModel model,
                                IntPredicate applicable, IntFunction<Integer> reader,
                                BiConsumer<Integer, Integer> applier) {
            this.world = w;
            this.spinner = sp;
            this.model = model;
            this.applicable = applicable;
            this.reader = reader;
            this.applier = applier;

            sp.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (internalRefresh) return;
                    if (entityId < 0 || !w.getEntityManager().isActive(entityId) || !applicable.test(entityId)) return;
                    int after = parseInt(model.getText(), cached);
                    if (after != cached) applier.accept(entityId, after);
                    cached = after;
                }
            });
        }

        public void setEntityId(int eid) {
            this.entityId = eid;
            refresh();
        }

        public void refresh() {
            internalRefresh = true;
            try {
                boolean ok = entityId >= 0 && world.getEntityManager().isActive(entityId) && applicable.test(entityId);
                spinner.setDisabled(!ok);
                if (!ok) {
                    model.setValue(0);
                    cached = 0;
                    return;
                }
                cached = reader.apply(entityId);
                model.setValue(cached);
            } finally {
                internalRefresh = false;
            }
        }

        private static int parseInt(String s, int fallback) {
            if (s == null) return fallback;
            s = s.trim();
            if (s.isEmpty() || "-".equals(s)) return fallback;
            try {
                return Integer.parseInt(s);
            } catch (Exception e) {
                return fallback;
            }
        }
    }

    public static class FloatSpinnerBinder {
        private final World world;
        private final Spinner spinner;
        private final SimpleFloatSpinnerModel model;

        private final IntPredicate applicable;
        private final IntFunction<Float> reader;
        private final BiConsumer<Integer, Float> applier;

        private boolean internalRefresh = false;
        private int entityId = -1;
        private float cached = 0f;

        public FloatSpinnerBinder(
                World w,
                Spinner sp,
                SimpleFloatSpinnerModel model,
                IntPredicate applicable,
                IntFunction<Float> reader,
                BiConsumer<Integer, Float> applier
        ) {
            this.world = w;
            this.spinner = sp;
            this.model = model;
            this.applicable = applicable;
            this.reader = reader;
            this.applier = applier;

            sp.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (internalRefresh) return;
                    if (!isApplicableEntity()) return;

                    float value = safeParseFloat(model.getText(), cached);

                    // Avoids unnecessary updates (otherwise internal loop)
                    if (value != cached) {
                        applier.accept(entityId, value);
                        cached = value;
                    }
                }
            });
        }

        // ------------------------------------------------------------------------
        // Public API
        // ------------------------------------------------------------------------

        public void setEntityId(int eid) {
            this.entityId = eid;
            refresh();
        }

        public void refresh() {
            internalRefresh = true;
            try {
                boolean ok = isApplicableEntity();
                spinner.setDisabled(!ok);

                if (!ok) {
                    cached = 0f;
                    model.setValue(0f);
                    return;
                }

                cached = reader.apply(entityId);
                model.setValue(cached);

            } finally {
                internalRefresh = false;
            }
        }

        // ------------------------------------------------------------------------
        // Helpers
        // ------------------------------------------------------------------------

        private boolean isApplicableEntity() {
            return entityId >= 0
                    && world.getEntityManager().isActive(entityId)
                    && applicable.test(entityId);
        }

        private static float safeParseFloat(String text, float fallback) {
            if (text == null) return fallback;
            String s = text.trim().replace(',', '.');

            // Allowed intermediate cases: the user types "-" or "."
            if (s.isEmpty() || "-".equals(s) || ".".equals(s)) return fallback;

            try {
                return Float.parseFloat(s);
            } catch (Exception e) {
                return fallback;
            }
        }
    }


    public static class SelectBoxBinder<T> {
        private final World world;
        private final VisSelectBox<T> box;
        private final IntPredicate applicable;
        private final IntFunction<T> reader;
        private final TriConsumer<Integer, T, T> applier; // (eid, before, after)
        private boolean internalRefresh;
        private int entityId = -1;
        private T cached;

        public SelectBoxBinder(World w, VisSelectBox<T> box,
                               IntPredicate applicable, IntFunction<T> reader,
                               TriConsumer<Integer, T, T> applier) {
            this.world = w;
            this.box = box;
            this.applicable = applicable;
            this.reader = reader;
            this.applier = applier;

            box.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (internalRefresh) return;
                    if (entityId < 0
                            || !w.getEntityManager().isActive(entityId)
                            || !applicable.test(entityId)) return;

                    T after = box.getSelected();
                    T before = cached;
                    if (!Objects.equals(before, after)) {
                        applier.accept(entityId, before, after);
                    }
                    cached = after;
                }
            });
        }

        public void setEntityId(int eid) {
            this.entityId = eid;
            refresh();
        }

        /**
         * Old refresh: leaves the list untouched and only realigns selection.
         */
        public void refresh() {
            internalRefresh = true;
            try {
                boolean ok = entityId >= 0
                        && world.getEntityManager().isActive(entityId)
                        && applicable.test(entityId);

                box.setDisabled(!ok);
                if (!ok) {
                    cached = null;
                    return;
                }

                cached = reader.apply(entityId);
                box.setSelected(cached);
            } finally {
                internalRefresh = false;
            }
        }

        /**
         * NEW: update the list WITHOUT triggering the applier, while realigning with ECS.
         */
        public void setItems(Array<T> items) {
            internalRefresh = true;
            try {
                box.setItems(items);

                boolean ok = entityId >= 0
                        && world.getEntityManager().isActive(entityId)
                        && applicable.test(entityId);

                if (!ok) {
                    cached = null;
                    return;
                }

                T value = reader.apply(entityId);
                cached = value;

                if (value != null && items.contains(value, false)) {
                    box.setSelected(value);
                } else if (items.size > 0) {
                    // fallback: let the combo choose (first item) and align cached state
                    box.setSelected(items.first());
                    cached = box.getSelected();
                } else {
                    // no more items: nothing to select
                }
            } finally {
                internalRefresh = false;
            }
        }
    }


    public static class ColorPickerBinder {

        private final World world;
        private final ColorPicker picker;
        private final IntPredicate applicable;
        /**
         * Reads the packed color (rgba8888) from the entity.
         */
        private final IntFunction<Integer> readerPacked;
        /**
         * Applies the packed color (rgba8888): (eid, before, after).
         */
        private final TriConsumer<Integer, Integer, Integer> applierPacked;
        /**
         * Pour synchroniser l’UI (ex: image::setColor).
         */
        private final Consumer<Color> uiSync;

        private boolean internalRefresh;
        private int entityId = -1;
        private int cachedPacked;
        private final Color tmp = new Color();

        public ColorPickerBinder(
                World world,
                ColorPicker picker,
                IntPredicate applicable,
                IntFunction<Integer> readerPacked,
                TriConsumer<Integer, Integer, Integer> applierPacked,
                Consumer<Color> uiSync
        ) {
            this.world = world;
            this.picker = picker;
            this.applicable = applicable;
            this.readerPacked = readerPacked;
            this.applierPacked = applierPacked;
            this.uiSync = uiSync;

            // Listen to the picker through a ColorPickerAdapter
            picker.setListener(new ColorPickerAdapter() {
                @Override
                public void finished(Color newColor) {
                    onColorChosen(newColor);
                }
            });
        }

        private void onColorChosen(Color newColor) {
            if (internalRefresh) return;
            if (entityId < 0
                    || !world.getEntityManager().isActive(entityId)
                    || !applicable.test(entityId)) {
                return;
            }

            int before = cachedPacked;
            int after = Color.rgba8888(newColor);

            if (before != after) {
                applierPacked.accept(entityId, before, after);
                cachedPacked = after;
            }

            if (uiSync != null) {
                uiSync.accept(newColor);
            }
        }

        public void setEntityId(int eid) {
            this.entityId = eid;
            refresh();
        }

        public void refresh() {
            internalRefresh = true;
            try {
                boolean ok = entityId >= 0
                        && world.getEntityManager().isActive(entityId)
                        && applicable.test(entityId);

                if (!ok) {
                    // no tint: restore white
                    cachedPacked = 0xFFFFFFFF;
                    tmp.set(Color.WHITE);
                    picker.setColor(tmp);
                    if (uiSync != null) uiSync.accept(tmp);
                    return;
                }

                // Read rgba int from the component
                cachedPacked = readerPacked.apply(entityId);
                // Conversion to Color
                Color.rgba8888ToColor(tmp, cachedPacked);

                // Sync the picker and button.
                picker.setColor(tmp);
                if (uiSync != null) uiSync.accept(tmp);

            } finally {
                internalRefresh = false;
            }
        }
    }


    // petit functional util
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }


}
