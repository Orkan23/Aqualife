package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt2.broker.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {
    private ExecutorService threadPool;
    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private volatile boolean stopRequested = false;
    private final Endpoint endpoint;
    private volatile ClientCollection clientCollection;
    private int idCounter;
    private int NUM_THREADS = 5;

    public Broker() {
        threadPool = Executors.newFixedThreadPool(2);
        endpoint = new Endpoint(Properties.PORT);
        clientCollection = new ClientCollection();
        idCounter = 0;
    }

    public void broker() {
        threadPool.execute(() -> {
            JOptionPane.showMessageDialog(null, "Press OK to stop server");
            stopRequested = true;
            System.out.println("Broker has been stopped!");
        });

        while (!stopRequested) {
            Message msg = endpoint.blockingReceive();
            Serializable payload = msg.getPayload();
            InetSocketAddress sender = msg.getSender();
            System.out.print(sender + ": ");

            threadPool.execute(() -> {
                System.out.println(threadPool.toString());
                if (payload instanceof RegisterRequest) register(sender);
                else if (payload instanceof DeregisterRequest) deregister((DeregisterRequest) payload);
                else if (payload instanceof HandoffRequest) handoffFish((HandoffRequest) payload, sender);
                else if (payload instanceof PoisonPill) {
                    System.out.println("Stopped Broker via Poison Pill!");
                    threadPool.shutdown();
                    System.exit(0);
                } else System.out.println(payload.toString());
            });

        }
    }

    public void register(InetSocketAddress sender) {
        readWriteLock.writeLock().lock();  // if any other thread is reading or writing, wait before start writing
        String id = "Tank " + idCounter++ + ":" + sender.getPort();
        System.out.printf("Register new client as %s\n", id);
        clientCollection.add(id, sender);

        int currentIndex = clientCollection.indexOf(id);

        InetSocketAddress currentClient = (InetSocketAddress) clientCollection.getClient(currentIndex);
        InetSocketAddress leftNeighbor = getLeftNeighbor(currentIndex);
        InetSocketAddress rightNeighbor = getRightNeighbor(currentIndex);

        endpoint.send(currentClient, new NeighborUpdate(leftNeighbor, Direction.LEFT));
        endpoint.send(currentClient, new NeighborUpdate(rightNeighbor, Direction.RIGHT));

        if (clientCollection.size() > 1) {
            endpoint.send(leftNeighbor, new NeighborUpdate(sender, Direction.RIGHT));
            endpoint.send(rightNeighbor, new NeighborUpdate(sender, Direction.LEFT));
        } else endpoint.send(currentClient, new Token());

        endpoint.send(sender, new RegisterResponse(id));
        readWriteLock.writeLock().unlock();
    }

    public void deregister(DeregisterRequest deregisterRequest) {
        readWriteLock.writeLock().lock();
        System.out.printf("Deregister request of %s\n", deregisterRequest.getId());

        int index = clientCollection.indexOf(deregisterRequest.getId());
        if (index == -1) {
            System.out.println("An unregistered client tries to hand off a fish!");
            return;
        }

        InetSocketAddress leftNeighbor = getLeftNeighbor(index);
        InetSocketAddress rightNeighbor = getRightNeighbor(index);

        endpoint.send(rightNeighbor, new NeighborUpdate(leftNeighbor, Direction.LEFT));
        endpoint.send(leftNeighbor, new NeighborUpdate(rightNeighbor, Direction.RIGHT));

        clientCollection.remove(index);
        readWriteLock.writeLock().unlock();
    }

    private InetSocketAddress getLeftNeighbor(int index) {
        return (InetSocketAddress) clientCollection.getClient((index - 1 + clientCollection.size()) % clientCollection.size());
    }

    private InetSocketAddress getRightNeighbor(int index) {
        return (InetSocketAddress) clientCollection.getClient((index + 1) % clientCollection.size());
    }

    public void handoffFish(HandoffRequest handoffRequest, InetSocketAddress sender) {
        readWriteLock.readLock().lock();  // if any other thread is writing, wait before start reading
        int index = clientCollection.indexOf(sender);

        if (index == -1) {
            System.out.println("An unregistered client tries to hand off a fish!");
            return;
        }

        Direction direction = handoffRequest.getFish().getDirection();
        InetSocketAddress clientNeighbourAddress = (InetSocketAddress) (direction.equals(Direction.RIGHT) ? clientCollection.getRightNeighorOf(index) : clientCollection.getLeftNeighorOf(index));

        System.out.printf("Hand off %s to %s\n", handoffRequest.getFish().getId(), handoffRequest.getFish().getDirection());

        endpoint.send(clientNeighbourAddress, handoffRequest);
        readWriteLock.readLock().unlock();
    }

    public static void main(String[] args) {
        new Broker().broker();
    }
}
