import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Chord {

    private static Node currentNode;
    private static InetSocketAddress joiningPoint;
    private static Helper utility;

    public static void main(String[] args) {
        System.out.println("Starting DHT Node Setup...");
        utility = new Helper();

        // Obtain the IP address of the current machine
        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        // Initialize the current node
        currentNode = new Node(Helper.createSocketAddress(ip + ":" + args[0]));

        // Determine whether to initiate a new ring or join an existing one
        if (args.length == 1) {
            joiningPoint = currentNode.getAddress();
        } else if (args.length == 3) {
            joiningPoint = Helper.createSocketAddress(args[1] + ":" + args[2]);
            if (joiningPoint == null) {
                System.out.println("Failed to locate the joining address. Exiting.");
                return;
            }
        } else {
            System.out.println("Invalid arguments. Exiting.");
            System.exit(0);
        }

        // Attempt to join the DHT ring
        if (!currentNode.join(joiningPoint)) {
            System.out.println("Unable to join the DHT ring at the provided address. Exiting.");
            System.exit(0);
        }

        // Display joining information
        System.out.println("Joined the DHT ring.");
        System.out.println("Node IP Address: " + ip);
        currentNode.printNeighbors();

        // Handling user commands
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.println("\nType 'info' to display node information or 'exit' to leave the DHT ring:");
            String command = input.next();
            if ("exit".equals(command)) {
                currentNode.stopAllThreads();
                System.out.println("Node exiting the DHT ring...");
                System.exit(0);
            } else if ("info".equals(command)) {
                currentNode.printDataStructure();
            }
        }
    }
}
