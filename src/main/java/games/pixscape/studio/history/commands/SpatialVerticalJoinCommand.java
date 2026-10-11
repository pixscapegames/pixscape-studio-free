package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.history.*;
import games.pixscape.studio.service.spatial.*;

/** One vertical edit transaction; redo uses the recorded result, never another search. */
public final class SpatialVerticalJoinCommand implements Command, OutcomeAwareCommand, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry identities;
    private final long selectedMap,referenceMap;
    private final SpatialVerticalJoinProposal proposal;
    private final SpatialBlockLinkCommand edit;
    private int expectedRevision;
    private String expectedPhysics;
    private CommandOutcome outcome=CommandOutcome.NO_CHANGE;
    private String rejectionReason;
    public SpatialVerticalJoinCommand(World world,HistoryIdRegistry ids,SpatialVerticalJoinProposal proposal) {
        this.world=world;identities=ids;this.proposal=proposal;
        selectedMap=ids.ensureForEntity(proposal.selected.entity);referenceMap=ids.ensureForEntity(proposal.candidate.reference.entity);
        expectedRevision=proposal.selected.revision;
        expectedPhysics=proposal.selected.physics;
        edit=new SpatialBlockLinkCommand(world,ids,proposal.selected.entity,SpatialLinkProposal.editSnapshot(
                proposal.selected.blocks,proposal.after,proposal.selected.map,proposal.selectedId,proposal.selected.defaultHeight,proposal.defaultHeight));
    }
    @Override public String label() {return "Raccorder";}
    @Override public boolean isNoop() {return !proposal.valid || edit.isNoop();}
    public boolean wasApplied() {return outcome==CommandOutcome.APPLIED;}
    public String rejectionReason() {return rejectionReason;}
    @Override public void redo() {redoOutcome();}
    @Override public void undo() {undoOutcome();}
    @Override public CommandOutcome executeOutcome() {return redoOutcome();}
    @Override public CommandOutcome redoOutcome() {return outcome=apply(false);}
    @Override public CommandOutcome undoOutcome() {return outcome=apply(true);}
    private CommandOutcome apply(boolean undo) {
        rejectionReason=null;
        if(!proposal.valid) {rejectionReason=proposal.diagnostic;return CommandOutcome.REJECTED;}
        int source=identities.entityOfHistoryId(selectedMap),fixed=identities.entityOfHistoryId(referenceMap);
        games.pixscape.studio.configuration.ProjectConfig config=games.pixscape.studio.configuration.ProjectConfig.getInstance();
        float pixelsPerMeter=config!=null && config.getCurrentSceneMeta()!=null?config.getCurrentSceneMeta().pixelsPerMeter:0;
        if(source<0 || fixed<0 || !world.getEntityManager().isActive(source) || !world.getEntityManager().isActive(fixed)
                || !proposal.selected.matches(world,source,expectedRevision)
                || !expectedPhysics.equals(SpatialVerticalJoinProposal.MapState.physicsSignature(world,source))
                || pixelsPerMeter!=proposal.selected.pixelsPerMeter
                || source!=fixed && !proposal.candidate.reference.matches(world,fixed,proposal.candidate.reference.revision)) {
            rejectionReason="The map context changed after the preview. Reopen Raccorder. Nothing was modified.";
            return CommandOutcome.REJECTED;
        }
        SpatialBlocksComponent reference=world.getMapper(SpatialBlocksComponent.class).getSafe(fixed,null);
        if(reference==null) {rejectionReason="The reference ensemble is missing.";return CommandOutcome.REJECTED;}
        Json json=new Json();
        if(reference.blocks.size!=proposal.candidate.reference.blocks.blocks.size
                || !json.toJson(reference.links).equals(json.toJson(proposal.candidate.reference.blocks.links))) {
            rejectionReason="The reference map's volumes or links changed after the preview.";return CommandOutcome.REJECTED;
        }
        for(SpatialBlockData wall:proposal.candidate.reference.blocks.blocks) {
            if(source==fixed && proposal.affected.contains(wall.id))continue;
            if(!json.toJson(wall).equals(json.toJson(SpatialVerticalJoinProposal.find(reference,wall.id)))) {
                rejectionReason="The fixed reference or a disjoint volume changed after the preview.";return CommandOutcome.REJECTED;
            }
        }
        if(source!=fixed && world.getMapper(TiledLayerComponent.class).get(fixed).defaultTileHeight!=proposal.candidate.reference.defaultHeight) {
            rejectionReason="The reference map changed after the preview.";return CommandOutcome.REJECTED;
        }
        CommandOutcome result=undo?edit.undoOutcome():edit.redoOutcome();
        if(result==CommandOutcome.APPLIED) {
            expectedRevision=world.getMapper(TiledLayerComponent.class).get(source).data.contentRevision();
            expectedPhysics=SpatialVerticalJoinProposal.MapState.physicsSignature(world,source);
        }
        else rejectionReason=edit.rejectionReason();
        return result;
    }
}
