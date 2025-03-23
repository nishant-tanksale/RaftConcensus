package log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LogHandler {
    private final String logFilePath;
    private ObjectOutputStream out;
    private boolean fileExists;
    private int lastLogIndex = -1;
    private int lastLogTerm = -1;

    public LogHandler(String logFilePath) {
        this.logFilePath = logFilePath;
        this.fileExists = new File(logFilePath).exists();
        openLogFile();
        updateLastLogInfo();
    }

    private void openLogFile() {
        try {
            FileOutputStream fos = new FileOutputStream(logFilePath, true);
            out = fileExists ? new AppendableObjectOutputStream(fos) : new ObjectOutputStream(fos);
        } catch (IOException e) {
            System.err.println("Error initializing log file: " + e.getMessage());
        }
    }

    public synchronized void append(LogEntry entry) {
        try {
            out.writeObject(entry);
            out.flush();
            lastLogIndex = entry.getIndex();
            lastLogTerm = entry.getTerm();
        } catch (IOException e) {
            System.err.println("Error writing log entry: " + e.getMessage());
        }
    }

    public synchronized List<LogEntry> readAll() {
        List<LogEntry> logs = new ArrayList<>();
        File file = new File(logFilePath);
        if (!file.exists()) return logs;

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            while (true) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof LogEntry) {
                        logs.add((LogEntry) obj);
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error reading log file: " + e.getMessage());
        }
        return logs;
    }

    public synchronized void close() {
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            System.err.println("Error closing log file: " + e.getMessage());
        }
    }

    public synchronized int getLastLogIndex() {
        return lastLogIndex;
    }

    public synchronized int getLastLogTerm() {
        return lastLogTerm;
    }

    private void updateLastLogInfo() {
        List<LogEntry> logs = readAll();
        if (!logs.isEmpty()) {
            lastLogIndex = logs.get(logs.size() - 1).getIndex();
            lastLogTerm = logs.get(logs.size() - 1).getTerm();
        }
    }

    // 🛠️ Compact logs after snapshot
    public synchronized void compactLog(int lastIncludedIndex, int lastIncludedTerm) {
        List<LogEntry> logs = readAll();
        List<LogEntry> newLogs = new ArrayList<>();

        // Keep only logs after lastIncludedIndex
        for (LogEntry entry : logs) {
            if (entry.getIndex() > lastIncludedIndex) {
                newLogs.add(entry);
            }
        }

        // Overwrite log file with compacted logs
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(logFilePath))) {
            for (LogEntry entry : newLogs) {
                oos.writeObject(entry);
            }
            System.out.println("📌 Log compacted. Removed entries ≤ " + lastIncludedIndex);
        } catch (IOException e) {
            System.err.println("Error compacting logs: " + e.getMessage());
        }

        // Update last log index & term
        updateLastLogInfo();
    }

    public synchronized void clearLogs(int lastIncludedIndex) {
        List<LogEntry> logs = readAll();
        List<LogEntry> newLogs = new ArrayList<>();

        // Keep only logs after lastIncludedIndex
        for (LogEntry entry : logs) {
            if (entry.getIndex() > lastIncludedIndex) {
                newLogs.add(entry);
            }
        }

        // Overwrite log file with remaining logs
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(logFilePath))) {
            for (LogEntry entry : newLogs) {
                oos.writeObject(entry);
            }
            System.out.println("🗑️ Cleared logs up to index " + lastIncludedIndex);
        } catch (IOException e) {
            System.err.println("Error clearing logs: " + e.getMessage());
        }

        // Update last log index & term
        updateLastLogInfo();
    }


}
