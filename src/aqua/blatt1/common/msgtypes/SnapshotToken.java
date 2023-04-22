package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public class SnapshotToken implements Serializable {
    private int counter;

    public SnapshotToken(int counter) {
        this.counter = counter;
    }

    public int getCounter() {
        return counter;
    }
}
