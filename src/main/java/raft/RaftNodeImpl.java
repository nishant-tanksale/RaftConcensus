package raft;

import log.LogEntry;
import log.LogHandler;
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
    private volatile long lastHeartbeatTime = System.currentTimeMillis();

    public RaftNodeImpl(int nodeId, int port, int totalNodes) throws RemoteException {
        this.nodeId = nodeId;
        this.port = port;
        this.totalNodes = totalNodes;
        this.logHandler = new LogHandler("logs/node" + nodeId + ".txt");  // Initialize log storage
        this.rpcHandler = new RPCHandler(totalNodes);

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
            int lastLogIndex = logs.size() - 1;  // Get last log index (-1 if empty)
            int prevLogIndex = (lastLogIndex >= 0) ? lastLogIndex : -1;
            int prevLogTerm = (prevLogIndex >= 0) ? logs.get(prevLogIndex).getTerm() : 0; // Default term = 0 if no logs

            LogEntry entry = new LogEntry(term, lastLogIndex + 1, newData);
            logHandler.append(entry); // Append entry to the log

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

            // 🛑 FIX: Handle prevLogIndex -1 (empty log case)
            if (prevLogIndex >= 0) {
                if (prevLogIndex >= logs.size() || logs.get(prevLogIndex).getTerm() != prevLogTerm) {
                    System.err.println("❌ Log inconsistency detected! Rejecting entry.");
                    return false; // Reject if log doesn't match
                }
            }

            // Append new entry
            LogEntry entry = new LogEntry(term, prevLogIndex + 1, newData);
            logHandler.append(entry);
            System.out.println("Node " + nodeId + " persisted data from Leader " + leaderId);

            return true;
        }
        return false;
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
