import java.net.InetSocketAddress;
import java.util.Random;

public class NodeMaintenance extends Thread {

    private Node local;
    private Random random;
    private volatile boolean running = true;
    private volatile boolean alive = true;

    public NodeMaintenance(Node node) {
        this.local = node;
        this.random = new Random();
    }

    @Override
    public void run() {
        while (running) {
            // Update predecessor
            if (local.getPredecessor() != null && Helper.sendRequest(local.getPredecessor(), "KEEP") == null) {
                local.clearPredecessor();
            }

            // Fix fingers
            int i = random.nextInt(31) + 2;
            InetSocketAddress ithFinger = local.searchSuccessor(Helper.calculateNodePosition(local.getId(), i));
            if (i > 0 && i <= 32) {
                local.updateSingleFinger(i, ithFinger);
            }

            // Stabilize
            InetSocketAddress successor = local.getSuccessor();
            if (successor == null || successor.equals(local.getAddress())) {
                local.fillSuccessor();
            }
            successor = local.getSuccessor();
            if (successor != null && !successor.equals(local.getAddress())) {
                // Try to get my successor's predecessor
                InetSocketAddress x = Helper.requestAddress(successor, "YOURPRE");

                // If bad connection with successor, delete successor
                if (x == null) {
                    local.deleteSuccessor();
                } else if (!x.equals(successor)) {
                    // Successor's predecessor is not itself
                    long local_id = Helper.hashSocketAddress(local.getAddress());
                    long successor_relative_id = Helper.computeRelativeId(Helper.hashSocketAddress(successor), local_id);
                    long x_relative_id = Helper.computeRelativeId(Helper.hashSocketAddress(x), local_id);
                    if (x_relative_id > 0 && x_relative_id < successor_relative_id) {
                        local.updateSingleFinger(1,x);
                    }
                } else {
                    // Successor's predecessor is successor itself, then notify successor
                    local.processNodeNotification(successor,"NOTIFY");
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void stopNodeMaintenance() {
        running = false;
    }

    public void toDie() {
        alive = false;
    }
}
