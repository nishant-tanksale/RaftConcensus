package  raft;

import java.rmi.registry.*;
import java.util.Arrays;

public class ListRegistry {
    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("127.0.0.1", 1099);
        String[] names = registry.list();
        System.out.println("Registered Nodes: " + Arrays.toString(names));
    }
}