package raft;

import log.LogEntry;
import log.LogHandler;
import log.SnapshotHandler;
import rpc.RPCHandler;
import rpc.RaftNode;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class RaftNodeImpl extends UnicastRemoteObject implements RaftNode {
    private final int nodeId;
    private final int port;
    private int term = 0;
    private boolean isLeader = false;
    private int leaderId = -1;
    private final int totalNodes;
    private String data = "Initial Data";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final RPCHandler rpcHandler;
    private final LogHandler logHandler;
    private final SnapshotHandler snapshotHandler;
    private volatile long lastHeartbeatTime = System.currentTimeMillis();
    private int lastIncludedIndex = -1;
    private int lastIncludedTerm = 0;

    public RaftNodeImpl(int nodeId, int port, int totalNodes) throws RemoteException {
        this.nodeId = nodeId;
        this.port = port;
        this.totalNodes = totalNodes;
        this.logHandler = new LogHandler("logs/node" + nodeId + ".txt");
        this.snapshotHandler = new SnapshotHandler("snapshots/node" + nodeId + ".dat");
        this.rpcHandler = new RPCHandler(totalNodes);

        // Restore from snapshot if available
        SnapshotHandler.Snapshot snapshot = snapshotHandler.loadSnapshot();
        if (snapshot != null) {
            this.lastIncludedIndex = snapshot.lastIncludedIndex;
            this.lastIncludedTerm = snapshot.lastIncludedTerm;
            this.data = snapshot.state;
            logHandler.clearLogs(lastIncludedIndex);
            System.out.println("🔄 Node " + nodeId + " restored from snapshot at Index=" + lastIncludedIndex);
        }

        // Start periodic log compaction
        scheduler.scheduleAtFixedRate(this::createSnapshot, 30, 30, TimeUnit.SECONDS);

        // Start leader election with a random delay
        scheduler.schedule(this::startElection, new Random().nextInt(5000), TimeUnit.MILLISECONDS);

        // Start election timeout monitoring
        scheduler.scheduleAtFixedRate(this::checkElectionTimeout, 0, 1, TimeUnit.SECONDS);
    }

    public String getStatus() {
        return isLeader ? "Leader" : "Follower";
    }

    @Override
    public synchronized void receiveHeartbeat(int leaderId) {
        this.leaderId = leaderId;
        lastHeartbeatTime = System.currentTimeMillis();
        System.out.println("Node " + nodeId + " received heartbeat from Leader " + leaderId);
    }

    @Override
    public synchronized boolean requestVote(int candidateId, int candidateTerm) {
        if (candidateTerm > term) {
            term = candidateTerm;
            leaderId = candidateId;
            isLeader = false;
            System.out.println("Node " + nodeId + " voted for " + candidateId);
            return true;
        }
        System.out.println("Node " + nodeId + " rejected vote request from " + candidateId);
        return false;
    }

    @Override
    public synchronized String readData() {
        return "Node " + nodeId + " Read Data: " + data;
    }

    public synchronized void writeData(String newData) throws RemoteException {
        if (isLeader) {
            List<LogEntry> logs = logHandler.readAll();
            int lastLogIndex = logs.size() - 1;
            int prevLogIndex = (lastLogIndex >= 0) ? lastLogIndex : lastIncludedIndex;
            int prevLogTerm = (prevLogIndex >= 0) ? logs.get(prevLogIndex).getTerm() : lastIncludedTerm;

            LogEntry entry = new LogEntry(term, lastLogIndex + 1, newData);
            logHandler.append(entry);

            System.out.println("Leader " + nodeId + " updated data: " + newData);
            rpcHandler.replicateData(nodeId, newData, term, prevLogIndex, prevLogTerm);
        } else {
            throw new RemoteException("Write requests must go to the Leader!");
        }
    }

    @Override
    public synchronized boolean appendEntries(int leaderTerm, int leaderId, int prevLogIndex, int prevLogTerm, String newData) throws RemoteException {
        if (leaderTerm >= term) {
            term = leaderTerm;
            this.leaderId = leaderId;
            this.isLeader = false;
            lastHeartbeatTime = System.currentTimeMillis();

            List<LogEntry> logs = logHandler.readAll();

            // 🛑 Handle case where prevLogIndex is below last snapshot
            if (prevLogIndex < lastIncludedIndex) {
                return true; // Already applied
            }

            if (prevLogIndex >= 0) {
                if (prevLogIndex >= logs.size() || logs.get(prevLogIndex).getTerm() != prevLogTerm) {
                    System.err.println("❌ Log inconsistency detected! Requesting snapshot.");
                    return false;
                }
            }

            LogEntry entry = new LogEntry(term, prevLogIndex + 1, newData);
            logHandler.append(entry);
            System.out.println("Node " + nodeId + " persisted data from Leader " + leaderId);

            return true;
        }
        return false;
    }

    private void createSnapshot() {
        List<LogEntry> logs = logHandler.readAll();
        if (logs.isEmpty()) return;

        int lastIndex = logs.get(logs.size() - 1).getIndex();
        int lastTerm = logs.get(logs.size() - 1).getTerm();
        String state = "Application state at index " + lastIndex;

        snapshotHandler.saveSnapshot(lastIndex, lastTerm, state);
        logHandler.clearLogs(lastIndex);
        lastIncludedIndex = lastIndex;
        lastIncludedTerm = lastTerm;
    }

    public synchronized void installSnapshot(int term, int lastIncludedIndex, int lastIncludedTerm, String state) {
        if (term < this.term) return;

        System.out.println("📥 Installing snapshot: Index=" + lastIncludedIndex);
        snapshotHandler.saveSnapshot(lastIncludedIndex, lastIncludedTerm, state);
        logHandler.clearLogs(lastIncludedIndex);
        this.lastIncludedIndex = lastIncludedIndex;
        this.lastIncludedTerm = lastIncludedTerm;
        this.data = state;
    }

    private void startElection() {
        term++;
        System.out.println("Node " + nodeId + " starting election for term " + term);
        try {
            int votes = rpcHandler.getVotes(nodeId, term);
            if (votes > totalNodes / 2) {
                isLeader = true;
                leaderId = nodeId;
                System.out.println("Node " + nodeId + " is elected as Leader!");
                startHeartbeats();
            }
        } catch (Exception e) {
            System.err.println("Election failed: " + e.getMessage());
        }
    }

    private void startHeartbeats() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isLeader) {
                rpcHandler.sendHeartbeats(nodeId, nodeId);
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void checkElectionTimeout() {
        if (!isLeader && System.currentTimeMillis() - lastHeartbeatTime > 5000) {
            System.out.println("Node " + nodeId + " did not receive heartbeat, starting election.");
            startElection();
        }
    }

    @Override
    public synchronized List<LogEntry> readLogEntries() throws RemoteException {
        return logHandler.readAll();
    }

    public int getNodeId() {
        return nodeId;
    }
}
