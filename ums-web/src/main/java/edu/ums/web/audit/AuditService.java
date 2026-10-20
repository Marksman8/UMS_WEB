package edu.ums.web.audit;

/** Records who did what. Entries are written after the business transaction commits. */
public interface AuditService {

    /** Records an action by the signed-in user. */
    void record(String action, String entity, Object entityId, String detail);

    /** Records an action when no user is signed in yet (login attempts) or on behalf of someone. */
    void recordAs(String actor, String role, String action, String entity, Object entityId, String detail, boolean ok);

    /** Records a denied request by the signed-in user. */
    void denied(String detail);
}
