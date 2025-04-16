package no.hvl.dat110.chordoperations;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Set;
import java.util.Timer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.middleware.Node;
import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.Hash;
import no.hvl.dat110.util.Util;

public class ChordProtocols {

	private static final Logger logger = LogManager.getLogger(ChordProtocols.class);
	private NodeInterface chordnode;
	private StabilizationProtocols stabprotocol;

	public ChordProtocols(NodeInterface chordnode) {
		this.chordnode = chordnode;
		joinRing();
		stabilizationProtocols();
	}

	public void stabilizationProtocols() {
		Timer timer = new Timer();
		stabprotocol = new StabilizationProtocols(this, timer);
		timer.scheduleAtFixedRate(stabprotocol, 5000, 2000);
	}

	public void joinRing() {
		try {
			Registry registry = Util.tryIPSingleMachine(chordnode.getNodeName());
			if(registry != null) {
				try {
					String foundNode = Util.activeIP;
					NodeInterface randomNode = (NodeInterface) registry.lookup(foundNode);

					NodeInterface chordnodeSuccessor = randomNode.findSuccessor(chordnode.getNodeID());

					chordnode.setSuccessor(chordnodeSuccessor);
					chordnode.setPredecessor(null);

					chordnodeSuccessor.notify(chordnode);

					fixFingerTable();

					((Node) chordnode).copyKeysFromSuccessor(chordnodeSuccessor);

				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			} else {
				createRing((Node) chordnode);
			}
		} catch (NumberFormatException | RemoteException e1) {
			logger.error("Error joining ring: " + e1.getMessage());
		}
	}

	private void createRing(Node node) throws RemoteException {
		node.setPredecessor(null);
		node.setSuccessor(node);
	}

	public void leaveRing() throws RemoteException {
		try {
			NodeInterface prednode = chordnode.getPredecessor();
			NodeInterface succnode = chordnode.getSuccessor();
			Set<BigInteger> keyids = chordnode.getNodeKeys();

			if(succnode != null) {
				for(BigInteger fileID : keyids) {
					try {
						succnode.addKey(fileID);
						Message msg = chordnode.getFilesMetadata(fileID);
						succnode.saveFileContent(msg.getNameOfFile(), fileID, msg.getBytesOfFile(), msg.isPrimaryServer());
					} catch (RemoteException e) {
						logger.error("Error transferring key: " + e.getMessage());
					}
				}
				succnode.setPredecessor(prednode);
			}
			if(prednode != null) {
				prednode.setSuccessor(succnode);
			}
			chordnode.setSuccessor(chordnode);
			chordnode.setPredecessor(chordnode);
			stabprotocol.setStop(true);

		} catch(Exception e) {
			logger.error("Error while leaving ring: "+e.getMessage());
		}
	}

	public void fixFingerTable() {
		try {
			// Get existing finger table
			List<NodeInterface> fingerTable = chordnode.getFingerTable();

			// Clear the finger table by creating a new empty one
			fingerTable.clear();

			BigInteger modulus = Hash.addressSize();
			int mbit = Hash.bitSize();

			for (int i = 1; i <= mbit; i++) {
				BigInteger k = chordnode.getNodeID()
						.add(BigInteger.valueOf(2).pow(i-1))
						.mod(modulus);

				NodeInterface succnode = chordnode.findSuccessor(k);

				if (succnode != null) {
					fingerTable.add(succnode);
				}
			}

		} catch (RemoteException e) {
			logger.error("Error fixing finger table: " + e.getMessage());
		}
	}

	protected NodeInterface getChordnode() {
		return chordnode;
	}
}