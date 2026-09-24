package games.pixscape.studio.service.atlas;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** Deterministic scheduling; work still executes on an actual non-caller thread. */
public final class ControlledAtlasExecutor extends AbstractExecutorService {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private boolean stopped;
    @Override public void execute(Runnable task) { tasks.add(task); }
    public int queued() { return tasks.size(); }
    public Thread startNext() {
        Thread worker = new Thread(tasks.remove(), "controlled-atlas-worker");
        worker.start();
        return worker;
    }
    public void runNext() throws InterruptedException { join(startNext()); }
    public static void join(Thread worker) throws InterruptedException {
        worker.join(10000);
        if (worker.isAlive()) throw new AssertionError("Atlas worker did not finish");
    }
    @Override public void shutdown() { stopped = true; }
    @Override public List<Runnable> shutdownNow() { stopped = true; var result = List.copyOf(tasks); tasks.clear(); return result; }
    @Override public boolean isShutdown() { return stopped; }
    @Override public boolean isTerminated() { return stopped; }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
}
