package rpc;

import log.LogHandler;
import raft.RaftNodeImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RPCHandler {
    private final int registryPort = 1099;
    private final int totalNodes;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RPCHandler(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public List<RaftNode> getRemoteNodes(int callingNodeId) throws Exception {
        List<RaftNode> remoteNodesList = new ArrayList<>();
        Registry registry = LocateRegistry.getRegistry(registryPort);
        for (int i = 1; i <= totalNodes; i++) {
            if (i != callingNodeId) {
                try {
                    RaftNode node = (RaftNode) registry.lookup("RaftNode" + i);
                    remoteNodesList.add(node);
                } catch (Exception e) {
                    System.err.println("Failed to lookup node RaftNode" + i + ": " + e.getMessage());
                }
            }
        }
        return remoteNodesList;
    }

    public int getVotes(int callingNodeId, int termId) throws Exception {
        List<RaftNode> remoteNodes = getRemoteNodes(callingNodeId);
        int votes = 1; // Self vote

        for (RaftNode node : remoteNodes) {
            try {
                if (node.requestVote(callingNodeId, termId)) {
                    votes++;
                }
                } catch (Exception e) {
                    System.err.println("Vote request failed: " + e.getMessage());
                }
        }
        return votes;
    }

    public void sendHeartbeats(int leaderId, int callingNodeId) {
        try {
            List<RaftNode> remoteNodes = getRemoteNodes(callingNodeId);
            for (RaftNode node : remoteNodes) {
                executor.execute(() -> {
                    try {
                        node.receiveHeartbeat(leaderId);
                    } catch (Exception e) {
                        System.err.println("Heartbeat failed: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Failed to send heartbeats: " + e.getMessage());
        }
    }

    public boolean replicateData(int leaderId, String newData, int leaderTerm, int prevLogIndex, int prevLogTerm) {
        try {
            List<RaftNode> remoteNodes = getRemoteNodes(leaderId);
            int[] ackCount = {1}; // Leader itself counts as an ack

            List<Runnable> tasks = new ArrayList<>();
            for (RaftNode node : remoteNodes) {
                tasks.add(() -> {
                    try {
                        boolean success = node.appendEntries(leaderTerm, leaderId, prevLogIndex, prevLogTerm, newData);
                        if (success) {
                            synchronized (ackCount) {
                                ackCount[0]++;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Replication failed: " + e.getMessage());
                    }
                });
            }

            tasks.forEach(executor::execute);
            return ackCount[0] > totalNodes / 2;
        } catch (Exception e) {
            System.err.println("Failed to replicate data: " + e.getMessage());
        }
        return false;
    }

    private void handleFollowerFailure(int followerId) {
        int attempts = 0;
        int baseDelay = 100; // Start with 100ms
        int maxRetries = 5;

        while (attempts < maxRetries) {
            try {
                System.out.println("Retrying heartbeat to follower " + followerId + " (Attempt " + (attempts + 1) + ")");
                rpcHandler.sendHeartbeats(nodeId, followerId);
                System.out.println("Follower " + followerId + " responded. Marking as active.");
                return; // Exit if successful
            } catch (Exception e) {
                System.err.println("Retry attempt " + (attempts + 1) + " failed for follower " + followerId);
            }

            try {
                Thread.sleep(baseDelay * (1 << attempts)); // 100ms, 200ms, 400ms, etc.
            } catch (InterruptedException ignored) {}

            attempts++;
        }

        System.out.println("Follower " + followerId + " is unresponsive after " + maxRetries + " retries. Marking as unavailable.");
    }


    public void shutdown() {
        executor.close();
    }
}
