package ym.smartannouncer.message;

import java.util.concurrent.atomic.AtomicReference;

public final class MessageRegistry {
    private final AtomicReference<MessageSnapshot> snapshot = new AtomicReference<>(MessageSnapshot.empty());

    public MessageSnapshot snapshot() {
        return snapshot.get();
    }

    public void replace(MessageSnapshot newSnapshot) {
        snapshot.set(newSnapshot);
    }
}
