package edu.ums.web.academic;

public interface ResultPublisher {

    /**
     * Publishes every APPROVED mark sheet of the term and writes results and SGPA.
     * Must run inside the term-advance transaction. Returns the number of results written.
     */
    int publish(long termId);
}
