package ym.smartannouncer.platform;

public interface TaskHandle {
    TaskHandle NOOP = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void cancel();

    boolean isCancelled();
}
