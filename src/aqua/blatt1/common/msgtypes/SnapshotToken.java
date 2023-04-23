package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public class SnapshotToken implements Serializable {
    private int counter;
    private InetSocketAddress initiator;

    public SnapshotToken(int counter) {
        this.initiator = initiator;
        this.counter = counter;
    }

    public InetSocketAddress getInitiator() {
        return initiator;
    }

    public int getCounter() {
        return counter;
    }
}
