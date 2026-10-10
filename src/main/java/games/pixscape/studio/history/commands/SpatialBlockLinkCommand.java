package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLink;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialLinkProposal;

/** One exact snapshot transaction: geometry, local links and the map's default height. */
public final class SpatialBlockLinkCommand implements Command, OutcomeAwareCommand, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry identities;
    private final long mapHistoryId;
    private final SpatialLinkProposal proposal;
    private final float beforeHeight;
    private CommandOutcome lastOutcome = CommandOutcome.NO_CHANGE;

    public boolean wasApplied() { return lastOutcome == CommandOutcome.APPLIED; }

    public SpatialBlockLinkCommand(World world, HistoryIdRegistry identities, int map, SpatialLinkProposal proposal) {
        this.world = world;
        this.identities = identities;
        mapHistoryId = identities.ensureForEntity(map);
        this.proposal = proposal;
        beforeHeight = proposal.beforeDefaultHeight;
    }

    @Override public String label() { return proposal.unlink ? "Délier" : "Lier"; }
    @Override public boolean isNoop() { return !proposal.valid; }
    @Override public void redo() { redoOutcome(); }
    @Override public void undo() { undoOutcome(); }
    @Override public CommandOutcome executeOutcome() { return redoOutcome(); }
    @Override public CommandOutcome redoOutcome() { return lastOutcome = publish(proposal.after, proposal.defaultHeight, false); }
    @Override public CommandOutcome undoOutcome() { return lastOutcome = publish(proposal.before, beforeHeight, true); }

    private CommandOutcome publish(SpatialBlocksComponent target, float height, boolean undo) {
        if (!proposal.valid) return CommandOutcome.REJECTED;
        int map = identities.entityOfHistoryId(mapHistoryId);
        if (map < 0 || !world.getEntityManager().isActive(map)) return CommandOutcome.NO_CHANGE;
        SpatialBlocksComponent current = world.getMapper(SpatialBlocksComponent.class).getSafe(map, null);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(map, null);
        if (tiled == null || !same(current, undo ? proposal.after : proposal.before)
                || Float.compare(tiled.defaultTileHeight, undo ? proposal.defaultHeight : beforeHeight) != 0)
            return CommandOutcome.REJECTED;
        CommandOutcome result = SpatialBlockCommandSupport.replaceAllValidated(world, map, target.blocks, target.links);
        if (result != CommandOutcome.APPLIED) return result;
        tiled.defaultTileHeight = height;
        if (tiled.data != null) {
            tiled.data.defaultTileHeight = height;
            tiled.data.markAllChunksContentDirty();
        }
        SpatialBlockCommandSupport.markChanged(world, map, this);
        games.pixscape.studio.event.EventFlow.i().publish(new games.pixscape.studio.event.EventFlow.LayerSpatialDepthChanged(
                map, games.pixscape.studio.event.EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private static boolean same(SpatialBlocksComponent a, SpatialBlocksComponent b) {
        if (a == null || a.blocks.size != b.blocks.size || a.links.size != b.links.size) return false;
        com.badlogic.gdx.utils.IntMap<SpatialBlockData> index = new com.badlogic.gdx.utils.IntMap<>();
        for (SpatialBlockData wall : a.blocks) index.put(wall.id, wall);
        for (SpatialBlockData wall : b.blocks) if (!SpatialBlockCommandSupport.same(index.get(wall.id), wall)) return false;
        for (int i = 0; i < a.links.size; i++) {
            SpatialBlockLink x = a.links.get(i), y = b.links.get(i);
            if (x.firstBlockId != y.firstBlockId || x.secondBlockId != y.secondBlockId
                    || x.firstFace != y.firstFace || x.secondFace != y.secondFace) return false;
        }
        return true;
    }
}
