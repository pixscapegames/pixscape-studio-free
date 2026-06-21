package games.pixscape.studio.service;

import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;

public final class ProjectConfigSceneMetaBridge {

    private final int MY_TAG = EventFlow.tag(this);

    public ProjectConfigSceneMetaBridge() {
        EventFlow.i().subscribe(EventFlow.SceneNameChanged.class, ev -> {
            ProjectConfig cfg = ProjectConfig.getInstance();
            if (cfg == null) return;
            cfg.renameScene(ev.oldName(), ev.newName());
        });

        EventFlow.i().subscribe(EventFlow.SceneDescriptionChanged.class, ev -> {
            ProjectConfig cfg = ProjectConfig.getInstance();
            if (cfg == null) return;
            SceneMeta ui = cfg.getCurrentSceneMeta();
            if (ui == null) return;
            ui.setDescription(ev.newDescription());
        });
    }

    /**
     * Call on every model-side scene change.
     */
    public void pushCurrentSceneMetaToUI() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;
        SceneMeta meta = cfg.getCurrentSceneMeta();
        if (meta == null) return;

        EventFlow.i().publish(new EventFlow.CurrentSceneMeta(
                meta.getName(),
                meta.getDescription(),
                MY_TAG
        ));
    }
}
