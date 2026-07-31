package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.exception.HtmlPreviewNotReadyException;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.preview.PreviewLauncher;
import games.pixscape.studio.ui.preview.PreviewTarget;

import java.io.IOException;


public class BottomMenuBar extends VisTable {
    private final StudioApplicationAdapter app;
    private final VisSelectBox<String> sceneSelectBox;
    private final NewSceneWindow newSceneWindow;
    private final Array<String> items = new Array<>();
    private final VisLabel zoomValue;
    private final VisLabel panFieldX;
    private final VisLabel panFieldY;
    private final VisTextButton btnPreview;
    private final VisTextButton btnPreviewSettings;
    private final VisSelectBox<Resolution> resolutionSelectBox;
    private final VisCheckBox landScapeChekBox;
    private final VisCheckBox rulersVisibilityCheckBox;
    private final Button btnDeleteScene;
    private final SceneSwitchWorkflow sceneSwitchWorkflow;
    private String lastValue = null;

    public static final float HEIGHT = 32;
    private final int MY_TAG = EventFlow.tag(this);

    public BottomMenuBar(StudioApplicationAdapter application) {

        this.app = application;
        OrthographicCamera camera = (OrthographicCamera) app.getCanvas().getGridStage().getViewport().getCamera();
        MenuBar.MenuBarStyle mbStyle = VisUI.getSkin().get(MenuBar.MenuBarStyle.class);
        setBackground(mbStyle.background);
        padTop(3);

        sceneSelectBox = new VisSelectBox<>("default");
        btnDeleteScene = new Button(VisUI.getSkin(), "delete");
        btnDeleteScene.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String sceneName = getSceneNameToDelete();
                if (sceneName == null || sceneName.isBlank()) return;
                showDeleteSceneDialog(sceneName);
            }
        });
        VisLabel sceneLabel = new VisLabel("Scene:");
        newSceneWindow = new NewSceneWindow("New Scene");
        VisLabel zoomLabel = new VisLabel("Zoom:  ");
        zoomValue = new VisLabel();
        VisLabel panLabel = new VisLabel("Pan:  ");
        panFieldX = new VisLabel();
        panFieldY = new VisLabel();
        VisTextButton centerCam = new VisTextButton("Center camera");
        centerCam.setColor(CommonLayout.BUTTON_COLOR);
        centerCam.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                camera.position.set(0, 0, 0f);
                camera.update();
            }
        });
        btnPreview = new VisTextButton("Preview");
        btnPreview.setColor(Color.RED);
        btnPreview.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                launchPreviewWithSaveGuard();
            }
        });
        btnPreviewSettings = new VisTextButton("Settings");
        btnPreviewSettings.setColor(CommonLayout.BUTTON_COLOR);
        btnPreviewSettings.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                PreviewSettingsDialog dialog = new PreviewSettingsDialog();
                dialog.show(getStage());
            }
        });
        resolutionSelectBox = new VisSelectBox<>();
        resolutionSelectBox.setItems(getResolutions());
        landScapeChekBox = new VisCheckBox("landscape");
        landScapeChekBox.setChecked(true);
        applyPreviewSettingsFromConfig();

        EventFlow.i().subscribe(EventFlow.SceneNameChanged.class, ev -> {
            if (ev.sourceTag() == MY_TAG) return;
            refreshSelectBox();
        });
        rulersVisibilityCheckBox = new VisCheckBox("Rulers");
        rulersVisibilityCheckBox.setChecked(true);
        rulersVisibilityCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                app.getDockManager().setRulersVisible(rulersVisibilityCheckBox.isChecked());
            }
        });

        sceneSelectBox.setMaxListCount(10);
        sceneSwitchWorkflow = new SceneSwitchWorkflow(
                (targetScene, continuation, onCancel, onSaveFailure) ->
                        app.runAfterCurrentSceneSaveDecision(
                                "Unsaved Project",
                                "Do you want to save before switching scenes?",
                                continuation,
                                onCancel,
                                onSaveFailure
                        ),
                targetScene -> app.getSceneService().changeSceneNow(targetScene),
                this::refreshSelectBox,
                targetScene -> lastValue = targetScene,
                throwable -> Dialogs.showOKDialog(
                        getStage(),
                        "Save failed",
                        PreviewLaunchSupport.userMessageFor(throwable)
                ),
                ex -> Dialogs.showOKDialog(getStage(), "Scene switch failed", ex.getMessage()),
                sceneSelectBox::setDisabled
        );
        sceneSelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (event.getTarget() != sceneSelectBox) return;

                String cur = sceneSelectBox.getSelected();
                if (cur == null || cur.equals(lastValue)) return;

                if ("New...".equals(cur)) {
                    lastValue = cur;
                    // Open the scene creation window
                    newSceneWindow.resetSceneName();
                    app.getUiStage().addActor(newSceneWindow.fadeIn());
                    return;
                }

                sceneSwitchWorkflow.request(cur);
            }
        });

        newSceneWindow.getOKButton().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String name = newSceneWindow.getSceneName();

                if (name != null && !name.isBlank()) {
                    try {
                        app.getSceneService().createNewScene(
                                name,
                                newSceneWindow.getTileWidth(),
                                newSceneWindow.getTileHeight(),
                                newSceneWindow.getProjection()
                        );
                        newSceneWindow.fadeOut();
                        refreshSelectBox();
                    } catch (RuntimeException ex) {
                        Dialogs.showOKDialog(getStage(), "Scene creation failed", ex.getMessage());
                        refreshSelectBox();
                    }
                }
            }
        });


        left();
        add(sceneLabel).padLeft(10).padRight(3);
        add(sceneSelectBox).width(120).left();
        add(btnDeleteScene).padLeft(4).padRight(100).left();
        add(btnPreview).left().padRight(8);
        add(btnPreviewSettings).left().padRight(20);
        add().expandX();

        add(panLabel).right();
        add(panFieldX).right();
        add(panFieldY).right().padRight(30);
        add(zoomLabel).right();
        add(zoomValue).right().padRight(30);
        add(rulersVisibilityCheckBox).right().padRight(100);
        add(centerCam).width(120).right();
    }

    private void showDeleteSceneDialog(String sceneName) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || sceneName == null || sceneName.isBlank()) return;

        if (cfg.getSceneNames().size <= 1) {
            VisDialog error = new VisDialog("Cannot delete scene");
            error.text("You cannot delete the last remaining scene.");
            error.button("OK");
            error.show(getStage());
            return;
        }

        VisDialog dialog = new VisDialog("Delete Scene") {
            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) return;

                try {
                    app.getSceneService().deleteScene(sceneName);
                    refreshSelectBox();
                } catch (RuntimeException ex) {
                    Dialogs.showOKDialog(getStage(), "Scene delete failed", ex.getMessage());
                    refreshSelectBox();
                }
            }
        };

        dialog.text("Delete scene \"" + sceneName + "\"?\n\nThis action cannot be undone.");
        dialog.button("Delete", true);
        dialog.button("Cancel", false);
        dialog.show(getStage());
    }

    private void launchPreviewWithSaveGuard() {
        if (!app.getSceneService().requiresSaveBeforePreview()) {
            launchPreviewNow();
            return;
        }

        app.getSceneService().saveProjectAndCurrentSceneWithProgress(
                app.getUiStage(),
                this::launchPreviewNow,
                throwable -> {
                    if (throwable instanceof HtmlPreviewNotReadyException) {
                        Dialogs.showOKDialog(
                                getStage(),
                                "HTML Preview",
                                throwable.getMessage()
                        );
                        return;
                    }

                    Dialogs.showOKDialog(
                            getStage(),
                            "Preview failed",
                            "Save before preview failed: " + PreviewLaunchSupport.userMessageFor(throwable)
                    );
                }
        );
    }

    private void launchPreviewNow() {
        try {
            ProjectConfig cfg = ProjectConfig.getInstance();

            PreviewLauncher.open(
                    cfg,
                    () -> app.setPreviewActive(true),
                    () -> app.setPreviewActive(false),
                    resolutionSelectBox.getSelected(),
                    landScapeChekBox.isChecked(),
                    cfg.previewTarget
            );
        } catch (HtmlPreviewNotReadyException ex) {
            Dialogs.showOKDialog(
                    getStage(),
                    "HTML Preview",
                    ex.getMessage()
            );
        } catch (RuntimeException ex) {
            if (PreviewLaunchSupport.isInternalInvariantFailure(ex)) {
                throw ex;
            }

            Gdx.app.error("BottomMenuBar", "Preview launch failed", ex);
            Dialogs.showOKDialog(getStage(), "Preview failed", PreviewLaunchSupport.userMessageFor(ex));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyPreviewSettingsFromConfig() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        Array<Resolution> resolutions = getResolutions();
        resolutionSelectBox.setItems(resolutions);

        Resolution match = null;
        for (Resolution resolution : resolutions) {
            if (resolution.witdht() == cfg.previewWidth
                    && resolution.height() == cfg.previewHeight) {
                match = resolution;
                break;
            }
        }

        if (match == null && resolutions.size > 0) {
            match = resolutions.first();
            cfg.previewWidth = match.witdht();
            cfg.previewHeight = match.height();
        }

        if (match != null) {
            resolutionSelectBox.setSelected(match);
        }

        landScapeChekBox.setChecked(cfg.previewLandscape);
    }

    private String getSceneNameToDelete() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;
        return cfg.getCurrentSceneName();
    }

    private void updateDeleteSceneButtonState() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        boolean disabled =
                cfg == null
                        || cfg.getCurrentSceneName() == null
                        || cfg.getSceneNames().size <= 1;

        btnDeleteScene.setDisabled(disabled);
    }

    public void refreshSelectBox() {
        items.clear();
        items.add("New...");

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg != null) {
            items.addAll(cfg.getSceneNames());
        }

        sceneSelectBox.getSelection().setProgrammaticChangeEvents(false);
        sceneSelectBox.setItems(items);

        if (cfg != null && cfg.getCurrentSceneMeta() != null) {
            String curName = cfg.getCurrentSceneMeta().getName();
            if (curName != null && items.contains(curName, false)) {
                sceneSelectBox.getSelection().set(curName);
                lastValue = curName;
            } else {
                sceneSelectBox.getSelection().set("New...");
                lastValue = "New...";
            }
        } else {
            sceneSelectBox.getSelection().set("New...");
            lastValue = "New...";
        }

        sceneSelectBox.getSelection().setProgrammaticChangeEvents(true);
        updateDeleteSceneButtonState();
    }


    public void setZoom(float zoom) {
        zoomValue.setText(String.format("%.1f", zoom));
    }

    public void setPan(float x, float y) {
        panFieldX.setText(String.format("(%.0f,", x));
        panFieldY.setText(String.format("%.0f)", y));
    }


    private final class NewSceneWindow extends VisWindow {
        private final VisTextButton ok = new VisTextButton("OK");
        private final VisTextField sceneName = new VisTextField();
        private final VisSelectBox<String> projectionBox = new VisSelectBox<>();
        private final VisTextField tfTileWidth = new VisTextField("32");
        private final VisTextField tfTileHeight = new VisTextField("32");


        public NewSceneWindow(String title) {
            super(title);
            getTitleLabel().setAlignment(Align.center);
            ok.setColor(CommonLayout.BUTTON_COLOR);

            // --- Default Scene (Tiled) ---
            projectionBox.setItems("None", "Orthogonal", "Isometric");
            projectionBox.setSelected("Orthogonal");
            projectionBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    boolean tiledEnabled = !"None".equals(projectionBox.getSelected());

                    tfTileWidth.setDisabled(!tiledEnabled);
                    tfTileHeight.setDisabled(!tiledEnabled);

                    if (!tiledEnabled) {
                        tfTileWidth.setText("32");
                        tfTileHeight.setText("32");
                    }
                }
            });
            defaults().pad(4).left();
            setWidth(280);
            setHeight(250);
            addCloseButton();

            add(new VisLabel("Scene name: ")).left();
            add(sceneName).left().growX().row();

            add(new VisLabel("Tiled projection:")).left();
            add(projectionBox).growX().row();

            add(new VisLabel("Tile Width (px):")).left();
            add(tfTileWidth).growX().row();

            add(new VisLabel("Tile Height (px):")).left();
            add(tfTileHeight).growX().row();

            add(ok).padTop(15).center().colspan(2);
        }

        @Override
        protected void close() {
            refreshSelectBox();
            super.close();
        }

        public VisTextButton getOKButton() {
            return ok;
        }

        public String getSceneName() {
            return sceneName.getText();
        }

        public void resetSceneName() {
            sceneName.setText("");
        }

        public String getProjection() {
            return projectionBox.getSelected();
        }

        public int getTileWidth() {
            return parseIntSafe(tfTileWidth.getText(), 32);
        }

        public int getTileHeight() {
            return parseIntSafe(tfTileHeight.getText(), 32);
        }

        private int parseIntSafe(String value, int def) {
            try {
                int v = Integer.parseInt(value.trim());
                return v > 0 ? v : def;
            } catch (Exception e) {
                return def;
            }
        }
    }

    public void setPreviewRunning(boolean running) {
        btnPreview.setText(running ? "Preview (open)" : "Preview");
        // Keep the button clickable: if already open, it focuses the window.
        btnPreview.setDisabled(false);
        btnPreview.setColor(running ? Color.ORANGE : Color.RED);
    }

    private Array<Resolution> getResolutions() {
        Array<Resolution> resolutions = new Array<>();
        resolutions.add(new Resolution(1280, 720));
        resolutions.add(new Resolution(1280, 800));
        resolutions.add(new Resolution(1280, 1024));
        resolutions.add(new Resolution(1360, 768));
        resolutions.add(new Resolution(1366, 768));
        resolutions.add(new Resolution(1440, 900));
        resolutions.add(new Resolution(1600, 900));
        resolutions.add(new Resolution(1600, 1200));
        resolutions.add(new Resolution(1680, 1050));
        resolutions.add(new Resolution(1920, 1080));
        resolutions.add(new Resolution(1920, 1200));

        return resolutions;
    }

    private final class PreviewSettingsDialog extends VisDialog {
        private static final String TARGET_DESKTOP = "Desktop GL30";
        private static final String TARGET_HTML = "HTML WebGL2";

        private final VisSelectBox<Resolution> resolutionBox = new VisSelectBox<>();
        private final VisSelectBox<String> orientationSelect = new VisSelectBox<>();
        private final VisSelectBox<String> platformSelect = new VisSelectBox<>();

        private final VisLabel resolutionLabel = new VisLabel("Resolution");
        private final VisLabel orientationLabel = new VisLabel("Orientation");
        private final VisLabel htmlInfoLabel = new VisLabel(
                "HTML WebGL2 uses the browser canvas size."
        );

        private PreviewSettingsDialog() {
            super("Preview Settings");
            getTitleLabel().setAlignment(Align.center);
            addCloseButton();

            ProjectConfig cfg = ProjectConfig.getInstance();

            resolutionBox.setItems(getResolutions());
            selectConfiguredResolution(cfg);

            orientationSelect.setItems("Landscape", "Portrait");
            orientationSelect.setSelected(cfg.previewLandscape ? "Landscape" : "Portrait");

            platformSelect.setItems(TARGET_DESKTOP, TARGET_HTML);
            platformSelect.setSelected(
                    cfg.previewTarget == PreviewTarget.HTML
                            ? TARGET_HTML
                            : TARGET_DESKTOP
            );

            htmlInfoLabel.setWrap(true);
            htmlInfoLabel.setColor(Color.LIGHT_GRAY);

            platformSelect.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    updateResolutionControlsVisibility();
                }
            });

            Table content = getContentTable();
            content.clear();
            content.defaults().pad(6).left();

            content.add(new VisLabel("Platform target")).left();
            content.add(platformSelect).width(180).row();

            content.add(resolutionLabel).left();
            content.add(resolutionBox).width(180).row();

            content.add(orientationLabel).left();
            content.add(orientationSelect).width(180).row();

            content.add(htmlInfoLabel).left().colspan(2).width(300).row();

            updateResolutionControlsVisibility();

            VisTextButton cancelButton = new VisTextButton("Cancel");
            VisTextButton saveButton = new VisTextButton("Save");

            cancelButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    hide();
                }
            });

            saveButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    saveSettings();
                    hide();
                }
            });

            Table buttons = getButtonsTable();
            buttons.clear();
            buttons.defaults().pad(8).minWidth(100);
            buttons.center();
            buttons.padTop(12);

            buttons.add(cancelButton);
            buttons.add(saveButton);
        }

        private void selectConfiguredResolution(ProjectConfig cfg) {
            if (cfg == null) return;

            Array<Resolution> resolutions = getResolutions();

            for (Resolution resolution : resolutions) {
                if (resolution.witdht() == cfg.previewWidth
                        && resolution.height() == cfg.previewHeight) {
                    resolutionBox.setSelected(resolution);
                    return;
                }
            }

            if (resolutions.size > 0) {
                resolutionBox.setSelected(resolutions.first());
            }
        }

        private void updateResolutionControlsVisibility() {
            boolean html = TARGET_HTML.equals(platformSelect.getSelected());

            resolutionLabel.setVisible(!html);
            resolutionBox.setVisible(!html);
            orientationLabel.setVisible(!html);
            orientationSelect.setVisible(!html);

            htmlInfoLabel.setVisible(html);
        }

        private void saveSettings() {
            ProjectConfig cfg = ProjectConfig.getInstance();
            if (cfg == null) return;

            PreviewTarget target = TARGET_HTML.equals(platformSelect.getSelected())
                    ? PreviewTarget.HTML
                    : PreviewTarget.DESKTOP;

            cfg.previewTarget = target;

            if (target == PreviewTarget.DESKTOP) {
                Resolution selected = resolutionBox.getSelected();

                if (selected != null) {
                    cfg.previewWidth = selected.witdht();
                    cfg.previewHeight = selected.height();
                }

                cfg.previewLandscape = "Landscape".equals(orientationSelect.getSelected());
            }

            Gdx.app.log("PreviewSettings", "Saved preview target=" + cfg.previewTarget);

            applyPreviewSettingsFromConfig();
            app.getSceneService().markCurrentSceneSaveRequired();
        }
    }
}
