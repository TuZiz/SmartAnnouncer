package ym.smartannouncer.storage;

import java.time.Instant;

public interface AnnouncementDispatchStore extends AutoCloseable {
    AnnouncementDispatchStore DISABLED = new AnnouncementDispatchStore() {
        @Override
        public boolean claimDispatch(String announcementId, String bucketKey, Instant dispatchAt) {
            return true;
        }

        @Override
        public void cleanup() {
        }

        @Override
        public void close() {
        }
    };

    boolean claimDispatch(String announcementId, String bucketKey, Instant dispatchAt);

    void cleanup();

    @Override
    void close();
}
