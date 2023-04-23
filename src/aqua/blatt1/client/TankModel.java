package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordType;

public class TankModel extends Observable implements Iterable<FishModel> {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    protected final ClientCommunicator.ClientForwarder forwarder;

    protected InetSocketAddress leftNeighbor;
    protected InetSocketAddress rightNeighbor;

    protected boolean token;
    protected boolean snapshotToken;
    protected boolean globalSnaphsot;
    protected volatile boolean finishLocalSnaphsot;
    protected Timer timer;

    protected RecordType recordMode = RecordType.IDLE;

    protected int snapshotCounter;

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
    }

    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = Math.min(x, WIDTH - FishModel.getXSize() - 1);
            y = Math.min(y, HEIGHT - FishModel.getYSize());

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y, rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        if (recordMode != RecordType.IDLE)
            System.out.println("capture fish");
        snapshotCounter++;
        fish.setToStart();
        fishies.add(fish);
    }

    public synchronized void receiveToken() {
        System.out.println("Received Token");
        token = true;
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                token = false;
                forwarder.handToken(leftNeighbor);
                System.out.println("Handover token to " + leftNeighbor.getPort());
            }
        }, 2000);
    }

    public synchronized void initiateSnapshot() {
        snapshotCounter = fishies.size();
        recordMode = RecordType.BOTH;
        globalSnaphsot = false;
        snapshotToken = false;
        forwarder.sendSnapshotMarker(leftNeighbor);
        forwarder.sendSnapshotMarker(rightNeighbor);
        forwarder.handSnapshotToken(leftNeighbor, snapshotCounter);
    }

    public synchronized void receiveSnapshotMarker(InetSocketAddress sender) {
        switch (recordMode) {
            case IDLE:
                System.out.println("Received Snapshot Marker in IDLE to RIGHT/LEFT");
                snapshotCounter = fishies.size();
                finishLocalSnaphsot = false;
                forwarder.sendSnapshotMarker(leftNeighbor);
                forwarder.sendSnapshotMarker(rightNeighbor);
                recordMode = sender.equals(leftNeighbor) ? RecordType.RIGHT : RecordType.LEFT;
                break;
            case RIGHT:
                System.out.println("Received Snapshot Marker in RIGHT to IDLE");
                if (sender.equals(rightNeighbor)) recordMode = RecordType.IDLE;
                finishLocalSnaphsot = true;
                System.out.println("Finished local Snapshot! Local counter: " + snapshotCounter);
                break;
            case LEFT:
                System.out.println("Received Snapshot Marker in LEFT to IDLE");
                if (sender.equals(leftNeighbor)) recordMode = RecordType.IDLE;
                finishLocalSnaphsot = true;
                System.out.println("Finished local Snapshot! Local counter: " + snapshotCounter);
                break;
            case BOTH:
                System.out.println("Received Snapshot Marker in BOTH to RIGHT/LEFT");
                recordMode = sender.equals(leftNeighbor) ? RecordType.RIGHT : RecordType.LEFT;
                finishLocalSnaphsot = false;
                break;
        }
    }

    public synchronized void receiveSnapshotToken(int counter) {
        snapshotToken = true;
        while (!finishLocalSnaphsot) Thread.onSpinWait();
        forwarder.handSnapshotToken(leftNeighbor, counter + snapshotCounter);
        snapshotToken = false;
    }


    public synchronized boolean hasToken() {
        return token;
    }

    public synchronized boolean hasSnapshotToken() {
        return snapshotToken;
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge()) if (hasToken()) {
                if (rightNeighbor != null && fish.getDirection().equals(Direction.RIGHT))
                    forwarder.handOff(fish, rightNeighbor);
                else if (leftNeighbor != null && fish.getDirection().equals(Direction.LEFT))
                    forwarder.handOff(fish, leftNeighbor);
            } else {
                fish.reverse();
            }

            if (fish.disappears()) it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

    public synchronized void setLeftNeighbor(InetSocketAddress neighbor) {
        this.leftNeighbor = neighbor;
    }

    public synchronized void setRightNeighbor(InetSocketAddress neighbor) {
        this.rightNeighbor = neighbor;
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }


}