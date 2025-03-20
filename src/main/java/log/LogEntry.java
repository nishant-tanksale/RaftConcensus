package log;

import java.io.Serializable;

public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int term;
    private final int index;
    private final String data;

    public LogEntry(int term, int index, String data) {
        this.term = term;
        this.index = index;
        this.data = data;
    }

    public int getTerm() {
        return term;
    }

    public int getIndex() {
        return index;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return "LogEntry{term=" + term + ", index=" + index + ", data='" + data + "'}";
    }
}
