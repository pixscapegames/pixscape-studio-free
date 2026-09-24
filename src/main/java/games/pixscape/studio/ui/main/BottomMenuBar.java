package games.pixscape.studio.ui.main;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.studio.ui.modal.Dialogs;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.EditorDocumentKey;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.exception.HtmlPreviewNotReadyException;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.preview.PreviewLauncher;
import games.pixscape.studio.ui.preview.PreviewTarget;

import java.io.IOException;
import java.util.function.Consumer;


public class BottomMenuBar extends VisTable {
    private final StudioApplicationAdapter app;
    private final VisSelectBox<String> sceneSelectBox;
    private final Array<String> items = new Array<>();
    private final VisLabel zoomValue;
    private final VisLabel panFieldX;
    private final VisLabel panFieldY;
    private final VisTextButton btnPreview;
    private final VisTextButton btnPreviewSettings;
    private final VisSelectBox<Resolution> resolutionSelectBox;
    private final VisCheckBox landScapeChekBox;
    private final VisCheckBox rulersVisibilityCheckBox;
    private final Button btnAddScene;
    private final Button btnDeleteScene;
    private final VisTextButton centerCam;
    private boolean sceneControlsBusy;

    public static final float HEIGHT = 32;
    private final int MY_TAG = EventFlow.tag(this);

    public BottomMenuBar(StudioApplicationAdapter application) {

        this.app = application;
        OrthographicCamera camera = (OrthographicCamera) app.getCanvas().getGridStage().getViewport().getCamera();
        MenuBar.MenuBarStyle mbStyle = VisUI.getSkin().get(MenuBar.MenuBarStyle.class);
        setBackground(mbStyle.background);
        padTop(3);

        sceneSelectBox = new VisSelectBox<>("default");
        btnAddScene = new Button(VisUI.getSkin(), "add");
        btnAddScene.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!worldEditingAllowed()) return;
                createNewScene();
            }
        });
        btnDeleteScene = new Button(VisUI.getSkin(), "delete");
        btnDeleteScene.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!worldEditingAllowed()) return;
                String sceneName = getSceneNameToDelete();
                if (sceneName == null || sceneName.isBlank()) return;
                showDeleteSceneDialog(sceneName);
            }
        });
        VisLabel sceneLabel = new VisLabel("Scene:");
        VisLabel zoomLabel = new VisLabel("Zoom:  ");
        zoomValue = new VisLabel();
        VisLabel panLabel = new VisLabel("Pan:  ");
        panFieldX = new VisLabel();
        panFieldY = new VisLabel();
        centerCam = new VisTextButton("Center camera");
        centerCam.setColor(CommonLayout.BUTTON_COLOR);
        centerCam.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                app.centerActiveCanvasCamera();
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
        app.getEditorDocumentManager().addListener(new EditorDocumentManager.Listener() {
            @Override public void documentOpened(OpenEditorDocument document) {
                if (document.type() == EditorDocumentType.SCENE) refreshSelectBox();
            }

            @Override public void documentActivated(OpenEditorDocument previous,
                                                     OpenEditorDocument current) {
                refreshSelectBox();
            }

            @Override public void documentClosed(OpenEditorDocument document) {
                if (document.type() == EditorDocumentType.SCENE) refreshSelectBox();
            }
        });
        rulersVisibilityCheckBox = new VisCheckBox("Rulers");
        rulersVisibilityCheckBox.setChecked(true);
        rulersVisibilityCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!canvasNavigationAllowed()) return;
                app.setActiveCanvasRulersVisible(rulersVisibilityCheckBox.isChecked());
            }
        });

        sceneSelectBox.setMaxListCount(10);
        sceneSelectBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (event.getTarget() != sceneSelectBox) return;
                requestSceneFromSelector(sceneSelectBox.getSelected());
            }
        });
        sceneSelectBox.getList().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                requestSceneFromSelector(sceneSelectBox.getList().getSelected());
            }
        });

        left();
        add(sceneLabel).padLeft(10).padRight(3);
        add(sceneSelectBox).width(120).left();
        add(btnAddScene).padLeft(4).left();
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

        EventFlow.i().subscribe(EventFlow.StudioEditingModeChanged.class,
                event -> refreshWorldEditingAvailability());
        refreshWorldEditingAvailability();
    }

    private void showDeleteSceneDialog(String sceneName) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || sceneName == null || sceneName.isBlank()) return;

        if (cfg.getSceneNames().size <= 1) {
            VisDialog error = new StudioDialog("Cannot delete scene");
            error.text("You cannot delete the last remaining scene.");
            error.button("OK");
            error.show(getStage());
            return;
        }

        VisDialog dialog = new StudioDialog("Delete Scene") {
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

    private void createNewScene() {
        try {
            app.getSceneService().createNewScene("New Scene");
        } catch (RuntimeException ex) {
            Dialogs.showOKDialog(getStage(), "Scene creation failed", ex.getMessage());
        }
        refreshSelectBox();
    }

    private void setSceneControlsBusy(boolean busy) {
        sceneControlsBusy = busy;
        refreshWorldEditingAvailability();
    }

    private void requestSceneFromSelector(String sceneName) {
        if (sceneControlsBusy) return;
        try {
            navigateSceneFromSelector(
                    ProjectConfig.getInstance(),
                    app.getEditorDocumentManager(),
                    sceneSelectBox,
                    items,
                    app.getSceneService()::changeSceneNow,
                    this::setSceneControlsBusy,
                    sceneName);
        } catch (RuntimeException ex) {
            Dialogs.showOKDialog(getStage(), "Scene open failed", ex.getMessage());
        }
    }

    static boolean navigateSceneFromSelector(ProjectConfig config,
                                             EditorDocumentManager documents,
                                             VisSelectBox<String> selector,
                                             Array<String> targetItems,
                                             Consumer<String> opener,
                                             Consumer<Boolean> busyState,
                                             String sceneName) {
        if (config == null || documents == null || selector == null || targetItems == null
                || opener == null || busyState == null
                || sceneName == null || sceneName.isBlank()) return false;
        EditorDocumentKey key = sceneDocumentKey(config, sceneName);
        if (key == null) return false;
        if (documents.find(key) != null) {
            populateSceneSelector(selector, targetItems, config);
            return false;
        }

        busyState.accept(true);
        try {
            opener.accept(sceneName);
            return true;
        } finally {
            busyState.accept(false);
            populateSceneSelector(selector, targetItems, config);
        }
    }

    private static EditorDocumentKey sceneDocumentKey(ProjectConfig config, String sceneName) {
        if (config == null || sceneName == null || sceneName.isBlank()) return null;
        String canonicalSceneId = config.canonicalSceneTag(sceneName);
        if (canonicalSceneId == null || canonicalSceneId.isBlank()) return null;
        return new EditorDocumentKey(EditorDocumentType.SCENE, canonicalSceneId);
    }

    private void updateDeleteSceneButtonState() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        boolean disabled =
                sceneControlsBusy
                        || !worldEditingAllowed()
                        || cfg == null
                        || cfg.getCurrentSceneName() == null
                        || cfg.getSceneNames().size <= 1;

        btnDeleteScene.setDisabled(disabled);
    }

    private boolean worldEditingAllowed() {
        return app.getCanvas().getStudioEditingModeService().allowsWorldEditingActions();
    }

    private void refreshWorldEditingAvailability() {
        sceneSelectBox.setDisabled(sceneControlsBusy);
        btnAddScene.setDisabled(sceneControlsBusy || !worldEditingAllowed());
        centerCam.setDisabled(!canvasNavigationAllowed());
        rulersVisibilityCheckBox.setDisabled(!canvasNavigationAllowed());
        updateDeleteSceneButtonState();
    }

    private boolean canvasNavigationAllowed() {
        return worldEditingAllowed() || app.hudCanvasActive();
    }

    public void refreshSelectBox() {
        populateSceneSelector(sceneSelectBox, items, ProjectConfig.getInstance());
        updateDeleteSceneButtonState();
    }

    static void populateSceneSelector(VisSelectBox<String> selector,
                                      Array<String> targetItems,
                                      ProjectConfig config) {
        targetItems.clear();

        if (config != null) {
            targetItems.addAll(config.getSceneNames());
        }

        selector.getSelection().setProgrammaticChangeEvents(false);
        selector.setItems(targetItems);

        if (config != null && config.getCurrentSceneMeta() != null) {
            String curName = config.getCurrentSceneMeta().getName();
            if (curName != null && targetItems.contains(curName, false)) {
                selector.getSelection().set(curName);
            } else if (targetItems.size > 0) {
                selector.getSelection().set(targetItems.first());
            }
        } else if (targetItems.size > 0) {
            selector.getSelection().set(targetItems.first());
        }

        selector.getSelection().setProgrammaticChangeEvents(true);
    }


    public void setZoom(float zoom) {
        zoomValue.setText(String.format("%.2f", zoom));
    }

    public void setPan(float x, float y) {
        panFieldX.setText(String.format("(%.0f,", x));
        panFieldY.setText(String.format("%.0f)", y));
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

    private final class PreviewSettingsDialog extends StudioDialog {
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
