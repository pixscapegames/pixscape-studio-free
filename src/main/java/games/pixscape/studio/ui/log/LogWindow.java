package games.pixscape.studio.ui.log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.logging.StudioLogCapture;
import games.pixscape.studio.logging.StudioLogLevel;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.widget.SimpleSelectBox;

import java.util.List;

public final class LogWindow extends DockablePanel {

    private static final float COLOR_INFO_R = 1f;
    private static final float COLOR_INFO_G = 1f;
    private static final float COLOR_INFO_B = 1f;
    private static final float COLOR_INFO_A = 1f;

    private static final float COLOR_WARN_R = 1f;
    private static final float COLOR_WARN_G = 0.65f;
    private static final float COLOR_WARN_B = 0.2f;
    private static final float COLOR_WARN_A = 1f;

    private static final float COLOR_ERROR_R = 1f;
    private static final float COLOR_ERROR_G = 0.35f;
    private static final float COLOR_ERROR_B = 0.35f;
    private static final float COLOR_ERROR_A = 1f;

    private final VisTable linesTable = new VisTable(false);
    private final VisScrollPane scroll;
    private final VisTextField filter = new VisTextField();
    private final SimpleSelectBox<StudioLogLevel.Selection> levelFilter = new SimpleSelectBox<>();
    private final VisCheckBox paused = new VisCheckBox("Pause", false);

    private final StudioLogCapture.Listener listener;

    private boolean listening;

    public LogWindow() {
        super("Debug Console");
        setDockMode(DockMode.WINDOW_ONLY);
        setOpenOnRegister(false);
        setPreferredWindowSize(900, 420);

        linesTable.top().left();
        linesTable.defaults().left().growX().padBottom(2f);

        scroll = new VisScrollPane(linesTable);
        scroll.setFadeScrollBars(false);
        scroll.setSmoothScrolling(true);
        scroll.setScrollingDisabled(false, false);

        VisTable top = new VisTable(true);
        filter.setMessageText("filter...");
        levelFilter.setItems(
                StudioLogLevel.Selection.ALL,
                StudioLogLevel.Selection.DEBUG,
                StudioLogLevel.Selection.INFO,
                StudioLogLevel.Selection.ERROR,
                StudioLogLevel.Selection.NONE
        );
        levelFilter.setSelected(StudioLogLevel.selected());

        VisTextButton clearBtn = new VisTextButton("Clear");
        clearBtn.setColor(CommonLayout.BUTTON_COLOR);

        VisTextButton copyBtn = new VisTextButton("Copy");
        copyBtn.setColor(CommonLayout.BUTTON_COLOR);

        clearBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                StudioLogCapture.clear();
                refreshAll();
            }
        });

        copyBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.getClipboard().setContents(buildFilteredText());
            }
        });

        filter.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refreshAll();
            }
        });

        levelFilter.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                StudioLogLevel.setSelected(levelFilter.getSelected());
                refreshAll();
            }
        });

        top.add(new VisLabel("Level:")).right();
        top.add(levelFilter).width(100);
        top.add(new VisLabel("Filter:")).right();
        top.add(filter).growX();
        top.add(paused);
        top.add(copyBtn);
        top.add(clearBtn);

        add(top).growX().row();
        add(scroll).grow().minWidth(600).row();

        listener = entry -> {
            if (paused.isChecked()) return;
            if (!matchesFilter(entry)) return;

            Gdx.app.postRunnable(() -> {
                if (matchesFilter(entry)) appendLine(entry);
            });
        };

        refreshAll();
    }

    @Override
    protected void setStage(Stage stage) {
        Stage previous = getStage();
        super.setStage(stage);
        if (previous == stage) return;

        if (stage == null) {
            stopListening();
        } else {
            startListening();
            refreshAll();
        }
    }

    private void refreshAll() {
        List<StudioLogCapture.Entry> entries = StudioLogCapture.snapshot();

        linesTable.clearChildren();

        for (StudioLogCapture.Entry entry : entries) {
            if (!matchesFilter(entry)) continue;
            addLineLabel(entry);
        }

        linesTable.invalidateHierarchy();
        scrollToBottom();
    }

    private void appendLine(StudioLogCapture.Entry entry) {
        addLineLabel(entry);
        linesTable.invalidateHierarchy();
        scrollToBottom();
    }

    private void addLineLabel(StudioLogCapture.Entry entry) {
        VisLabel label = new VisLabel(entry == null ? "" : entry.text());
        label.setColor(colorForLineR(entry), colorForLineG(entry), colorForLineB(entry), colorForLineA(entry));

        linesTable.add(label).left().growX().row();
    }

    private String buildFilteredText() {
        List<StudioLogCapture.Entry> entries = StudioLogCapture.snapshot();
        StringBuilder sb = new StringBuilder(4096);
        boolean first = true;

        for (StudioLogCapture.Entry entry : entries) {
            if (!matchesFilter(entry)) continue;
            if (!first) sb.append('\n');
            first = false;
            sb.append(entry.text());
        }

        return sb.toString();
    }

    private void scrollToBottom() {
        scroll.layout();
        scroll.setScrollPercentY(1f);
        scroll.updateVisualScroll();
    }

    private boolean matchesFilter(StudioLogCapture.Entry entry) {
        if (entry == null) return false;
        StudioLogLevel.Selection selectedLevel = levelFilter.getSelected();
        if (selectedLevel == StudioLogLevel.Selection.NONE) {
            return false;
        }
        if (selectedLevel == StudioLogLevel.Selection.DEBUG && entry.level() != StudioLogCapture.Level.DEBUG) {
            return false;
        }
        if (selectedLevel == StudioLogLevel.Selection.INFO && entry.level() != StudioLogCapture.Level.INFO) {
            return false;
        }
        if (selectedLevel == StudioLogLevel.Selection.ERROR && entry.level() != StudioLogCapture.Level.ERROR) {
            return false;
        }

        String f = safe(filter.getText());
        return f.isEmpty() || entry.text().contains(f);
    }

    private float colorForLineR(StudioLogCapture.Entry entry) {
        if (isError(entry)) return COLOR_ERROR_R;
        if (isWarn(entry)) return COLOR_WARN_R;
        return COLOR_INFO_R;
    }

    private float colorForLineG(StudioLogCapture.Entry entry) {
        if (isError(entry)) return COLOR_ERROR_G;
        if (isWarn(entry)) return COLOR_WARN_G;
        return COLOR_INFO_G;
    }

    private float colorForLineB(StudioLogCapture.Entry entry) {
        if (isError(entry)) return COLOR_ERROR_B;
        if (isWarn(entry)) return COLOR_WARN_B;
        return COLOR_INFO_B;
    }

    private float colorForLineA(StudioLogCapture.Entry entry) {
        if (isError(entry)) return COLOR_ERROR_A;
        if (isWarn(entry)) return COLOR_WARN_A;
        return COLOR_INFO_A;
    }

    private boolean isWarn(StudioLogCapture.Entry entry) {
        return entry != null && entry.text().contains("[WARN]");
    }

    private boolean isError(StudioLogCapture.Entry entry) {
        return entry != null && entry.level() == StudioLogCapture.Level.ERROR;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void startListening() {
        if (listening) return;
        StudioLogCapture.addListener(listener);
        listening = true;
    }

    private void stopListening() {
        if (!listening) return;
        StudioLogCapture.removeListener(listener);
        listening = false;
    }
}
