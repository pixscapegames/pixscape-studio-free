package games.pixscape.studio.event;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class EventFlowSubscriptionScopeTest {
    @Test
    public void scopedListenerReceivesOnlyWhileItsSceneIsActiveAndUnsubscribesOnClose() {
        EventFlow flow = EventFlow.i();
        flow.discardPending();
        AtomicBoolean active = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        EventFlow.SubscriptionScope scope = flow.newSubscriptionScope(active::get);
        scope.run(() -> flow.subscribe(EventFlow.LayerOrderChanged.class, event -> calls.incrementAndGet()));

        flow.publish(new EventFlow.LayerOrderChanged(1));
        flow.flush();
        assertEquals(0, calls.get());

        active.set(true);
        flow.publish(new EventFlow.LayerOrderChanged(2));
        flow.flush();
        assertEquals(1, calls.get());

        scope.close();
        flow.publish(new EventFlow.LayerOrderChanged(3));
        flow.flush();
        assertEquals(1, calls.get());
    }
}
