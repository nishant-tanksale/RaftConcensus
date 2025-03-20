package raft;
import rpc.RaftNode;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestRemoteCall {
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
            RaftNode node2 = (RaftNode) registry.lookup("RaftNode1");

            boolean vote = node2.requestVote(1, 12345);  // Sample election request
            System.out.println("Vote response from RaftNode1: " + vote);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
