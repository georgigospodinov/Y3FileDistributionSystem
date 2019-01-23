package communication.structures;

/**
 * Maintains status for a seeder on the network.
 * This includes for how many periods has the seeder been unresponsive
 * and whether this host has the most up to date list of torrents.
 *
 * @version 1.2
 */
public class SeederStatus {

    public static final int UP_TO_DATE = 2;
    public static final int BEING_UPDATED = 1;
    public static final int OUT_OF_DATE = 0;
    private static final int DEFAULT_PERIODS = 0;
    private static final int DECLARE_DEAD_PERIODS = 3;
    private int periodsWithoutHellos = DEFAULT_PERIODS;
    private int upToDate = 0;

    public void countPeriod() {
        periodsWithoutHellos++;
    }

    public void resetPeriods() {
        periodsWithoutHellos = DEFAULT_PERIODS;
    }

    public boolean isDead() {
        return periodsWithoutHellos == DECLARE_DEAD_PERIODS;
    }

    public int getPeriodsWithoutHellos() {
        return periodsWithoutHellos;
    }

    public void outOfDate() {
        upToDate = OUT_OF_DATE;
    }

    public void beingUpdated() {
        upToDate = BEING_UPDATED;
    }

    public void updated() {
        upToDate = UP_TO_DATE;
    }

    public boolean isUpToDate() {
        return upToDate == UP_TO_DATE;
    }

    public boolean isBeingUpdated() {
        return upToDate == BEING_UPDATED;
    }

    public boolean isOutOfDate() {
        return upToDate == OUT_OF_DATE;
    }

    public int getUpToDate() {
        return upToDate;
    }

    @Override
    public String toString() {
        return "{periods=" + periodsWithoutHellos + ", upToDate=" + upToDate + "}";
    }

}
