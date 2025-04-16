package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

public class MutualExclusion {

	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);
	private boolean CS_BUSY = false;
	private boolean WANTS_TO_ENTER_CS = false;
	private List<Message> queueack;
	private List<Message> mutexqueue;
	private LamportClock clock;
	private Node node;

	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		clock = new LamportClock();
		queueack = new ArrayList<Message>();
		mutexqueue = new ArrayList<Message>();
	}

	public synchronized void acquireLock() {
		CS_BUSY = true;
	}

	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;
	}

	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
		logger.info(node.nodename + " wants to access CS");

		// Clear queues
		queueack.clear();
		mutexqueue.clear();

		// Increment clock and set message clock
		clock.increment();
		message.setClock(clock.getClock());

		// Set want to enter CS flag
		WANTS_TO_ENTER_CS = true;

		// Remove duplicate peers
		List<Message> activenodes = removeDuplicatePeersBeforeVoting();

		// Multicast request
		multicastMessage(message, activenodes);

		// Wait for all replies
		boolean permission = areAllMessagesReturned(activenodes.size());

		if(permission) {
			acquireLock();
			node.broadcastUpdatetoPeers(updates);
			mutexqueue.clear();
		}

		return permission;
	}

	private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
		logger.info("Number of peers to vote = "+activenodes.size());

		for(Message nodeMessage : activenodes) {
			NodeInterface stub = Util.getProcessStub(nodeMessage.getNodeName(), nodeMessage.getPort());
			if(stub != null) {
				stub.onMutexRequestReceived(message);
			}
		}
	}

	public void onMutexRequestReceived(Message message) throws RemoteException {
		clock.increment();

		if(message.getNodeName().equals(node.getNodeName())) {
			onMutexAcknowledgementReceived(message);
			return;
		}

		int caseid = -1;

		if(!CS_BUSY && !WANTS_TO_ENTER_CS) {
			caseid = 0;
		} else if(CS_BUSY) {
			caseid = 1;
		} else if(WANTS_TO_ENTER_CS) {
			caseid = 2;
		}

		doDecisionAlgorithm(message, mutexqueue, caseid);
	}

	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
		String procName = message.getNodeName();
		int port = message.getPort();

		switch(condition) {
			case 0: {
				NodeInterface stub = Util.getProcessStub(procName, port);
				if(stub != null) {
					stub.onMutexAcknowledgementReceived(message);
				}
				break;
			}
			case 1: {
				queue.add(message);
				break;
			}
			case 2: {
				int senderClock = message.getClock();
				int receiverClock = node.getMessage().getClock();

				if(senderClock < receiverClock ||
						(senderClock == receiverClock && message.getNodeID().compareTo(node.getNodeID()) < 0)) {
					NodeInterface stub = Util.getProcessStub(procName, port);
					if(stub != null) {
						stub.onMutexAcknowledgementReceived(message);
					}
				} else {
					queue.add(message);
				}
				break;
			}
			default: break;
		}
	}

	public void onMutexAcknowledgementReceived(Message message) {
		queueack.add(message);
	}

	public void multicastReleaseLocks(Set<Message> activenodes) {
		logger.info("Releasing locks from = "+activenodes.size());

		for(Message message : activenodes) {
			NodeInterface stub = Util.getProcessStub(message.getNodeName(), message.getPort());
			if(stub != null) {
				try {
					stub.releaseLocks();
				} catch (RemoteException e) {
					logger.error("Error releasing locks: " + e.getMessage());
				}
			}
		}
	}

	private boolean areAllMessagesReturned(int numvoters) throws RemoteException {
		logger.info(node.getNodeName()+": size of queueack = "+queueack.size());

		if(queueack.size() == numvoters) {
			queueack.clear();
			return true;
		}

		return false;
	}

	private List<Message> removeDuplicatePeersBeforeVoting() {
		List<Message> uniquepeer = new ArrayList<Message>();
		for(Message p : node.activenodesforfile) {
			boolean found = false;
			for(Message p1 : uniquepeer) {
				if(p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if(!found)
				uniquepeer.add(p);
		}
		return uniquepeer;
	}
}