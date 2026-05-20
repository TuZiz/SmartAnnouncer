package ym.smartannouncer.storage;

import java.time.Instant;

public interface AnnouncementDispatchStore extends AutoCloseable {
    AnnouncementDispatchStore DISABLED = new AnnouncementDispatchStore() {
        @Override
        public boolean claimOnce(String announcementId, String scopeKey, Instant dispatchAt) {
            return true;
        }

        @Override
        public boolean claimCooldown(String announcementId, String scopeKey, Instant dispatchAt, long cooldownSeconds) {
            return true;
        }

        @Override
        public void cleanup() {
        }

        @Override
        public void close() {
        }
    };

    boolean claimOnce(String announcementId, String scopeKey, Instant dispatchAt);

    boolean claimCooldown(String announcementId, String scopeKey, Instant dispatchAt, long cooldownSeconds);

    void cleanup();

    @Override
    void close();
}
