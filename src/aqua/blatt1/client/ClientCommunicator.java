package aqua.blatt1.client;

import java.net.InetSocketAddress;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

public class ClientCommunicator {
    private final Endpoint endpoint;

    public ClientCommunicator() {
        endpoint = new Endpoint();
    }

    public class ClientForwarder {
        private final InetSocketAddress broker;

        private ClientForwarder() {
            this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
        }

        public void register() {
            endpoint.send(broker, new RegisterRequest());
        }

        public void deregister(String id) {
            endpoint.send(broker, new DeregisterRequest(id));
        }

        /**
         * method which is used for the communication via broker
         */
        public void handOff(FishModel fish) {
            endpoint.send(broker, new HandoffRequest(fish));
        }

        /**
         * method which is used for the communication directly to neighbours
         */
        public void handOff(FishModel fish, InetSocketAddress neighbor) {
            endpoint.send(neighbor, new HandoffRequest(fish));
            System.out.printf("Hand off %s to %s\n", fish.getId(), neighbor.getPort());
        }

        public void handToken(InetSocketAddress neighbor) {
            endpoint.send(neighbor, new Token());
        }

        public void sendSnapshotMarker(InetSocketAddress neighbor) {
            endpoint.send(neighbor, new SnapshotMarker());
        }

        public void handSnapshotToken(InetSocketAddress neighbor, int count) {
            endpoint.send(neighbor, new SnapshotToken(count));
        }

    }

    public class ClientReceiver extends Thread {
        private final TankModel tankModel;

        private ClientReceiver(TankModel tankModel) {
            this.tankModel = tankModel;
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();

                if (msg.getPayload() instanceof RegisterResponse) {
                    System.out.printf("Client port: %s\n", ((RegisterResponse) msg.getPayload()).getId());
                    tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId());
                }

                if (msg.getPayload() instanceof HandoffRequest) {
                    tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());
                }

                if (msg.getPayload() instanceof NeighborUpdate)
                    if (((NeighborUpdate) msg.getPayload()).getDirection().equals(Direction.LEFT)) {
                        tankModel.setLeftNeighbor(((NeighborUpdate) msg.getPayload()).getNeighbor());

                        System.out.printf("Neighbour Left: %s\n", ((NeighborUpdate) msg.getPayload()).getNeighbor());
                    } else if (((NeighborUpdate) msg.getPayload()).getDirection().equals(Direction.RIGHT)) {
                        tankModel.setRightNeighbor(((NeighborUpdate) msg.getPayload()).getNeighbor());
                        System.out.printf("Neighbour Right: %s\n", ((NeighborUpdate) msg.getPayload()).getNeighbor());
                    }

                if (msg.getPayload() instanceof Token)
                    tankModel.receiveToken();

                if (msg.getPayload() instanceof SnapshotMarker)
                    tankModel.receiveSnapshotMarker(msg.getSender());

                if (msg.getPayload() instanceof SnapshotToken)
                    tankModel.receiveSnapshotToken(((SnapshotToken) msg.getPayload()).getCounter());

            }
            System.out.println("Receiver stopped.");
        }
    }

    public ClientForwarder newClientForwarder() {
        return new ClientForwarder();
    }

    public ClientReceiver newClientReceiver(TankModel tankModel) {
        return new ClientReceiver(tankModel);
    }

}
