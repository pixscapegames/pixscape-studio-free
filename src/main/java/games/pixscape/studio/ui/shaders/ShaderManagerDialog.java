package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
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

public class ShaderManagerDialog extends VisWindow {

    private enum ShaderKind {
        MATERIAL,
        FX
    }

    private interface NameAction {
        void run(String value);
    }

    private final StudioApplicationAdapter app;

    private final VisSelectBox<ShaderKind> typeBox;
    private final VisSelectBox<String> shaderBox;
    private final VisTextField nameField;

    private final TabbedPane targetTabs;
    private final VisTable targetContent = new VisTable(true);

    private final ObjectMap<ShaderVariant, VisTextArea> vertAreas = new ObjectMap<>();
    private final ObjectMap<ShaderVariant, VisTextArea> fragAreas = new ObjectMap<>();

    private final VisTextButton testButton;
    private final VisTextButton saveButton;
    private final VisTextButton cancelButton;
    private final VisTextButton newButton;
    private final VisTextButton duplicateButton;
    private final VisTextButton renameButton;
    private final VisTextButton deleteButton;

    private static final float CODE_ROWS = 12f;
    private static final float CODE_AREA_HEIGHT = 220f;

    private boolean creatingNew = false;
    private ShaderVariant selectedVariant = ShaderVariant.DESKTOP_GL30;

    public ShaderManagerDialog(StudioApplicationAdapter app) {
        super("Custom Shaders");

        this.app = app;

        setModal(true);
        setMovable(true);
        setResizable(true);
        addCloseButton();
        closeOnEscape();

        VisTable root = new VisTable(true);
        root.pad(8);
        root.defaults().left().growX();

        root.add(new VisLabel("Shader kind")).row();
        typeBox = new VisSelectBox<>();
        typeBox.setItems(ShaderKind.MATERIAL, ShaderKind.FX);
        typeBox.setSelected(ShaderKind.MATERIAL);
        root.add(typeBox).width(280).row();

        root.add(new VisLabel("Project shader")).row();
        shaderBox = new VisSelectBox<>();
        root.add(shaderBox).width(280).row();

        root.add(new VisLabel("Name")).row();
        nameField = new VisTextField();
        root.add(nameField).width(280).row();

        targetTabs = new TabbedPane();
        addTargetTab(ShaderVariant.DESKTOP_GL30, "Desktop GL30");
        addTargetTab(ShaderVariant.ES3_WEBGL2, "Android ES3 / HTML WebGL2");

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

        root.add(targetTabs.getTabsPane()).growX().row();

        VisScrollPane scrollPane = new VisScrollPane(targetContent);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setCancelTouchFocus(false);
        scrollPane.setFlickScroll(false);
        root.add(scrollPane).grow().row();
        showTargetContent((VariantTab) targetTabs.getActiveTab());

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

        root.add(buttons).growX().padTop(6);

        add(root).grow();

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

        VisTextArea vertArea = vertAreas.get(variant);
        if (vertArea == null) {
            vertArea = new VisTextArea();
            vertArea.setPrefRows(CODE_ROWS);
            vertAreas.put(variant, vertArea);
        }

        VisTextArea fragArea = fragAreas.get(variant);
        if (fragArea == null) {
            fragArea = new VisTextArea();
            fragArea.setPrefRows(CODE_ROWS);
            fragAreas.put(variant, fragArea);
        }

        table.add(new VisLabel("Vertex shader")).row();
        table.add(createCodeScrollPane(vertArea)).growX().height(CODE_AREA_HEIGHT).row();

        table.add(new VisLabel("Fragment shader")).padTop(8).row();
        table.add(createCodeScrollPane(fragArea)).growX().height(CODE_AREA_HEIGHT).row();

        table.add(new VisLabel("Material shaders can use #include \"pixscape_common.glsl\".")).padTop(8).row();

        return table;
    }

    private void showTargetContent(VariantTab tab) {
        if (tab == null) return;

        selectedVariant = tab.getVariant();
        targetContent.clear();
        targetContent.add(tab.getContentTable()).grow();
    }

    private VisScrollPane createCodeScrollPane(VisTextArea area) {
        VisScrollPane scrollPane = new VisScrollPane(area);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setCancelTouchFocus(false);
        scrollPane.setFlickScroll(false);
        scrollPane.setScrollingDisabled(false, false);
        return scrollPane;
    }

    private void hookListeners() {
        typeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                creatingNew = false;
                refreshShaderList();
                updateUIFromSelection();
            }
        });

        shaderBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                creatingNew = false;
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

        if (names.size > 0) {
            shaderBox.setSelected(names.first());
            creatingNew = false;
        } else {
            creatingNew = true;
        }
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

        if (creatingNew || shaderName == null || shaderName.isBlank()) {
            nameField.setDisabled(false);
            nameField.setText("");
            fillTemplates(typeBox.getSelected());
            return;
        }

        nameField.setDisabled(true);
        nameField.setText(shaderName);
        loadVariantSources(shaderName, typeBox.getSelected());
    }

    private void startNewShader() {
        creatingNew = true;
        nameField.setDisabled(false);
        nameField.setText("");
        fillTemplates(typeBox.getSelected());
    }

    private void loadVariantSources(String shaderName, ShaderKind kind) {
        FileHandle shaderDir = getProjectShaderDir(kind, shaderName);

        for (ShaderVariant variant : ShaderVariant.values()) {
            String prefix = variantFilePrefix(variant);

            FileHandle vertFile = shaderDir.child(prefix + ".vert");
            FileHandle fragFile = shaderDir.child(prefix + ".frag");

            VisTextArea vertArea = vertAreas.get(variant);
            VisTextArea fragArea = fragAreas.get(variant);

            vertArea.setText(vertFile.exists() ? vertFile.readString("UTF-8") : "");
            fragArea.setText(fragFile.exists() ? fragFile.readString("UTF-8") : "");
        }
    }

    private void fillTemplates(ShaderKind kind) {
        for (ShaderVariant variant : ShaderVariant.values()) {
            VisTextArea vertArea = vertAreas.get(variant);
            VisTextArea fragArea = fragAreas.get(variant);

            if (kind == ShaderKind.FX) {
                vertArea.setText(templateFxVertex(variant));
                fragArea.setText(templateFxFragment(variant));
            } else {
                vertArea.setText(templateMaterialVertex(variant));
                fragArea.setText(templateMaterialFragment(variant));
            }
        }
    }

    private void doTestCompile() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            Dialogs.showErrorDialog(getStage(), "Shader name is required.");
            return;
        }

        String vertSource = vertAreas.get(selectedVariant).getText();
        String fragSource = fragAreas.get(selectedVariant).getText();

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

        String name = sanitizeName(nameField.getText().trim());
        if (name.isEmpty()) {
            Dialogs.showErrorDialog(getStage(), "Shader name is required.");
            return;
        }

        for (ShaderVariant variant : ShaderVariant.values()) {
            String vertSource = vertAreas.get(variant).getText();
            String fragSource = fragAreas.get(variant).getText();

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
                        .writeString(vertAreas.get(variant).getText(), false, "UTF-8");

                shaderDir.child(prefix + ".frag")
                        .writeString(fragAreas.get(variant).getText(), false, "UTF-8");
            }

            shaderDir.child("shader.json").writeString(buildShaderJson(name, kind), false, "UTF-8");
            shaderDir.child("includes").mkdirs();

            ShaderRegistry.reloadForProject(projectDir, StudioFs.DIR_ORIG_SHADERS);

            Dialogs.showOKDialog(getStage(), "Shader saved", "Shader '" + name + "' saved and registered.");

            EventFlow.i().publish(new EventFlow.ShaderListChanged(EventFlow.tag(this)));

            creatingNew = false;
            refreshShaderList();
            shaderBox.setSelected(name);
            updateUIFromSelection();

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
                return;
            }
            if (sourceName.equals(newName)) {
                Dialogs.showErrorDialog(getStage(), "New shader name must be different.");
                return;
            }

            FileHandle sourceDir = getProjectShaderDir(kind, sourceName);
            FileHandle targetDir = getProjectShaderDir(kind, newName);

            if (targetDir.exists()) {
                Dialogs.showErrorDialog(getStage(), "A shader with that name already exists.");
                return;
            }

            sourceDir.copyTo(targetDir);
            targetDir.child("shader.json").writeString(buildShaderJson(newName, kind), false, "UTF-8");
            targetDir.child("includes").mkdirs();

            reloadRegistryAndNotify();
            refreshShaderList();
            shaderBox.setSelected(newName);
            creatingNew = false;
            updateUIFromSelection();
        });
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
                return;
            }
            if (sourceName.equals(targetName)) {
                Dialogs.showErrorDialog(getStage(), "New shader name must be different.");
                return;
            }

            FileHandle sourceDir = getProjectShaderDir(kind, sourceName);
            FileHandle targetDir = getProjectShaderDir(kind, targetName);

            if (targetDir.exists()) {
                Dialogs.showErrorDialog(getStage(), "A shader with that name already exists.");
                return;
            }

            sourceDir.moveTo(targetDir);
            if (!targetDir.exists()) {
                Dialogs.showErrorDialog(getStage(), "Shader directory rename failed.");
                return;
            }

            targetDir.child("shader.json").writeString(buildShaderJson(targetName, kind), false, "UTF-8");

            reloadRegistryAndNotify();
            refreshShaderList();
            shaderBox.setSelected(targetName);
            creatingNew = false;
            updateUIFromSelection();
        });
    }

    private void promptForShaderName(String title, String initialValue, String label, NameAction action) {
        if (getStage() == null) return;

        final VisWindow dialog = new VisWindow(title);
        dialog.setModal(true);
        dialog.setMovable(true);
        dialog.addCloseButton();

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
                dialog.remove();
                action.run(value == null ? "" : value);
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
