package log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LogHandler {
    private final String logFilePath;
    private ObjectOutputStream out;
    private boolean fileExists;

    public LogHandler(String logFilePath) {
        this.logFilePath = logFilePath;
        this.fileExists = new File(logFilePath).exists();
        openLogFile(); // Open the log file only once
    }

    private void openLogFile() {
        try {
            FileOutputStream fos = new FileOutputStream(logFilePath, true);
            out = fileExists ? new AppendableObjectOutputStream(fos) : new ObjectOutputStream(fos);
        } catch (IOException e) {
            System.err.println("Error initializing log file: " + e.getMessage());
        }
    }

    // Append log entry without corrupting the file
    public synchronized void append(LogEntry entry) {
        try {
            out.writeObject(entry);
            out.flush(); // Ensure immediate writing
        } catch (IOException e) {
            System.err.println("Error writing log entry: " + e.getMessage());
        }
    }

    // Read all log entries while handling EOF correctly
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
                    break; // End of file reached
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error reading log file: " + e.getMessage());
        }
        return logs;
    }

    // Close output stream properly when node shuts down
    public synchronized void close() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing log file: " + e.getMessage());
        }
    }

    // Get last log index
    public synchronized int getLastLogIndex() {
        List<LogEntry> logs = readAll();
        return logs.isEmpty() ? -1 : logs.get(logs.size() - 1).getIndex();
    }

    // Get last log term
    public synchronized int getLastLogTerm() {
        List<LogEntry> logs = readAll();
        return logs.isEmpty() ? -1 : logs.get(logs.size() - 1).getTerm();
    }
}
