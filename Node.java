	import java.net.InetSocketAddress;
	import java.text.SimpleDateFormat;
	import java.util.Date;
	import java.util.HashMap;

	public class Node {

		private final long localId;
		private final InetSocketAddress localSocketAddress;
		private InetSocketAddress predecessor;
		private final HashMap<Integer, InetSocketAddress> fingerTable;
		private final CommunicateManager communicateManager;
		private final NodeMaintenance nodeMaintenance;

		public Node(InetSocketAddress address) {
			localSocketAddress = address;
			localId = Helper.hashSocketAddress(localSocketAddress);
			fingerTable = initializeFingerTable();
			predecessor = null;
			communicateManager = new CommunicateManager(this, 10);
			nodeMaintenance = new NodeMaintenance(this);
		}

		private HashMap<Integer, InetSocketAddress> initializeFingerTable() {
			int initialCapacity = 32;
			HashMap<Integer, InetSocketAddress> newFingerTable = new HashMap<>(initialCapacity);
			for (int i = 1; i <= initialCapacity; i++) {
				newFingerTable.put(i, null);
			}
			return newFingerTable;
		}

		public boolean join(InetSocketAddress contact) {
			if (contact == null) {
				System.out.println("Contact address is null. Join operation cannot proceed.");
				return false;
			}
			System.out.println("Attempting to join. Contact address: " + contact);
			if (!contact.equals(localSocketAddress)) {
				InetSocketAddress successor = Helper.requestAddress(contact, "FINDSUCC_" + localId);
				if (successor == null) {
					System.out.println("Unable to find the successor for the provided address.");
					return false;
				}
				updateSingleFinger(1, successor);
			}
			ThreadsRun();
			return true;
		}
		
		private void ThreadsRun() {
			if (communicateManager != null) {
				communicateManager.start();
			}
			if (nodeMaintenance != null) {
				nodeMaintenance.start();
			}
		}
		public void processNodeNotification(InetSocketAddress otherNode, String notificationType) {
			if (otherNode == null) {
				System.out.println("The provided node address is null.");
				return;
			}
		
			if (notificationType == null || notificationType.isEmpty()) {
				System.out.println("Notification type is null or empty.");
				return;
			}
		
			if ("notify".equalsIgnoreCase(notificationType)) {
				if (!otherNode.equals(localSocketAddress)) {
					Helper.sendRequest(otherNode, "PREFOUND_" + localSocketAddress.getAddress().toString() + ":" + localSocketAddress.getPort());
				}
			} else if ("notified".equalsIgnoreCase(notificationType)) {
				long oldPreId = predecessor != null ? Helper.hashSocketAddress(predecessor) : -1;
				if (predecessor == null || predecessor.equals(localSocketAddress) || isPredecessorUpdateRequired(otherNode, oldPreId)) {
					setPredecessor(otherNode);
				}
			} else {
				System.out.println("Invalid notification type provided: " + notificationType);
			}
		}
		
		private boolean isPredecessorUpdateRequired(InetSocketAddress newPre, long oldPreId) {
			long localRelativeId = Helper.computeRelativeId(localId, oldPreId);
			long newPreRelativeId = Helper.computeRelativeId(Helper.hashSocketAddress(newPre), oldPreId);
			return newPreRelativeId > 0 && newPreRelativeId < localRelativeId;
		}


		public InetSocketAddress searchSuccessor(long id) {
			InetSocketAddress ret = this.getSuccessor();
			InetSocketAddress pre = findPredecessor(id);
			if (!pre.equals(localSocketAddress))
				ret = Helper.requestAddress(pre, "YOURSUCC");
			if (ret == null)
				ret = localSocketAddress;
			return ret;
		}


		private InetSocketAddress findPredecessor(long targetId) {
			InetSocketAddress currentNode = this.localSocketAddress;
			InetSocketAddress currentNodeSuccessor = this.getSuccessor();
			InetSocketAddress targetNode = this.localSocketAddress;
			long targetNodeId = computeNodeId(currentNode, currentNodeSuccessor);
			long findidRelativeId = Helper.computeRelativeId(targetId, Helper.hashSocketAddress(currentNode));
		
			while (!(findidRelativeId > 0 && findidRelativeId <= targetNodeId)) {
				InetSocketAddress previousNode = currentNode;
				currentNode = updateCurrentNode(currentNode, targetId, targetNode);
				if (previousNode.equals(currentNode)) break;
		
				currentNodeSuccessor = Helper.requestAddress(currentNode, "YOURSUCC");
				targetNodeId = computeNodeId(currentNode, currentNodeSuccessor);
				findidRelativeId = Helper.computeRelativeId(targetId, Helper.hashSocketAddress(currentNode));
			}
		
			return currentNode;
		}
		
		private long computeNodeId(InetSocketAddress currentNode, InetSocketAddress currentNodeSuccessor) {
			if (currentNodeSuccessor != null) {
				return Helper.computeRelativeId(Helper.hashSocketAddress(currentNodeSuccessor), Helper.hashSocketAddress(currentNode));
			}
			return 0;
		}
		
		private InetSocketAddress updateCurrentNode(InetSocketAddress currentNode, long findid, InetSocketAddress targetNode) {
			if (currentNode.equals(this.localSocketAddress)) {
				return this.closestPrecedingFinger(findid);
			} else {
				InetSocketAddress result = Helper.requestAddress(currentNode, "CLOSEST_" + findid);
				if (result == null || result.equals(currentNode)) {
					return targetNode;
				} else {
					return result;
				}
			}
		}

	public InetSocketAddress closestPrecedingFinger(long findId) {
			long findIdRelative = Helper.computeRelativeId(findId, localId);

			for (int i = 32; i > 0; i--) {
				InetSocketAddress finger = getValidFinger(i, findIdRelative);
				if (finger != null) {
					return finger;
				}
			}
			return localSocketAddress;
		}

		private InetSocketAddress getValidFinger(int index, long findIdRelative) {
			InetSocketAddress finger = fingerTable.get(index);
			if (finger == null) return null;

			long fingerId = Helper.hashSocketAddress(finger);
			long fingerRelativeId = Helper.computeRelativeId(fingerId, localId);

			if (isFingerValid(fingerRelativeId, findIdRelative)) {
				return checkFingerAlive(finger) ? finger : null;
			}
			return null;
		}

		private boolean isFingerValid(long fingerRelativeId, long findIdRelative) {
			return fingerRelativeId > 0 && fingerRelativeId < findIdRelative;
		}

		private boolean checkFingerAlive(InetSocketAddress finger) {
			String response = Helper.sendRequest(finger, "KEEP");
			if (response != null && response.equals("ALIVE")) {
				return true;
			} else {
				deleteCertainFinger(finger);
				return false;
			}
		}


		public void updateSingleFinger(int i, InetSocketAddress value) {
			fingerTable.put(i, value);
			if (i == 1 && value != null && !value.equals(localSocketAddress)) {
				processNodeNotification(value,"NOTIFY");
			}
		}


		public void deleteSuccessor() {
			InetSocketAddress successor = getSuccessor();
			if (successor == null) return;
		
			int breakIndex = findBreakIndex(successor);
			for (int j = breakIndex; j >= 1; j--) {
				updateSingleFinger(j, null);
			}
			if (predecessor != null && predecessor.equals(successor)) {
				setPredecessor(null);
			}
			fillSuccessor();
			successor = getSuccessor();
			if ((successor == null || successor.equals(successor)) &&
				predecessor != null && !predecessor.equals(localSocketAddress)) {
				InetSocketAddress newFirstFinger = getFirstFinger(successor);
				updateSingleFinger(1, newFirstFinger);
			}
		}
		
		private int findBreakIndex(InetSocketAddress successor) {
			for (int i = 32; i > 0; i--) {
				InetSocketAddress ithFinger = fingerTable.get(i);
				if (ithFinger != null && ithFinger.equals(successor)) {
					return i;
				}
			}
			return 32;
		}
		
		
		private InetSocketAddress getFirstFinger(InetSocketAddress successor) {
			InetSocketAddress p = predecessor;
			InetSocketAddress pre;
			while (true) {
				pre = Helper.requestAddress(p, "FINDPRE");
				if (pre == null || pre.equals(p) || pre.equals(localSocketAddress) || pre.equals(successor)) {
					break;
				}
				p = pre;
			}
			return p;
		}
		
		private void deleteCertainFinger(InetSocketAddress f) {
			for (int i = 32; i > 0; i--) {
				InetSocketAddress ithfinger = fingerTable.get(i);
				if (ithfinger != null && ithfinger.equals(f))
					fingerTable.put(i, null);
			}
		}

	// need to fix~
		public void fillSuccessor() {
			InetSocketAddress successor = this.getSuccessor();
			if (successor == null || successor.equals(localSocketAddress)) {
				for (int i = 2; i <= 32; i++) {
					InetSocketAddress ithfinger = fingerTable.get(i);
					if (ithfinger!=null && !ithfinger.equals(localSocketAddress)) {
						for (int j = i-1; j >=1; j--) {
							updateSingleFinger(j, ithfinger);
						}
						break;
					}
				}
			}
			successor = getSuccessor();
			if ((successor == null || successor.equals(localSocketAddress)) && predecessor!=null && !predecessor.equals(localSocketAddress)) {
				updateSingleFinger(1, predecessor);
			}

		}


		public void clearPredecessor () {
			setPredecessor(null);
		}
		private synchronized void setPredecessor(InetSocketAddress pre) {
			predecessor = pre;
		}
		public long getId() {
			return localId;
		}
		public InetSocketAddress getAddress() {
			return localSocketAddress;
		}
		public InetSocketAddress getPredecessor() {
			return predecessor;
		}
		public InetSocketAddress getSuccessor() {
			if (fingerTable != null && fingerTable.size() > 0) {
				return fingerTable.get(1);
			}
			return null;
		}

	public void printNeighbors() {
		System.out.println("\n--- Node Neighbors [" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] ---");
		System.out.println(String.format("%-15s %-30s %-20s", "Neighbor", "Address", "Position"));

		InetSocketAddress successor = fingerTable.get(1);
		String predecessorInfo, successorInfo;

		try {
			if (predecessor == null || predecessor.equals(localSocketAddress)) {
				predecessorInfo = "Self";
			} else {
				predecessorInfo = String.format("%s:%d, %s", predecessor.getAddress().getHostAddress(), predecessor.getPort(), Helper.hexIdAndPosition(predecessor));
			}

			if (successor == null || successor.equals(localSocketAddress)) {
				successorInfo = "Self";
			} else {
				successorInfo = String.format("%s:%d, %s", successor.getAddress().getHostAddress(), successor.getPort(), Helper.hexIdAndPosition(successor));
			}
		} catch (Exception e) {
			System.out.println("Error in retrieving predecessor/successor info: " + e.getMessage());
			predecessorInfo = "Error";
			successorInfo = "Error";
		}

		System.out.println(String.format("%-15s %-30s %-20s", "Predecessor", predecessorInfo, ""));
		System.out.println(String.format("%-15s %-30s %-20s", "Successor", successorInfo, ""));
	}

		public void printDataStructure() {
			System.out.println("\n================= Node Data Structure [" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] =================");
			try {
				System.out.println("LOCAL: " + localSocketAddress.toString() + "\t" + Helper.hexIdAndPosition(localSocketAddress));
		
				String predecessorInfo = (predecessor != null) ? predecessor.toString() + "\t" + Helper.hexIdAndPosition(predecessor) : "NULL";
				System.out.println("PREDECESSOR: " + predecessorInfo);
		
				System.out.println("\n--- Finger Table ---");
				System.out.println(String.format("%-5s %-20s %-30s %-20s", "Index", "Start", "Node Address", "Position"));
				for (int i = 1; i <= 32; i++) {
				long ithStart = Helper.calculateNodePosition(Helper.hashSocketAddress(localSocketAddress), i);
				InetSocketAddress finger = fingerTable.get(i);
				String fingerInfo, position;
		
				if (finger != null) {
					fingerInfo = finger.toString();
					position = Helper.hexIdAndPosition(finger);
				} else {
					fingerInfo = "NULL";
					position = "";
				}
		
				System.out.println(String.format("%-5d %-20s %-30s %-20s", i, Helper.longTo8DigitHex(ithStart), fingerInfo, position));
			}
		}	catch (Exception e) {
				System.out.println("Error in printing data structure: " + e.getMessage());
			}
			System.out.println("\n========================================================\n");
		}
		
		public void stopAllThreads() {
			if (communicateManager != null)
				communicateManager.toDie();
			if (nodeMaintenance != null)
				nodeMaintenance.toDie();
		}
	}
