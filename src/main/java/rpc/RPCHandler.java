package rpc;


import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class RPCHandler {
    private final int registryPort = 1099;
    private final int totalNodes;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RPCHandler(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public List<RaftNode> getRemoteNodes(int callingNodeId) {
        List<RaftNode> remoteNodesList = new ArrayList<>();
        try {
            Registry registry = LocateRegistry.getRegistry(registryPort);
            for (int i = 1; i <= totalNodes; i++) {
                if (i != callingNodeId) {
                    try {
                        RaftNode node = (RaftNode) registry.lookup("RaftNode" + i);
                        remoteNodesList.add(node);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to lookup RaftNode" + i + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error getting remote nodes: " + e.getMessage());
        }
        return remoteNodesList;
    }

    public int getVotes(int callingNodeId, int termId) {
        List<RaftNode> remoteNodes = getRemoteNodes(callingNodeId);
        int votes = 1; // Self vote
        for (var node : remoteNodes) {
            try {
                if (node.requestVote(callingNodeId, termId)) {
                    votes++;
                }
            } catch (Exception e) {
                System.err.println("⚠️ Vote request failed: " + e.getMessage());
            }

        }
        return votes;
    }

    public void sendHeartbeats(int leaderId, int callingNodeId) {
        List<RaftNode> remoteNodes = getRemoteNodes(callingNodeId);
        for (RaftNode node : remoteNodes) {
            try {
                node.receiveHeartbeat(leaderId);
            } catch (Exception e) {
                System.err.println("⚠️ Heartbeat failed for node " + leaderId + ": " + e.getMessage());
                handleFollowerFailure(node, leaderId);
            }

        }
    }

    public boolean replicateData(int leaderId, String newData, int leaderTerm, int prevLogIndex, int prevLogTerm) {
        List<RaftNode> remoteNodes = getRemoteNodes(leaderId);
        AtomicInteger ackCount = new AtomicInteger(1); // Leader itself counts as an ack

        remoteNodes.forEach(node -> executor.execute(() -> {
            try {
                boolean success = node.appendEntries(leaderTerm, leaderId, prevLogIndex, prevLogTerm, newData);
                if (success) ackCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println("⚠️ Replication failed: " + e.getMessage());
            }
        }));

        return ackCount.get() > totalNodes / 2;
    }

    private void handleFollowerFailure(RaftNode follower, int leaderId) {
        int attempts = 0;
        int baseDelay = 100; // Start with 100ms
        int maxRetries = 5;

        while (attempts < maxRetries) {
            try {
                System.out.println("🔄 Retrying heartbeat to follower (Attempt " + (attempts + 1) + ")");
                follower.receiveHeartbeat(leaderId);
                System.out.println("✅ Follower responded. Marking as active.");
                return; // Exit if successful
            } catch (Exception e) {
                System.err.println("❌ Retry attempt " + (attempts + 1) + " failed.");
            }

            try {
                Thread.sleep(baseDelay * (1 << attempts)); // 100ms, 200ms, 400ms, etc.
            } catch (InterruptedException ignored) {}

            attempts++;
        }

        System.err.println("🚨 Follower is unresponsive after " + maxRetries + " retries. Marking as unavailable.");
    }

    public void shutdown() {
        executor.close();
    }
}
