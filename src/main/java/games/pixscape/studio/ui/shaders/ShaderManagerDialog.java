package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.ShaderVariant;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.ShaderSourcePreprocessor;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.modal.StudioModalWindow;
import games.pixscape.studio.ui.modal.Dialogs;
import games.pixscape.studio.ui.widget.ScrollableCodeEditor;

public class ShaderManagerDialog extends StudioModalWindow {

    private enum ShaderKind {
        MATERIAL,
        FX
    }

    private interface NameAction {
        boolean run(String value);
    }

    private static final class ShaderSources {
        final ObjectMap<ShaderVariant, String> vertices = new ObjectMap<>();
        final ObjectMap<ShaderVariant, String> fragments = new ObjectMap<>();
    }

    private final StudioApplicationAdapter app;

    private final VisSelectBox<ShaderKind> typeBox;
    private final VisSelectBox<String> shaderBox;

    private final TabbedPane targetTabs;
    private final VisTable targetContent = new VisTable(true);
    private final VisScrollPane mainScrollPane;

    private final ObjectMap<ShaderVariant, ScrollableCodeEditor> vertEditors = new ObjectMap<>();
    private final ObjectMap<ShaderVariant, ScrollableCodeEditor> fragEditors = new ObjectMap<>();

    private final VisTextButton testButton;
    private final VisTextButton saveButton;
    private final VisTextButton cancelButton;
    private final VisTextButton newButton;
    private final VisTextButton duplicateButton;
    private final VisTextButton renameButton;
    private final VisTextButton deleteButton;

    private static final int CODE_ROWS = 12;
    private static final float CODE_AREA_HEIGHT = 220f;
    private static final float FORM_LABEL_WIDTH = 100f;
    private static final float FORM_CONTROL_WIDTH = 120f;
    private static final float CODE_CONTENT_MIN_WIDTH = 720f;

    private boolean updatingUi;
    private ShaderVariant selectedVariant = ShaderVariant.DESKTOP_GL30;

    public ShaderManagerDialog(StudioApplicationAdapter app) {
        super("Custom Shaders");

        this.app = app;

        setModal(true);
        setMovable(true);
        setResizable(true);

        closeOnEscape();

        VisTable mainContent = new VisTable(true);
        mainContent.pad(8);
        mainContent.defaults().left();

        VisTable formTable = new VisTable();
        formTable.defaults().left().padBottom(4f);

        typeBox = new VisSelectBox<>();
        typeBox.setItems(ShaderKind.MATERIAL, ShaderKind.FX);
        typeBox.setSelected(ShaderKind.MATERIAL);
        formTable.add(new VisLabel("Shader type:")).width(FORM_LABEL_WIDTH).padRight(10f);
        formTable.add(typeBox).width(FORM_CONTROL_WIDTH).row();

        shaderBox = new VisSelectBox<>();
        formTable.add(new VisLabel("Shader name:")).width(FORM_LABEL_WIDTH).padRight(10f);
        formTable.add(shaderBox).width(FORM_CONTROL_WIDTH).row();

        mainContent.add(formTable).left().row();

        targetTabs = new TabbedPane("shader-tabs");
        addTargetTab(ShaderVariant.DESKTOP_GL30, "Desktop");
        addTargetTab(ShaderVariant.ES3_WEBGL2, "Android / HTML");

        targetTabs.addListener(new TabbedPaneListener() {
            @Override
            public void switchedTab(Tab tab) {
                showTargetContent((VariantTab) tab);
            }

            @Override
            public void removedTab(Tab tab) {
            }

            @Override
            public void removedAllTabs() {
            }
        });

        VisTable contentFrame = new VisTable();
        contentFrame.setBackground(VisUI.getSkin().getDrawable("tabbed-pane-frame"));
        contentFrame.pad(2f);
        contentFrame.add(targetContent).growX();

        VisTable targetArea = new VisTable();
        targetArea.add(targetTabs.getTabsPane()).left().growX().row();
        targetArea.add(contentFrame).growX().padTop(-1f).row();
        mainContent.add(targetArea).growX().row();

        VisLabel includeHintLabel = new VisLabel(
                "Material shaders can use #include \"pixscape_common.glsl\"."
        );
        mainContent.add(includeHintLabel).left().padTop(4f).row();
        showTargetContent((VariantTab) targetTabs.getActiveTab());

        mainScrollPane = new VisScrollPane(mainContent);
        mainScrollPane.setScrollingDisabled(true, false);
        mainScrollPane.setForceScroll(false, false);
        mainScrollPane.setFadeScrollBars(false);
        mainScrollPane.setFlickScroll(false);
        mainScrollPane.setCancelTouchFocus(false);

        testButton = new VisTextButton("Test current target");
        saveButton = new VisTextButton("Save");
        cancelButton = new VisTextButton("Cancel");
        newButton = new VisTextButton("New");
        duplicateButton = new VisTextButton("Duplicate");
        renameButton = new VisTextButton("Rename");
        deleteButton = new VisTextButton("Delete");

        testButton.setColor(CommonLayout.BUTTON_COLOR);
        saveButton.setColor(CommonLayout.BUTTON_COLOR);
        cancelButton.setColor(CommonLayout.BUTTON_COLOR);
        newButton.setColor(CommonLayout.BUTTON_COLOR);
        duplicateButton.setColor(CommonLayout.BUTTON_COLOR);
        renameButton.setColor(CommonLayout.BUTTON_COLOR);
        deleteButton.setColor(CommonLayout.BUTTON_COLOR);

        VisTable buttons = new VisTable(true);
        VisTable leftButtons = new VisTable(true);
        VisTable rightButtons = new VisTable(true);

        leftButtons.add(testButton);
        leftButtons.add(saveButton);
        leftButtons.add(cancelButton);

        rightButtons.add(newButton);
        rightButtons.add(duplicateButton);
        rightButtons.add(renameButton);
        rightButtons.add(deleteButton);

        buttons.add(leftButtons).left().expandX();
        buttons.add(rightButtons).right();

        VisTable shell = new VisTable();
        shell.add(mainScrollPane).grow().row();
        shell.add(buttons).growX().padTop(6f);

        add(shell).grow();

        hookListeners();
        refreshShaderList();
        updateUIFromSelection();

        pack();
        centerWindow();
        setSize(900, 720);
    }

    private void addTargetTab(final ShaderVariant variant, final String title) {
        VariantTab tab = new VariantTab(variant, title, buildVariantEditorTable(variant));
        targetTabs.add(tab);

        if (variant == ShaderVariant.DESKTOP_GL30) {
            targetTabs.switchTab(tab);
        }
    }

    private VisTable buildVariantEditorTable(ShaderVariant variant) {
        VisTable table = new VisTable(true);
        table.defaults().left().growX();

        ScrollableCodeEditor vertEditor = vertEditors.get(variant);
        if (vertEditor == null) {
            vertEditor = new ScrollableCodeEditor(CODE_ROWS, CODE_CONTENT_MIN_WIDTH);
            vertEditors.put(variant, vertEditor);
        }

        ScrollableCodeEditor fragEditor = fragEditors.get(variant);
        if (fragEditor == null) {
            fragEditor = new ScrollableCodeEditor(CODE_ROWS, CODE_CONTENT_MIN_WIDTH);
            fragEditors.put(variant, fragEditor);
        }

        table.add(new VisLabel("Vertex shader")).row();
        table.add(vertEditor).growX().height(CODE_AREA_HEIGHT).row();

        table.add(new VisLabel("Fragment shader")).padTop(8).row();
        table.add(fragEditor).growX().height(CODE_AREA_HEIGHT).row();

        return table;
    }

    private void showTargetContent(VariantTab tab) {
        if (tab == null) return;

        selectedVariant = tab.getVariant();
        targetContent.clear();
        targetContent.add(tab.getContentTable()).grow();
    }

    private void hookListeners() {
        typeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (updatingUi) return;
                refreshShaderList();
                updateUIFromSelection();
            }
        });

        shaderBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (updatingUi) return;
                updateUIFromSelection();
            }
        });

        newButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                startNewShader();
            }
        });

        duplicateButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                doDuplicateShader();
            }
        });

        renameButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                doRenameShader();
            }
        });

        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                doDeleteShader();
            }
        });

        testButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                doTestCompile();
            }
        });

        saveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                doSaveAndRegister();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeOut();
            }
        });
    }

    private void refreshShaderList() {
        Array<String> names = listProjectShaders(typeBox.getSelected());
        shaderBox.setItems(names);
    }

    private Array<String> listProjectShaders(ShaderKind kind) {
        Array<String> result = new Array<>();

        FileHandle categoryDir = getProjectShaderCategoryDir(kind);
        if (categoryDir == null || !categoryDir.exists() || !categoryDir.isDirectory()) {
            return result;
        }

        for (FileHandle child : categoryDir.list()) {
            if (child != null && child.isDirectory()) {
                result.add(child.name());
            }
        }

        result.sort(String::compareTo);
        return result;
    }

    private void updateUIFromSelection() {
        String shaderName = shaderBox.getSelected();

        if (shaderName == null || shaderName.isBlank()) {
            clearEditors();
        } else {
            loadVariantSources(shaderName, typeBox.getSelected());
        }
        updateActionAvailability();
    }

    private void startNewShader() {
        if (getStage() == null) return;

        StudioModalWindow dialog = new StudioModalWindow("New shader");
        dialog.setMovable(true);
        dialog.closeOnEscape();

        VisTextField input = new VisTextField();
        VisSelectBox<ShaderKind> kindBox = new VisSelectBox<>();
        kindBox.setItems(ShaderKind.MATERIAL, ShaderKind.FX);
        kindBox.setSelected(typeBox.getSelected());

        VisTable form = new VisTable(true);
        form.pad(10f);
        form.defaults().left();
        form.add(new VisLabel("Shader name:")).width(FORM_LABEL_WIDTH);
        form.add(input).width(320f).row();
        form.add(new VisLabel("Shader type:")).width(FORM_LABEL_WIDTH);
        form.add(kindBox).width(160f).left().row();

        VisTextButton create = new VisTextButton("Create");
        VisTextButton cancel = new VisTextButton("Cancel");
        create.setColor(CommonLayout.BUTTON_COLOR);
        cancel.setColor(CommonLayout.BUTTON_COLOR);
        VisTable actions = new VisTable(true);
        actions.add(create);
        actions.add(cancel);
        form.add(actions).colspan(2).right().padTop(8f);

        dialog.add(form).grow();
        dialog.pack();
        dialog.centerWindow();

        Runnable createAction = () -> createShaderFromDialog(dialog, input, kindBox);
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                createAction.run();
            }
        });
        cancel.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.remove();
            }
        });
        input.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ENTER && keycode != Input.Keys.NUMPAD_ENTER) return false;
                createAction.run();
                return true;
            }
        });

        getStage().addActor(dialog);
        getStage().setKeyboardFocus(input);
    }

    private void createShaderFromDialog(
            StudioModalWindow dialog,
            VisTextField input,
            VisSelectBox<ShaderKind> kindBox) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            Dialogs.showErrorDialog(dialog.getStage(), "No project loaded. Custom shaders are stored per project.");
            return;
        }

        String name = sanitizeName(input.getText());
        if (name.isEmpty()) {
            Dialogs.showErrorDialog(dialog.getStage(), "Shader name is required.");
            return;
        }

        ShaderKind kind = kindBox.getSelected();
        FileHandle shaderDir = getProjectShaderDir(kind, name);
        if (shaderDir == null) {
            Dialogs.showErrorDialog(dialog.getStage(), "The project shader directory is unavailable.");
            return;
        }
        if (shaderDir.exists()) {
            Dialogs.showErrorDialog(dialog.getStage(),
                    "A " + kind.name() + " shader named '" + name + "' already exists.");
            return;
        }

        try {
            createNewShaderAsset(name, kind);
            reloadRegistryAndNotify();
            selectShader(kind, name);
            dialog.remove();
        } catch (Exception ex) {
            Dialogs.showErrorDialog(
                    dialog.getStage(),
                    "Error while creating shader",
                    ex.getMessage() != null ? ex.getMessage() : ex.toString()
            );
        }
    }

    private void createNewShaderAsset(String name, ShaderKind kind) {
        FileHandle shaderDir = getProjectShaderDir(kind, name);
        if (shaderDir == null) {
            throw new IllegalStateException("The project shader directory is unavailable.");
        }
        if (shaderDir.exists()) {
            throw new IllegalStateException("A shader with that name already exists.");
        }

        writeNewShaderAsset(shaderDir, name, kind);
    }

    private void writeNewShaderAsset(FileHandle shaderDir, String name, ShaderKind kind) {
        try {
            shaderDir.mkdirs();
            if (!shaderDir.exists()) {
                throw new IllegalStateException("The shader directory could not be created.");
            }

            for (ShaderVariant variant : ShaderVariant.values()) {
                String prefix = variantFilePrefix(variant);
                String vertex = kind == ShaderKind.FX
                        ? templateFxVertex(variant)
                        : templateMaterialVertex(variant);
                String fragment = kind == ShaderKind.FX
                        ? templateFxFragment(variant)
                        : templateMaterialFragment(variant);
                shaderDir.child(prefix + ".vert").writeString(vertex, false, "UTF-8");
                shaderDir.child(prefix + ".frag").writeString(fragment, false, "UTF-8");
            }

            shaderDir.child("shader.json").writeString(buildShaderJson(name, kind), false, "UTF-8");
            shaderDir.child("includes").mkdirs();
            if (!shaderDir.child("includes").exists()) {
                throw new IllegalStateException("The shader includes directory could not be created.");
            }
        } catch (RuntimeException ex) {
            if (shaderDir.exists()) shaderDir.deleteDirectory();
            throw ex;
        }
    }

    private void selectShader(ShaderKind kind, String name) {
        updatingUi = true;
        try {
            typeBox.setSelected(kind);
            Array<String> names = listProjectShaders(kind);
            shaderBox.setItems(names);
            if (name != null && names.contains(name, false)) {
                shaderBox.setSelected(name);
            }
        } finally {
            updatingUi = false;
        }
        updateUIFromSelection();
    }

    private void loadVariantSources(String shaderName, ShaderKind kind) {
        FileHandle shaderDir = getProjectShaderDir(kind, shaderName);

        for (ShaderVariant variant : ShaderVariant.values()) {
            String prefix = variantFilePrefix(variant);

            FileHandle vertFile = shaderDir.child(prefix + ".vert");
            FileHandle fragFile = shaderDir.child(prefix + ".frag");

            vertEditors.get(variant).setText(vertFile.exists() ? vertFile.readString("UTF-8") : "");
            fragEditors.get(variant).setText(fragFile.exists() ? fragFile.readString("UTF-8") : "");
        }
    }

    private void clearEditors() {
        for (ShaderVariant variant : ShaderVariant.values()) {
            vertEditors.get(variant).setText("");
            fragEditors.get(variant).setText("");
        }
    }

    private void updateActionAvailability() {
        String selected = shaderBox.getSelected();
        boolean hasSelection = selected != null && !selected.isBlank();
        testButton.setDisabled(!hasSelection);
        saveButton.setDisabled(!hasSelection);
        duplicateButton.setDisabled(!hasSelection);
        renameButton.setDisabled(!hasSelection);
        deleteButton.setDisabled(!hasSelection);
    }

    private void doTestCompile() {
        String name = shaderBox.getSelected();
        if (name == null || name.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "No shader selected to test.");
            return;
        }

        String vertSource = vertEditors.get(selectedVariant).getText();
        String fragSource = fragEditors.get(selectedVariant).getText();

        if (vertSource == null || vertSource.trim().isEmpty()) {
            Dialogs.showErrorDialog(getStage(), "Vertex shader code is empty.");
            return;
        }
        if (fragSource == null || fragSource.trim().isEmpty()) {
            Dialogs.showErrorDialog(getStage(), "Fragment shader code is empty.");
            return;
        }

        try {
            if (selectedVariant == ShaderRegistry.getCurrentShaderVariant()) {
                ShaderRegistry.testCompile(name, vertSource, fragSource, ShaderMode.TEXTURE_ARRAY);
                Dialogs.showOKDialog(getStage(), "Shader compile", "Compilation OK");
            } else {
                FileHandle includesDir = com.badlogic.gdx.Gdx.files.internal(RuntimeFs.RUNTIME_DIR_SHADER_INCLUDES);
                ShaderSourcePreprocessor.preprocess(vertSource, null, includesDir);
                ShaderSourcePreprocessor.preprocess(fragSource, null, includesDir);

                Dialogs.showOKDialog(
                        getStage(),
                        "Shader source",
                        "Source preprocessing OK.\n\nReal GPU compile is only available on the current Studio backend."
                );
            }
        } catch (Exception ex) {
            Dialogs.showErrorDialog(
                    getStage(),
                    "Compile error",
                    ex.getMessage() != null ? ex.getMessage() : ex.toString()
            );
        }
    }

    private void doSaveAndRegister() {
        ShaderKind kind = typeBox.getSelected();

        String name = shaderBox.getSelected();
        if (name == null || name.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "No shader selected to save.");
            return;
        }

        for (ShaderVariant variant : ShaderVariant.values()) {
            String vertSource = vertEditors.get(variant).getText();
            String fragSource = fragEditors.get(variant).getText();

            if (vertSource == null || vertSource.trim().isEmpty()) {
                Dialogs.showErrorDialog(getStage(), "Missing vertex shader for " + variant + ".");
                return;
            }
            if (fragSource == null || fragSource.trim().isEmpty()) {
                Dialogs.showErrorDialog(getStage(), "Missing fragment shader for " + variant + ".");
                return;
            }
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "No project loaded. Custom shaders are stored per project.");
            return;
        }

        try {
            FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
            FileHandle shaderDir = getProjectShaderDir(kind, name);
            shaderDir.mkdirs();

            for (ShaderVariant variant : ShaderVariant.values()) {
                String prefix = variantFilePrefix(variant);

                shaderDir.child(prefix + ".vert")
                        .writeString(vertEditors.get(variant).getText(), false, "UTF-8");

                shaderDir.child(prefix + ".frag")
                        .writeString(fragEditors.get(variant).getText(), false, "UTF-8");
            }

            shaderDir.child("shader.json").writeString(buildShaderJson(name, kind), false, "UTF-8");
            shaderDir.child("includes").mkdirs();

            ShaderRegistry.reloadForProject(projectDir, StudioFs.DIR_ORIG_SHADERS);

            Dialogs.showOKDialog(getStage(), "Shader saved", "Shader '" + name + "' saved and registered.");

            EventFlow.i().publish(new EventFlow.ShaderListChanged(EventFlow.tag(this)));

            selectShader(kind, name);

        } catch (Exception ex) {
            Dialogs.showErrorDialog(
                    getStage(),
                    "Error while saving shader",
                    ex.getMessage() != null ? ex.getMessage() : ex.toString()
            );
        }
    }

    private void doDeleteShader() {
        ShaderKind kind = typeBox.getSelected();
        String name = shaderBox.getSelected();

        if (name == null || name.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "No shader selected to delete.");
            return;
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "No project loaded. Only project shaders can be deleted.");
            return;
        }

        FileHandle shaderDir = getProjectShaderDir(kind, name);
        if (shaderDir == null || !shaderDir.exists()) {
            Dialogs.showErrorDialog(getStage(), "Shader files could not be found.");
            return;
        }

        boolean deleted = shaderDir.deleteDirectory();
        if (!deleted) {
            Dialogs.showErrorDialog(getStage(), "Shader directory could not be deleted.");
            return;
        }

        app.getCanvas().getShaderService().detachShaderFromEcs(name, kind == ShaderKind.FX);

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        ShaderRegistry.reloadForProject(projectDir, StudioFs.DIR_ORIG_SHADERS);

        EventFlow.i().publish(new EventFlow.ShaderListChanged(EventFlow.tag(this)));

        Dialogs.showOKDialog(getStage(), "Shader deleted", "Shader '" + name + "' has been removed from the project.");

        refreshShaderList();
        updateUIFromSelection();
    }

    private void doDuplicateShader() {
        ShaderKind kind = typeBox.getSelected();
        String sourceName = shaderBox.getSelected();

        if (sourceName == null || sourceName.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "Select a shader to duplicate.");
            return;
        }

        promptForShaderName("Duplicate shader", sourceName + "_copy", "New shader name", entered -> {
            String newName = sanitizeName(entered);
            if (newName.isBlank()) {
                Dialogs.showErrorDialog(getStage(), "Shader name is required.");
                return false;
            }
            if (sourceName.equals(newName)) {
                Dialogs.showErrorDialog(getStage(), "New shader name must be different.");
                return false;
            }

            FileHandle sourceDir = getProjectShaderDir(kind, sourceName);
            FileHandle targetDir = getProjectShaderDir(kind, newName);

            if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
                Dialogs.showErrorDialog(getStage(), "Shader source directory could not be found.");
                return false;
            }
            if (targetDir == null) {
                Dialogs.showErrorDialog(getStage(), "The project shader directory is unavailable.");
                return false;
            }
            if (targetDir.exists()) {
                Dialogs.showErrorDialog(getStage(), "A shader with that name already exists.");
                return false;
            }

            ShaderSources sources = snapshotCurrentEditorSources();
            try {
                validateShaderSources(sources);
                writeDuplicatedShaderAsset(sourceDir, targetDir, newName, kind, sources);
                reloadRegistryAndNotify();
                selectShader(kind, newName);
                return true;
            } catch (Exception ex) {
                rollbackDuplicatedShader(targetDir, ex);
                Dialogs.showErrorDialog(
                        getStage(),
                        "Error while duplicating shader",
                        ex.getMessage() != null ? ex.getMessage() : ex.toString()
                );
                return false;
            }
        });
    }

    private ShaderSources snapshotCurrentEditorSources() {
        ShaderSources sources = new ShaderSources();
        for (ShaderVariant variant : ShaderVariant.values()) {
            String vertex = vertEditors.get(variant).getText();
            String fragment = fragEditors.get(variant).getText();
            sources.vertices.put(variant, vertex == null ? null : new String(vertex));
            sources.fragments.put(variant, fragment == null ? null : new String(fragment));
        }
        return sources;
    }

    private void validateShaderSources(ShaderSources sources) {
        for (ShaderVariant variant : ShaderVariant.values()) {
            String vertex = sources.vertices.get(variant);
            if (vertex == null || vertex.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing vertex shader for " + variant + ".");
            }
            String fragment = sources.fragments.get(variant);
            if (fragment == null || fragment.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing fragment shader for " + variant + ".");
            }
        }
    }

    private void writeDuplicatedShaderAsset(
            FileHandle sourceDir,
            FileHandle targetDir,
            String newName,
            ShaderKind kind,
            ShaderSources sources) {
        try {
            targetDir.mkdirs();
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                throw new IllegalStateException("The duplicated shader directory could not be created.");
            }

            for (ShaderVariant variant : ShaderVariant.values()) {
                String prefix = variantFilePrefix(variant);
                targetDir.child(prefix + ".vert")
                        .writeString(sources.vertices.get(variant), false, "UTF-8");
                targetDir.child(prefix + ".frag")
                        .writeString(sources.fragments.get(variant), false, "UTF-8");
            }

            targetDir.child("shader.json").writeString(buildShaderJson(newName, kind), false, "UTF-8");
            FileHandle targetIncludes = targetDir.child("includes");
            targetIncludes.mkdirs();
            copyDirectoryContents(sourceDir.child("includes"), targetIncludes);
            validateStructuredShaderDirectory(targetDir);
        } catch (RuntimeException ex) {
            rollbackDuplicatedShader(targetDir, ex);
            throw ex;
        }
    }

    private void copyDirectoryContents(FileHandle sourceDir, FileHandle targetDir) {
        if (!sourceDir.exists()) return;
        if (!sourceDir.isDirectory()) {
            throw new IllegalStateException("Shader includes path is not a directory: " + sourceDir.path());
        }

        for (FileHandle sourceChild : sourceDir.list()) {
            FileHandle targetChild = targetDir.child(sourceChild.name());
            if (sourceChild.isDirectory()) {
                targetChild.mkdirs();
                if (!targetChild.exists() || !targetChild.isDirectory()) {
                    throw new IllegalStateException("Could not create includes directory: " + targetChild.path());
                }
                copyDirectoryContents(sourceChild, targetChild);
            } else {
                targetChild.writeBytes(sourceChild.readBytes(), false);
                if (!targetChild.exists() || targetChild.isDirectory()) {
                    throw new IllegalStateException("Could not copy include file: " + sourceChild.path());
                }
            }
        }
    }

    private void validateStructuredShaderDirectory(FileHandle shaderDir) {
        for (ShaderVariant variant : ShaderVariant.values()) {
            String prefix = variantFilePrefix(variant);
            validateNonEmptyFile(shaderDir.child(prefix + ".vert"));
            validateNonEmptyFile(shaderDir.child(prefix + ".frag"));
        }
        validateNonEmptyFile(shaderDir.child("shader.json"));

        FileHandle includesDir = shaderDir.child("includes");
        if (!includesDir.exists() || !includesDir.isDirectory()) {
            throw new IllegalStateException("Missing shader includes directory: " + includesDir.path());
        }
    }

    private void validateNonEmptyFile(FileHandle file) {
        if (!file.exists() || file.isDirectory()) {
            throw new IllegalStateException("Missing shader file: " + file.name());
        }
        if (file.length() <= 0L) {
            throw new IllegalStateException("Shader file is empty: " + file.name());
        }
    }

    private void rollbackDuplicatedShader(FileHandle targetDir, Exception originalFailure) {
        if (targetDir == null || !targetDir.exists()) return;
        if (!targetDir.deleteDirectory() && Gdx.app != null) {
            Gdx.app.error(
                    "ShaderManagerDialog",
                    "Could not roll back duplicated shader directory: " + targetDir.path(),
                    originalFailure
            );
        }
    }

    private void doRenameShader() {
        ShaderKind kind = typeBox.getSelected();
        String sourceName = shaderBox.getSelected();

        if (sourceName == null || sourceName.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "Select a shader to rename.");
            return;
        }

        promptForShaderName("Rename shader", sourceName, "New shader name", entered -> {
            String targetName = sanitizeName(entered);
            if (targetName.isBlank()) {
                Dialogs.showErrorDialog(getStage(), "Shader name is required.");
                return false;
            }
            if (sourceName.equals(targetName)) {
                Dialogs.showErrorDialog(getStage(), "New shader name must be different.");
                return false;
            }

            FileHandle sourceDir = getProjectShaderDir(kind, sourceName);
            FileHandle targetDir = getProjectShaderDir(kind, targetName);

            if (targetDir.exists()) {
                Dialogs.showErrorDialog(getStage(), "A shader with that name already exists.");
                return false;
            }

            try {
                sourceDir.moveTo(targetDir);
                if (!targetDir.exists()) {
                    Dialogs.showErrorDialog(getStage(), "Shader directory rename failed.");
                    return false;
                }

                targetDir.child("shader.json").writeString(buildShaderJson(targetName, kind), false, "UTF-8");

                reloadRegistryAndNotify();
                selectShader(kind, targetName);
                return true;
            } catch (Exception ex) {
                Dialogs.showErrorDialog(
                        getStage(),
                        "Error while renaming shader",
                        ex.getMessage() != null ? ex.getMessage() : ex.toString()
                );
                return false;
            }
        });
    }

    private void promptForShaderName(String title, String initialValue, String label, NameAction action) {
        if (getStage() == null) return;

        final StudioModalWindow dialog = new StudioModalWindow(title);
        dialog.setMovable(true);

        final VisTextField input = new VisTextField(initialValue == null ? "" : initialValue);

        VisTable content = new VisTable(true);
        content.pad(10f);
        content.defaults().left().growX();
        content.add(new VisLabel(label)).row();
        content.add(input).width(320f).row();

        VisTextButton ok = new VisTextButton("OK");
        VisTextButton cancel = new VisTextButton("Cancel");

        ok.setColor(CommonLayout.BUTTON_COLOR);
        cancel.setColor(CommonLayout.BUTTON_COLOR);

        VisTable actions = new VisTable(true);
        actions.add(ok);
        actions.add(cancel);
        content.add(actions).right().padTop(8f);

        dialog.add(content).grow();
        dialog.pack();
        dialog.centerWindow();

        ok.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String value = input.getText();
                if (action.run(value == null ? "" : value)) {
                    dialog.remove();
                }
            }
        });

        cancel.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.remove();
            }
        });

        getStage().addActor(dialog);
        getStage().setKeyboardFocus(input);
    }

    private FileHandle getProjectShaderCategoryDir(ShaderKind kind) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            return null;
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle root = projectDir.child(StudioFs.DIR_ORIG_SHADERS).child("custom");

        return root.child(kind == ShaderKind.FX ? "fx" : "material");
    }

    private FileHandle getProjectShaderDir(ShaderKind kind, String shaderName) {
        FileHandle category = getProjectShaderCategoryDir(kind);
        if (category == null) return null;
        return category.child(shaderName);
    }

    private String variantFilePrefix(ShaderVariant variant) {
        switch (variant) {
            case DESKTOP_GL30:
                return RuntimeFs.SHADER_VARIANT_DESKTOP_GL30;
            case ES3_WEBGL2:
                return RuntimeFs.SHADER_VARIANT_ES3_WEBGL2;
            default:
                throw new IllegalArgumentException("Unknown shader variant: " + variant);
        }
    }

    private String sanitizeName(String raw) {
        if (raw == null) return "";

        String cleaned = raw.trim().toLowerCase()
                .replace('\\', '_')
                .replace('/', '_')
                .replace(' ', '_');

        cleaned = cleaned.replaceAll("[^a-z0-9_-]", "_");
        cleaned = cleaned.replaceAll("_+", "_");

        while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);

        return cleaned;
    }

    private String buildShaderJson(String name, ShaderKind kind) {
        String desktop = variantFilePrefix(ShaderVariant.DESKTOP_GL30);
        String es = variantFilePrefix(ShaderVariant.ES3_WEBGL2);

        return "{\n"
                + "  \"type\": \"pixscape-custom-shader\",\n"
                + "  \"version\": 1,\n"
                + "  \"name\": \"" + name + "\",\n"
                + "  \"kind\": \"" + (kind == ShaderKind.FX ? "fx" : "material") + "\",\n"
                + "  \"mode\": \"" + ShaderMode.TEXTURE_ARRAY.name() + "\",\n"
                + "  \"targets\": {\n"
                + "    \"" + desktop + "\": {\n"
                + "      \"vertex\": \"" + desktop + ".vert\",\n"
                + "      \"fragment\": \"" + desktop + ".frag\"\n"
                + "    },\n"
                + "    \"" + es + "\": {\n"
                + "      \"vertex\": \"" + es + ".vert\",\n"
                + "      \"fragment\": \"" + es + ".frag\"\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    private void reloadRegistryAndNotify() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        ShaderRegistry.reloadForProject(projectDir, StudioFs.DIR_ORIG_SHADERS);
        EventFlow.i().publish(new EventFlow.ShaderListChanged(EventFlow.tag(this)));
    }

    private String templateMaterialVertex(ShaderVariant variant) {
        if (variant == ShaderVariant.ES3_WEBGL2) {
            return "#version 300 es\n"
                    + "precision highp float;\n"
                    + "precision mediump int;\n\n"
                    + "in vec2  a_position;\n"
                    + "in vec2  a_texCoord0;\n"
                    + "in vec4  a_color;\n"
                    + "in float a_layer;\n\n"
                    + "uniform mat4 u_projTrans;\n\n"
                    + "out vec2  v_uv;\n"
                    + "out vec4  v_color;\n"
                    + "flat out int v_layer;\n\n"
                    + "void main() {\n"
                    + "    v_uv = a_texCoord0;\n"
                    + "    v_color = a_color;\n"
                    + "    v_layer = int(a_layer + 0.5);\n"
                    + "    gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);\n"
                    + "}\n";
        }

        return "#version 330 core\n\n"
                + "in vec2  a_position;\n"
                + "in vec2  a_texCoord0;\n"
                + "in vec4  a_color;\n"
                + "in float a_layer;\n\n"
                + "uniform mat4 u_projTrans;\n\n"
                + "out vec2  v_uv;\n"
                + "out vec4  v_color;\n"
                + "flat out int v_layer;\n\n"
                + "void main() {\n"
                + "    v_uv = a_texCoord0;\n"
                + "    v_color = a_color;\n"
                + "    v_layer = int(a_layer + 0.5);\n"
                + "    gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);\n"
                + "}\n";
    }

    private String templateFxVertex(ShaderVariant variant) {
        if (variant == ShaderVariant.ES3_WEBGL2) {
            return "#version 300 es\n"
                    + "precision mediump float;\n\n"
                    + "in vec4 a_position;\n"
                    + "in vec4 a_color;\n"
                    + "in vec2 a_texCoord0;\n\n"
                    + "uniform mat4 u_projTrans;\n\n"
                    + "out vec4 v_color;\n"
                    + "out vec2 v_uv;\n\n"
                    + "void main() {\n"
                    + "    v_color = a_color;\n"
                    + "    v_uv = a_texCoord0;\n"
                    + "    gl_Position = u_projTrans * a_position;\n"
                    + "}\n";
        }

        return "#version 330 core\n\n"
                + "in vec4 a_position;\n"
                + "in vec4 a_color;\n"
                + "in vec2 a_texCoord0;\n\n"
                + "uniform mat4 u_projTrans;\n\n"
                + "out vec4 v_color;\n"
                + "out vec2 v_uv;\n\n"
                + "void main() {\n"
                + "    v_color = a_color;\n"
                + "    v_uv = a_texCoord0;\n"
                + "    gl_Position = u_projTrans * a_position;\n"
                + "}\n";
    }

    private String templateMaterialFragment(ShaderVariant variant) {
        if (variant == ShaderVariant.ES3_WEBGL2) {
            return "#version 300 es\n"
                    + "precision mediump float;\n"
                    + "precision mediump int;\n\n"
                    + "#include \"pixscape_common.glsl\"\n\n"
                    + "in vec2  v_uv;\n"
                    + "in vec4  v_color;\n"
                    + "flat in int v_layer;\n\n"
                    + "uniform sampler2DArray u_array;\n"
                    + "uniform float u_cutoutThreshold;\n"
                    + "uniform vec3  u_ambientMul;\n\n"
                    + "out vec4 fragColor;\n\n"
                    + "void main() {\n"
                    + "    vec4 tex = texture(u_array, vec3(v_uv, v_layer));\n\n"
                    + "    if (u_cutoutThreshold >= 0.0 && tex.a < u_cutoutThreshold) discard;\n\n"
                    + "    fragColor = pixscapeApplyMaterial(tex, v_color, u_ambientMul);\n"
                    + "}\n";
        }

        return "#version 330 core\n\n"
                + "#include \"pixscape_common.glsl\"\n\n"
                + "in vec2  v_uv;\n"
                + "in vec4  v_color;\n"
                + "flat in int v_layer;\n\n"
                + "uniform sampler2DArray u_array;\n"
                + "uniform float u_cutoutThreshold;\n"
                + "uniform vec3  u_ambientMul;\n\n"
                + "out vec4 fragColor;\n\n"
                + "void main() {\n"
                + "    vec4 tex = texture(u_array, vec3(v_uv, v_layer));\n\n"
                + "    if (u_cutoutThreshold >= 0.0 && tex.a < u_cutoutThreshold) discard;\n\n"
                + "    fragColor = pixscapeApplyMaterial(tex, v_color, u_ambientMul);\n"
                + "}\n";
    }

    private String templateFxFragment(ShaderVariant variant) {
        String version = variant == ShaderVariant.ES3_WEBGL2
                ? "#version 300 es\nprecision mediump float;\n\n"
                : "#version 330 core\n\n";

        return version
                + "in vec2 v_uv;\n"
                + "in vec4 v_color;\n\n"
                + "uniform sampler2D u_texture;\n"
                + "uniform float u_time;\n"
                + "uniform float u_intensity;\n\n"
                + "out vec4 fragColor;\n\n"
                + "void main() {\n"
                + "    vec4 base = texture(u_texture, v_uv) * v_color;\n"
                + "    float pulse = 0.5 + 0.5 * sin(u_time * 4.0);\n"
                + "    vec3 tint = mix(vec3(1.0), vec3(1.0, 0.6, 1.2), pulse * clamp(u_intensity, 0.0, 1.0));\n"
                + "    fragColor = vec4(base.rgb * tint, base.a);\n"
                + "}\n";
    }
}
