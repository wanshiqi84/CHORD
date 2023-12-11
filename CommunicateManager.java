import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommunicateManager extends Thread {

    private final Node local;
    private ServerSocketChannel serverSocketChannel;
    private final ExecutorService threadPool;
    private volatile boolean alive;

    public CommunicateManager(Node local, int threadPoolSize) {
        this.local = local;
        this.alive = true;
        this.threadPool = Executors.newFixedThreadPool(threadPoolSize);
        initializeServerSocket();
    }

    private void initializeServerSocket() {
        InetSocketAddress localAddress = local.getAddress();
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(localAddress);
            serverSocketChannel.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException("Error opening server socket at " + localAddress, e);
        }
    }

    @Override
    public void run() {
        while (alive) {
            try {
                SocketChannel talkSocketChannel = serverSocketChannel.accept();
                if (talkSocketChannel != null) {
                    Socket talkSocket = talkSocketChannel.socket();
                    threadPool.execute(() -> {
                        try {
                            handleSocketCommunication(talkSocket);
                        } catch (IOException e) {
                            System.err.println("Communication error on server port " + local.getAddress().getPort() + " and client port " + talkSocket.getPort());
                        }
                    });
                }
            } catch (IOException e) {
                if (!alive) {
                    break;
                }
            }
        }
    }

    private void handleSocketCommunication(Socket socket) throws IOException {
        try (InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream()) {
            String request = Helper.inputStreamToString(input);
            String response = processRequest(request);
            if (response != null) {
                output.write(response.getBytes());
            }
        }
    }

    private String processRequest(String request) {
        if (request == null) {
            return null;
        }
    
        String[] parts = request.split("_");
        String command = parts[0];
        InetSocketAddress result;
    
        switch (command) {
            case "CLOSEST":
                long idClosest = Long.parseLong(parts[1]);
                result = local.closestPrecedingFinger(idClosest);
                return formatResponse("MYCLOSEST", result);
    
            case "YOURSUCC":
                result = local.getSuccessor();
                return result != null ? formatResponse("MYSUCC", result) : "NOTHING";
    
            case "FINDPRE":
                result = local.getPredecessor();
                return result != null ? formatResponse("PRERES", result) : "NOTHING";
    
            case "FINDSUCC":
                long idFindSucc = Long.parseLong(parts[1]);
                result = local.searchSuccessor(idFindSucc);
                return formatResponse("FOUNDSUCC", result);
    
            case "PREFOUND":
                InetSocketAddress newPre = Helper.createSocketAddress(parts[1]);
                local.processNodeNotification(newPre, "NOTIFIED");
                return "NOTIFIED";
    
            case "KEEP":
                return "ALIVE";
    
            default:
                return null;
        }
    }

    private String formatResponse(String prefix, InetSocketAddress address) {
        String ip = address.getAddress().toString();
        int port = address.getPort();
        return prefix + "_" + ip + ":" + port;
    }

    private void shutdownThreadPool() {
        threadPool.shutdown();
    }

    public void toDie() {
        alive = false;
        try {
            this.shutdownThreadPool();
            serverSocketChannel.close();
        } catch (IOException e) {
        }
    }
}
