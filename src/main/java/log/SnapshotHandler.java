package log;

import java.io.*;

public class SnapshotHandler {
    private final String snapshotFilePath;

    public SnapshotHandler(String snapshotFilePath) {
        this.snapshotFilePath = snapshotFilePath;
    }

    public static class Snapshot implements Serializable {
        public final int lastIncludedIndex;
        public final int lastIncludedTerm;
        public final String state;

        public Snapshot(int lastIncludedIndex, int lastIncludedTerm, String state) {
            this.lastIncludedIndex = lastIncludedIndex;
            this.lastIncludedTerm = lastIncludedTerm;
            this.state = state;
        }
    }

    public void saveSnapshot(int lastIncludedIndex, int lastIncludedTerm, String state) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(snapshotFilePath))) {
            Snapshot snapshot = new Snapshot(lastIncludedIndex, lastIncludedTerm, state);
            oos.writeObject(snapshot);
            System.out.println("📌 Snapshot saved at Index=" + lastIncludedIndex);
        } catch (IOException e) {
            System.err.println("❌ Failed to save snapshot: " + e.getMessage());
        }
    }

    public Snapshot loadSnapshot() {
        File file = new File(snapshotFilePath);
        if (!file.exists()) return null;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (Snapshot) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("❌ Failed to load snapshot: " + e.getMessage());
            return null;
        }
    }
}
