package games.pixscape.studio.service.tiled;

import com.sun.management.ThreadMXBean;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.lang.management.ManagementFactory;

public class TiledSpatialMutationPerformanceTest {
    @Test
    public void reportGestureAndLinkedReferenceCosts() {
        benchmarkGesture(1, 1);
        benchmarkGesture(100, 10);
        benchmarkGesture(10_000, 100);
        benchmarkLinkedScan(10_000);
    }

    private static void benchmarkGesture(int count, int width) {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.data = new TiledMapLayerData(width, Math.max(1, (count + width - 1) / width), 32, 16, 16);
        TiledBrushSession warmup = session(tiled, Math.min(count, 100), width);
        warmup.toPlan();

        long allocationBefore = allocatedBytes();
        long canonicalStart = System.nanoTime();
        TiledMutationPlan plan = session(tiled, count, width).toPlan();
        long canonicalNs = System.nanoTime() - canonicalStart;
        TiledSpatialMutationPlanner planner = new TiledSpatialMutationPlanner();
        long validationStart = System.nanoTime();
        TiledSpatialMutationPlanner.Result result =
                planner.validateAndCommit(1, tiled.data, null, plan, true);
        long validationCommitNs = System.nanoTime() - validationStart;
        long allocated = allocatedBytes() - allocationBefore;

        tiled.data.beginAtomicMutation();
        for (int i = 0; i < plan.size(); i++) {
            tiled.data.setTileStaged(plan.gx(i), plan.gy(i), 0, (byte) 0);
        }
        long rollbackStart = System.nanoTime();
        tiled.data.rollbackAtomicMutation();
        long rollbackNs = System.nanoTime() - rollbackStart;
        Assert.assertTrue(result.accepted());
        System.out.println("TILED_MUTATION_PERF cells=" + count
                + " canonicalizationNs=" + canonicalNs
                + " validationAndCommitNs=" + validationCommitNs
                + " rollbackNs=" + rollbackNs
                + " allocatedBytes=" + allocated);
    }

    private static void benchmarkLinkedScan(int refs) {
        int width = 100;
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.data = new TiledMapLayerData(width, refs / width, 32, 16, 16);
        tiled.data.beginAtomicMutation();
        for (int i = 0; i < refs; i++) tiled.data.setTileStaged(i % width, i / width, 1, (byte) 0);
        tiled.data.commitAtomicMutation();
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 1;
        wall.structureId = 1;
        wall.beginAuthoredLinkedTileRefs();
        for (int i = 0; i < refs; i++) wall.addLinkedTileRef(i % width, i / width, 1);
        blocks.blocks.add(wall);
        TiledBrushSession erase = new TiledBrushSession(1);
        for (int i = 0; i < refs; i++) erase.apply(tiled, i % width, i / width, 0);
        TiledMutationPlan plan = erase.toPlan();
        TiledSpatialMutationPlanner planner = new TiledSpatialMutationPlanner();
        long start = System.nanoTime();
        TiledSpatialMutationPlanner.Result result = planner.preflight(plan, blocks, true);
        long scanNs = System.nanoTime() - start;
        Assert.assertEquals(TiledSpatialMutationPlanner.Status.REJECTED_LINKED_ANCHOR, result.status());
        System.out.println("TILED_MUTATION_PERF linkedRefs=" + refs + " linkedScanNs=" + scanNs);
    }

    private static TiledBrushSession session(TiledLayerComponent tiled, int count, int width) {
        TiledBrushSession session = new TiledBrushSession(1);
        for (int i = 0; i < count; i++) session.apply(tiled, i % width, i / width, 7);
        return session;
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof ThreadMXBean)) return 0L;
        ThreadMXBean allocationBean = (ThreadMXBean) bean;
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
