package raft;

import rpc.RaftNode;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class RaftServer {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -cp out raft.RaftServer <nodeId> <port> <totalNodes>");
            return;
        }

        try {
            int nodeId = Integer.parseInt(args[0]);
            int port = Integer.parseInt(args[1]);
            int totalNodes = Integer.parseInt(args[2]);

            // Start the RMI registry if not already running
            try {
                LocateRegistry.createRegistry(1099);
            } catch (Exception e) {
                System.out.println("RMI Registry already running.");
            }
            //
            // Create and bind the node
            RaftNodeImpl node = new RaftNodeImpl(nodeId, port, totalNodes);
            Registry registry = LocateRegistry.getRegistry(1099);
            registry.rebind("RaftNode" + nodeId , node);

            System.out.println("Node " + nodeId + " is running on port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
