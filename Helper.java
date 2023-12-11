import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

public class Helper {
    
    private static final int BITWISE_MODULO = 32;

    private static final HashMap<Integer, Long> powerOfTwo = new HashMap<>();

    static {
        long base = 1;
        for (int i = 0; i <= 32; i++) {
            powerOfTwo.put(i, base);
            base *= 2;
        }
    }


    public static long hashSocketAddress(InetSocketAddress addr) {
        int i = addr.hashCode();
        return compressIntToHash(i);
    }

    public static long hashString(String s) {
        int i = s.hashCode();
        return compressIntToHash(i);
    }
/**
 * Compresses an integer into a compact hash value using SHA-1 algorithm.
 * Takes an integer, applies SHA-1 hashing, and then compresses the hash to a long value 
 * by using the first 4 bytes of the hash.
 * */
    private static long compressIntToHash(int i) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = ByteBuffer.allocate(4).putInt(i).array();
            md.update(bytes);
            byte[] hash = md.digest();
            return ByteBuffer.wrap(Arrays.copyOf(hash, 4)).getInt() & 0xFFFFFFFFL;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to compute hash", e);
        }
    }
	
    public static long computeRelativeId(long universal, long local) {
        long difference = universal - local;
        if (difference < 0) {
            difference += powerOfTwo.get(BITWISE_MODULO);
        }
        return difference;
    }

    public static String hexIdAndPosition(InetSocketAddress addr) {
        long hash = hashSocketAddress(addr);
        return (longTo8DigitHex(hash) + " (" + hash * 100 / Helper.calculatePowerOfTwo(32) + "%)");
    }

	public static String longTo8DigitHex(long l) {
		return String.format("%08x", l);
	}
	
    public static long calculateNodePosition(long nodeid, int i) {
        return (nodeid + powerOfTwo.get(i - 1)) % powerOfTwo.get(32);
    }

    public static long calculatePowerOfTwo(int k) {
        return powerOfTwo.get(k);
    }
    public static InetSocketAddress requestAddress (InetSocketAddress server, String req) {

		// invalid input, return null
		if (server == null || req == null) {
			return null;
		}

		// send request to server
		String response = sendRequest(server, req);

		// if response is null, return null
		if (response == null) {
			return null;
		}

		// or server cannot find anything, return server itself 
		else if (response.startsWith("NOTHING"))
			return server;

		// server find something, 
		// using response to create, might fail then and return null
		else {
			InetSocketAddress ret = Helper.createSocketAddress(response.split("_")[1]);
			return ret;
		}
	}


	public static String sendRequest(InetSocketAddress server, String req) {

		// invalid input
		if (server == null || req == null)
			return null;

		Socket talkSocket = null;

		// try to open talkSocket, output request to this socket
		// return null if fail to do so
		try {
			talkSocket = new Socket(server.getAddress(),server.getPort());
			PrintStream output = new PrintStream(talkSocket.getOutputStream());
			output.println(req);
		} catch (IOException e) {
			//System.out.println("\nCannot send request to "+server.toString()+"\nRequest is: "+req+"\n");
			return null;
		}

		// sleep for a short time, waiting for response
		try {
			Thread.sleep(60);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// get input stream, try to read something from it
		InputStream input = null;
		try {
			input = talkSocket.getInputStream();
		} catch (IOException e) {
			System.out.println("Cannot get input stream from "+server.toString()+"\nRequest is: "+req+"\n");
		}
		String response = Helper.inputStreamToString(input);

		// try to close socket
		try {
			talkSocket.close();
		} catch (IOException e) {
			throw new RuntimeException(
					"Cannot close socket", e);
		}
		return response;
	}
    public static InetSocketAddress createSocketAddress(String addr) {
        if (addr == null || !addr.contains(":")) {
            return null;
        }

        String[] parts = addr.split(":", 2);
        String ip = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];
        int port;

        try {
            port = Integer.parseInt(parts[1]);
            InetAddress ipAddress = InetAddress.getByName(ip);
            return new InetSocketAddress(ipAddress, port);
        } catch (UnknownHostException | NumberFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String inputStreamToString(InputStream in) {
        if (in == null) {
            return null;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line = null;
        try {
            line = reader.readLine();
        } catch (IOException e) {
            System.out.println("Cannot read line from input stream.");
            return null;
        }

        return line;
    }
}
