package nl.robin.rolbeheer.web;

/** Fout die als leesbare melding naar het webpaneel gaat. */
public final class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}
