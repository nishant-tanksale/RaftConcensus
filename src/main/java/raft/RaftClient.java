package raft;

import log.LogEntry;
import rpc.RaftNode;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

public class RaftClient {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Registry registry;

        try {
            registry = LocateRegistry.getRegistry(1099);
        } catch (Exception e) {
            System.err.println("Error connecting to registry: " + e.getMessage());
            return;
        }

        while (true) {
            System.out.println("\n--- Raft Client Menu ---");
            System.out.println("1. Write data (Leader only)");
            System.out.println("2. Read logs from a node");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input! Please enter a number.");
                continue;
            }

            switch (choice) {
                case 1:
                    writeData(scanner, registry);
                    break;
                case 2:
                    readLogs(scanner, registry);
                    break;
                case 3:
                    System.out.println("Exiting...");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void writeData(Scanner scanner, Registry registry) {
        try {
            System.out.print("Enter leader node ID: ");
            int leaderId = Integer.parseInt(scanner.nextLine());

            RaftNode leaderNode = (RaftNode) registry.lookup("RaftNode" + leaderId);
            System.out.print("Enter data to write: ");
            String newData = scanner.nextLine();

            leaderNode.writeData(newData);
            System.out.println("✅ Data written successfully to leader " + leaderId);

        } catch (Exception e) {
            System.err.println("Error writing data: " + e.getMessage());
        }
    }

    private static void readLogs(Scanner scanner, Registry registry) {
        try {
            System.out.print("Enter node ID to read logs from: ");
            int nodeId = Integer.parseInt(scanner.nextLine());

            RaftNode node = (RaftNode) registry.lookup("RaftNode" + nodeId);
            List<LogEntry> logs = node.readLogEntries();

            System.out.println("\n📜 Logs from Node " + nodeId + ":");
            if (logs.isEmpty()) {
                System.out.println("No logs found.");
            } else {
                logs.forEach(System.out::println);
            }

        } catch (Exception e) {
            System.err.println("Error reading logs: " + e.getMessage());
        }
    }
}
