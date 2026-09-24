package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.Tooltip;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;

import java.util.List;
import java.util.Objects;

/** Contextual native menu over existing transactional table structure commands. */
final class HudTableContextMenu {
    private static PopupMenu activeMenu;
    private static Context activeContext;

    private HudTableContextMenu() { }

    static void show(Stage stage, float x, float y, HudEditorSession session,
                     String clickedCellId, boolean rowOnly) {
        close();
        if (stage == null || session == null || session.isTestMode()
                || session.status() != HudEditorSession.Status.READY) return;
        HudDocumentV1 document = session.document();
        HudLayoutAuthoring.CellPosition position =
                HudLayoutAuthoring.cellPosition(document, clickedCellId);
        if (position == null) return;
        List<String> range = List.copyOf(session.selectedCellRange());
        Context context = new Context(session, document, session.screenId(), clickedCellId,
                position.tableId(), session.selectedCellId(), range);
        PopupMenu menu = new PopupMenu();
        int column = position.column();
        add(menu, "Insert row before", HudLayoutAuthoring.StructureAction.INSERT_ROW_BEFORE,
                context, clickedCellId, clickedCellId, column);
        add(menu, "Insert row after", HudLayoutAuthoring.StructureAction.INSERT_ROW_AFTER,
                context, clickedCellId, clickedCellId, column);
        add(menu, "Delete row", HudLayoutAuthoring.StructureAction.DELETE_ROW,
                context, clickedCellId, clickedCellId, column);
        if (!rowOnly) {
            add(menu, "Insert column before", HudLayoutAuthoring.StructureAction.INSERT_COLUMN_BEFORE,
                    context, clickedCellId, clickedCellId, column);
            add(menu, "Insert column after", HudLayoutAuthoring.StructureAction.INSERT_COLUMN_AFTER,
                    context, clickedCellId, clickedCellId, column);
            if (position.colspan() == 1) {
                add(menu, "Delete column", HudLayoutAuthoring.StructureAction.DELETE_COLUMN,
                        context, clickedCellId, clickedCellId, column);
            } else {
                PopupMenu columns = new PopupMenu();
                for (int logical = column; logical < position.endColumn(); logical++) {
                    add(columns, "Column " + (logical + 1),
                            HudLayoutAuthoring.StructureAction.DELETE_COLUMN,
                            context, clickedCellId, clickedCellId, logical);
                }
                MenuItem delete = new MenuItem("Delete column");
                delete.setSubMenu(columns);
                menu.addItem(delete);
            }
            String first = range.size() > 1 ? range.get(0) : clickedCellId;
            String last = range.size() > 1 ? range.get(range.size() - 1) : clickedCellId;
            add(menu, "Merge selected cells", HudLayoutAuthoring.StructureAction.MERGE,
                    context, first, last, column);
            add(menu, "Split cell", HudLayoutAuthoring.StructureAction.SPLIT,
                    context, clickedCellId, clickedCellId, column);
        }
        activeContext = context;
        activeMenu = menu;
        menu.showMenu(stage, x, y);
    }

    private static void add(PopupMenu menu, String title, HudLayoutAuthoring.StructureAction action,
                            Context context, String anchorId, String otherId, int column) {
        MenuItem item = new MenuItem(title);
        String rejection = HudLayoutAuthoring.structureRejection(context.document,
                action, anchorId, otherId, column);
        if (rejection != null) {
            item.setDisabled(true);
            Tooltip tip = new Tooltip.Builder(rejection).target(item).build();
            tip.setAppearDelayTime(0f);
        }
        item.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (item.isDisabled() || !valid(context)) { event.handle(); return; }
                context.session.editTableStructure(action, anchorId, otherId, column);
                close();
                event.handle();
            }
        });
        menu.addItem(item);
    }

    static void closeIfStale(HudEditorSession session) {
        if (activeContext != null && activeContext.session == session && !valid(activeContext)) close();
    }

    static PopupMenu activeMenu() { return activeMenu; }

    private static boolean valid(Context context) {
        HudEditorSession session = context.session;
        HudLayoutAuthoring.CellPosition current =
                HudLayoutAuthoring.cellPosition(session.document(), context.clickedCellId);
        return activeContext == context && !session.isTestMode()
                && session.status() == HudEditorSession.Status.READY
                && session.document() == context.document
                && Objects.equals(session.screenId(), context.screenId)
                && Objects.equals(session.selectedCellId(), context.selectedCellId)
                && session.selectedCellRange().equals(context.selectedRange)
                && current != null && context.tableId.equals(current.tableId());
    }

    private static void close() {
        if (activeMenu != null) activeMenu.remove();
        activeMenu = null;
        activeContext = null;
    }

    private record Context(HudEditorSession session, HudDocumentV1 document, String screenId,
                           String clickedCellId, String tableId, String selectedCellId,
                           List<String> selectedRange) { }
}
