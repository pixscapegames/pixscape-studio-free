package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLinks;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.SpatialBlockLinkCommand;

/** Direct menu transaction, keeping the first selected volume as the fixed reference. */
public final class SpatialVolumeLinkAction {
    private SpatialVolumeLinkAction() {
    }

    /** Returns a short refusal, or null after one successfully published history operation. */
    public static String apply(World world, HistoryManager history, int map, int first, int second, boolean unlink) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(map, null);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(map, null);
        if (tiled == null || tiled.data == null || blocks == null) {
            return "The map is no longer available.";
        }
        SpatialLinkProposal proposal = SpatialLinkProposal.create(blocks, tiled.data, second, first, unlink, tiled.defaultTileHeight);
        if (!proposal.valid) {
            return refusal(proposal.diagnostic);
        }
        // The shared solver also serves gestures. A direct menu action must never match thickness implicitly.
        if (!unlink && changesThickness(proposal)) {
            return "Use Match thickness before linking these volumes.";
        }
        SpatialBlockLinkCommand command = new SpatialBlockLinkCommand(world, history.historyIds(), map, proposal);
        history.execute(command);
        return command.wasApplied() ? null : refusal(command.rejectionReason());
    }

    /** Copies thickness only; height and voluntary links remain unchanged. */
    public static String matchThickness(World world, HistoryManager history, int map, int first, int second) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(map, null);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(map, null);
        if (tiled == null || tiled.data == null || blocks == null) {
            return "The map is no longer available.";
        }
        SpatialBlockData reference = null, selected = null;
        for (SpatialBlockData wall : blocks.blocks) {
            if (wall.id == first) {
                reference = wall;
            }
            if (wall.id == second) {
                selected = wall;
            }
        }
        if (reference == null || selected == null || first == second) {
            return "Select two volumes in the same map.";
        }
        if (SpatialBlockLinks.near(reference.width, reference.depth)
                || SpatialBlockLinks.near(selected.width, selected.depth)) {
            return "Square volumes have no clear wall direction.";
        }
        float thickness = Math.min(reference.width, reference.depth);
        if (Float.compare(thickness, Math.min(selected.width, selected.depth)) == 0) {
            return "The volumes already have the same thickness.";
        }
        if (thickness >= Math.max(selected.width, selected.depth)) {
            return "This thickness would change the wall direction.";
        }
        SpatialWallEditSession session = new SpatialWallEditSession();
        boolean horizontal = selected.width > selected.depth;
        if (!session.begin(map, second, blocks, tiled.data)
                || !session.updateProperty(null, null, horizontal ? null : thickness, horizontal ? thickness : null)) {
            String reason = refusal(session.rejectionReason());
            session.cancel();
            return reason;
        }
        SpatialBlocksComponent after = SpatialLinkProposal.copy(blocks);
        after.blocks = session.candidateTopology().walls;
        session.cancel();
        for (SpatialBlockData wall : after.blocks) {
            if (wall.id == first && (wall.x != reference.x || wall.y != reference.y
                    || wall.width != reference.width || wall.depth != reference.depth)) {
                return "This would also move the reference volume. Adjust the connected walls first.";
            }
        }
        SpatialLinkProposal proposal = SpatialLinkProposal.editSnapshot(blocks, after, tiled.data, second,
                tiled.defaultTileHeight, tiled.defaultTileHeight);
        SpatialBlockLinkCommand command = new SpatialBlockLinkCommand(world, history.historyIds(), map, proposal, "Match thickness");
        history.execute(command);
        return command.wasApplied() ? null : refusal(command.rejectionReason());
    }

    private static boolean changesThickness(SpatialLinkProposal proposal) {
        IntMap<SpatialBlockData> before = new IntMap<>();
        for (SpatialBlockData wall : proposal.before.blocks) {
            before.put(wall.id, wall);
        }
        for (SpatialBlockData wall : proposal.after.blocks) {
            SpatialBlockData previous = before.get(wall.id);
            if (previous == null || Float.compare(Math.min(previous.width, previous.depth), Math.min(wall.width, wall.depth)) != 0) {
                return true;
            }
        }
        return false;
    }

    // Never expose solver diagnostics containing IDs, bounds or member lists in the menu flow.
    private static String refusal(String diagnostic) {
        if (diagnostic == null) {
            return "The volumes could not be updated. Select them again and retry.";
        }
        if (diagnostic.startsWith("Select two volumes")) {
            return "Select two volumes in the same map.";
        }
        if (diagnostic.startsWith("Different base altitudes")) {
            return "The volumes must have the same base altitude.";
        }
        if (diagnostic.startsWith("No unambiguous lateral contact")) {
            return "The volumes must share a clear side contact.";
        }
        if (diagnostic.startsWith("Square volumes")) {
            return "Square volumes have no clear wall direction.";
        }
        if (diagnostic.startsWith("These volumes already have")) {
            return "These volumes are already linked.";
        }
        if (diagnostic.startsWith("There is no voluntary link")) {
            return "These volumes are not linked.";
        }
        if (diagnostic.contains("thickness") || diagnostic.startsWith("Thickness")) {
            return "The wall thicknesses are incompatible. Adjust them before linking.";
        }
        if (diagnostic.startsWith("A previous junction") || diagnostic.startsWith("Corner faces")) {
            return "This would break an existing wall junction.";
        }
        if (diagnostic.startsWith("Adaptation would connect")) {
            return "This would also connect another group of volumes.";
        }
        if (diagnostic.startsWith("The volumes or default height changed")) {
            return "The volumes changed. Select them again and retry.";
        }
        return "The change conflicts with the existing geometry or physics shapes.";
    }
}
