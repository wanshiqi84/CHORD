import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChordPerformanceTest {

    public static void main(String[] args) {
        int ChordSize=50;
        List<Node> nodes = initializeChordNetwork(ChordSize);
        performLookupOperations(nodes,ChordSize);
        simulateNodeLeaves(nodes,5);
    }

    private static void performLookupOperations(List<Node> nodes, int size) {
        Random random = new Random();
        long totalLatency = 0;
        int successCount = 0;

    
        for (int i = 0; i < size; i++) { 
            long keyHash = Helper.hashString("key" + random.nextInt(1000)); 
            Node randomNode = nodes.get(random.nextInt(nodes.size()));
    
            long startTime = System.nanoTime();
            InetSocketAddress result = randomNode.searchSuccessor(keyHash);
            long endTime = System.nanoTime();
    
            long operationLatency = endTime - startTime;
            totalLatency += operationLatency;
    
            if (result != null) {
                successCount++;
                System.out.println("Key hash: " + keyHash + ", Found at Node: " + result + ", Latency: " + operationLatency + " ns");
            } else {
                System.out.println("Key hash: " + keyHash + " not found. Latency: " + operationLatency + " ns");
            }
        }
        double averageLatency = totalLatency / ((float)size);
        System.out.println("Average Latency: " + averageLatency + " ns");
    }
    
    private static void simulateNodeLeaves(List<Node> nodes, int operationsCount) {
        Random random = new Random();
    
        for (int i = 0; i < operationsCount; i++) {
            {
                // Node Leave
                if (nodes.size() > 1) {
                    int leaveIndex = random.nextInt(nodes.size());
                    Node leavingNode = nodes.get(leaveIndex);
                    leavingNode.stopAllThreads();
                    nodes.remove(leaveIndex);
                    System.out.println("Node left: " + leavingNode.getAddress());
                }
            }
            try {
                Thread.sleep(1000); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
private static List<Node> initializeChordNetwork(int nodeCount) {
    List<Node> nodes = new ArrayList<>();
    InetSocketAddress firstAddress = new InetSocketAddress("192.168.56.1", 8000);
    Node firstNode = new Node(firstAddress);
    firstNode.join(null); 
    nodes.add(firstNode);
    for (int i = 1; i < nodeCount; i++) {
        InetSocketAddress joiningPoint;
        InetSocketAddress address = new InetSocketAddress("192.168.56.1", 8000 + i);
        Node newNode = new Node(address);
        joiningPoint = Helper.createSocketAddress("192.168.56.1" + ":" +(8000 + i));
        newNode.join(joiningPoint); 
        nodes.add(newNode);

        try {
            Thread.sleep(100); 
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    return nodes;
}


}