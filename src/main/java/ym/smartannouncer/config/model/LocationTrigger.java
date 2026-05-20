package ym.smartannouncer.config.model;

public enum LocationTrigger {
    ENTER,
    LEAVE,
    BOTH;

    public boolean firesOnEnter() {
        return this == ENTER || this == BOTH;
    }

    public boolean firesOnLeave() {
        return this == LEAVE || this == BOTH;
    }
}
