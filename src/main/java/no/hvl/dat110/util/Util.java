package no.hvl.dat110.util;

import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.hvl.dat110.middleware.Node;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class Util {

	public static String activeIP = null;
	public static int numReplicas = 4;

	/**
	 * Checks if an ID falls within a circular interval (lower, upper]
	 * @param id The ID to check
	 * @param lower The lower bound (exclusive)
	 * @param upper The upper bound (inclusive)
	 * @return true if id is in (lower, upper], false otherwise
	 */
	public static boolean checkInterval(BigInteger id, BigInteger lower, BigInteger upper) {
		// If lower < upper (normal case)
		if (lower.compareTo(upper) < 0) {
			return id.compareTo(lower) > 0 && id.compareTo(upper) <= 0;
		}
		// If lower > upper (wrapped around case)
		else if (lower.compareTo(upper) > 0) {
			return id.compareTo(lower) > 0 || id.compareTo(upper) <= 0;
		}
		// If lower == upper (only possible if ring has 1 node)
		return true;
	}

	public static List<String> toString(List<NodeInterface> list) throws RemoteException {
		List<String> nodestr = new ArrayList<String>();
		list.forEach(node ->
				{
					nodestr.add(((Node)node).getNodeName());
				}
		);

		return nodestr;
	}

	public static NodeInterface getProcessStub(String name, int port) {
		NodeInterface nodestub = null;
		Registry registry = null;
		try {
			registry = LocateRegistry.getRegistry(port);
			nodestub = (NodeInterface) registry.lookup(name);
		} catch (NotBoundException | RemoteException e) {
			return null;
		}
		return nodestub;
	}

	public static Registry tryIPSingleMachine(String nodeip) throws NumberFormatException, RemoteException {
		String[] ips = StaticTracker.ACTIVENODES;
		List<String> iplist = Arrays.asList(ips);
		Collections.shuffle(iplist);

		Registry registry = null;
		for (String ip : iplist) {
			String ipaddress = ip.split(":")[0].trim();
			String port = ip.split(":")[1].trim();
			System.out.println(ipaddress+":"+port);
			if(nodeip.equals(ipaddress))
				continue;
			registry = LocateRegistry.getRegistry(Integer.valueOf(port));
			if (registry != null) {
				activeIP = ipaddress;
				return registry;
			}
		}
		return registry;
	}

	public static Map<String, Integer> getProcesses(){
		Map<String, Integer> processes = new HashMap<>();
		processes.put("process1", 9091);
		processes.put("process2", 9092);
		processes.put("process3", 9093);
		processes.put("process4", 9094);
		processes.put("process5", 9095);
		return processes;
	}
}