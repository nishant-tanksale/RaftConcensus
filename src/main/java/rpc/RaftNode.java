package rpc;

import log.LogEntry;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RaftNode extends Remote {
    boolean requestVote(int candidateId, int candidateTerm) throws RemoteException;
    void receiveHeartbeat(int leaderId) throws RemoteException;
    String readData() throws RemoteException;
    void writeData(String newData) throws RemoteException;
    boolean appendEntries(int leaderTerm, int leaderId, int prevLogIndex, int prevLogTerm, String newData) throws RemoteException;
    List<LogEntry> readLogEntries() throws RemoteException;
}
