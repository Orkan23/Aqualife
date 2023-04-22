package aqua.blatt1.common.msgtypes;


import aqua.blatt1.common.Direction;

import java.io.Serializable;
import java.net.InetSocketAddress;

@SuppressWarnings("serial")
public final class NeighborUpdate implements Serializable {

    private InetSocketAddress neighbor;
    private Direction direction;

    public NeighborUpdate(InetSocketAddress neighbor, Direction direction) {
        this.neighbor = neighbor;
        this.direction = direction;
    }

    public InetSocketAddress getNeighbor() {
        return neighbor;
    }

    public Direction getDirection() {
        return direction;
    }
}
