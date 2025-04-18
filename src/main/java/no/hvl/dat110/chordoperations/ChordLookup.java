package no.hvl.dat110.chordoperations;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.middleware.Node;
import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.Hash;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class ChordLookup {

	private static final Logger logger = LogManager.getLogger(ChordLookup.class);
	private Node node;

	public ChordLookup(Node node) {
		this.node = node;
	}

	public NodeInterface findSuccessor(BigInteger key) throws RemoteException {
		// Check if we are the immediate successor
		NodeInterface successor = node.getSuccessor();
		if (successor == null) {
			return node;  // Only node in the ring
		}

		// Check if key is between node and successor
		if (Util.checkInterval(key, node.getNodeID(), successor.getNodeID())) {
			return successor;
		}

		// Find closest preceding node using finger table
		NodeInterface closestPrecedingNode = findHighestPredecessor(key);

		// Prevent infinite recursion by checking if we're making progress
		if (closestPrecedingNode.getNodeID().equals(node.getNodeID())) {
			return successor;  // Default to our successor if no better node found
		}

		// Recursive call with depth limit
		try {
			return closestPrecedingNode.findSuccessor(key);
		} catch (RemoteException e) {
			logger.error("Remote exception in findSuccessor: " + e.getMessage());
			return successor;  // Fallback to our successor
		}
	}

	private NodeInterface findHighestPredecessor(BigInteger key) throws RemoteException {
		List<NodeInterface> fingerTable = node.getFingerTable();
		if (fingerTable == null || fingerTable.isEmpty()) {
			return node;
		}

		// Search finger table from highest to lowest
		for (int i = fingerTable.size() - 1; i >= 0; i--) {
			NodeInterface finger = fingerTable.get(i);
			if (finger != null) {
				BigInteger fingerId = finger.getNodeID();
				if (Util.checkInterval(fingerId, node.getNodeID(), key)) {
					return finger;
				}
			}
		}
		return node;
	}

	public void copyKeysFromSuccessor(NodeInterface succ) {
		Set<BigInteger> filekeys;
		try {
			// if this node and succ are the same, don't do anything
			if(succ.getNodeName().equals(node.getNodeName()))
				return;

			logger.info("copy file keys that are <= "+node.getNodeName()+" from successor "+ succ.getNodeName()+" to "+node.getNodeName());

			filekeys = new HashSet<>(succ.getNodeKeys());
			BigInteger nodeID = node.getNodeID();

			for(BigInteger fileID : filekeys) {
				if(fileID.compareTo(nodeID) <= 0) {
					logger.info("fileID="+fileID+" | nodeID= "+nodeID);
					node.addKey(fileID);  // re-assign file to this successor node
					Message msg = succ.getFilesMetadata().get(fileID);
					node.saveFileContent(msg.getNameOfFile(), fileID, msg.getBytesOfFile(), msg.isPrimaryServer()); // save the file in memory of the newly joined node
					succ.removeKey(fileID); // remove the file key from the successor
					succ.getFilesMetadata().remove(fileID); // also remove the saved file from memory
				}
			}

			logger.info("Finished copying file keys from successor "+ succ.getNodeName()+" to "+node.getNodeName());
		} catch (RemoteException e) {
			logger.error(e.getMessage());
		}
	}

	public void notify(NodeInterface pred_new) throws RemoteException {
		NodeInterface pred_old = node.getPredecessor();

		// if the predecessor is null accept the new predecessor
		if(pred_old == null) {
			node.setPredecessor(pred_new);        // accept the new predecessor
			return;
		}

		else if(pred_new.getNodeName().equals(node.getNodeName())) {
			node.setPredecessor(null);
			return;
		} else {
			BigInteger nodeID = node.getNodeID();
			BigInteger pred_oldID = pred_old.getNodeID();

			BigInteger pred_newID = pred_new.getNodeID();

			// check that pred_new is between pred_old and this node, accept pred_new as the new predecessor
			// check that ftsuccID is a member of the set {nodeID+1,...,ID-1}
			boolean cond = Util.checkInterval(pred_newID, pred_oldID.add(BigInteger.ONE), nodeID.add(BigInteger.ONE));
			if(cond) {
				node.setPredecessor(pred_new);        // accept the new predecessor
			}
		}
	}
}