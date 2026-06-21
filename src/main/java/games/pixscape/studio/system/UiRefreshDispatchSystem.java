package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;

import java.util.List;

public class UiRefreshDispatchSystem extends BaseSystem implements ProfiledSystem {
    private final List<AfterEcsStep> listeners = new java.util.ArrayList<>();
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public void add(AfterEcsStep l) {
        if (l != null) listeners.add(l);
    }

    public void remove(AfterEcsStep l) {
        listeners.remove(l);
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.UI_REFRESH_DISPATCH);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.UI_REFRESH_DISPATCH, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        for (int i = 0, n = listeners.size(); i < n; i++) {
            try {
                listeners.get(i).afterEcsStep();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
