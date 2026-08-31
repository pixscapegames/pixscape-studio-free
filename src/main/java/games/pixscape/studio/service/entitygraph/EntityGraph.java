package games.pixscape.studio.service.entitygraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record EntityGraph(List<EntityGraphEntry> entries) {
    public EntityGraph(List<EntityGraphEntry> entries) {
        List<EntityGraphEntry> copied = new ArrayList<>(entries);
        Set<Integer> sourceIds = new HashSet<>();
        for (EntityGraphEntry entry : copied) {
            if (!sourceIds.add(entry.sourceEntityId())) {
                throw new IllegalArgumentException("Entity graph source IDs must be unique.");
            }
        }
        for (EntityGraphEntry entry : copied) {
            if (entry.parentSourceEntityId() != -1
                    && !sourceIds.contains(entry.parentSourceEntityId())) {
                throw new IllegalArgumentException("Entity graph parent source ID must resolve inside the graph.");
            }
        }
        this.entries = Collections.unmodifiableList(copied);
    }

    public static EntityGraph empty() {
        return new EntityGraph(Collections.emptyList());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }
}
