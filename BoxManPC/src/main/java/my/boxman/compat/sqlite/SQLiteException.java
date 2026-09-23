package my.boxman.compat.sqlite;

public class SQLiteException extends RuntimeException {
    public SQLiteException() {
        super();
    }

    public SQLiteException(String message) {
        super(message);
    }

    public SQLiteException(String message, Throwable cause) {
        super(message, cause);
    }

    public SQLiteException(Throwable cause) {
        super(cause);
    }
}
