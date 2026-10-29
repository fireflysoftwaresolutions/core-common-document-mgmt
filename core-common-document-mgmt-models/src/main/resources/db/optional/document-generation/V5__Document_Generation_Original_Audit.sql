-- Optional owner coupling after V4 and the exact genuine immutable-audit deployment.
DO $$ BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET') OR NOT pg_catalog.pg_has_role(current_user,'document_generation_guard','SET') THEN
  RAISE EXCEPTION 'Separately delegated document and audit installer required' USING ERRCODE='42501';END IF;
END $$;
SET LOCAL ROLE firefly_audit_guard;
GRANT USAGE ON SCHEMA firefly_audit TO document_generation_guard;
GRANT REFERENCES(resource_tenant_id,event_id) ON firefly_audit.audit_event TO document_generation_guard;
CREATE FUNCTION firefly_audit.document_generation_require_audit(p_tenant UUID,p_document UUID,p_event UUID,p_digest TEXT,
 p_issuer TEXT,p_subject TEXT,p_client TEXT,p_platform TEXT,p_realm TEXT,p_staff UUID,p_policy TEXT,p_intent TEXT,p_new BOOLEAN) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c firefly_audit_private.context%ROWTYPE;e firefly_audit.audit_event%ROWTYPE;j JSONB:=p_intent::jsonb;
BEGIN
 SELECT * INTO c FROM firefly_audit_private.context t WHERE t.xid=pg_catalog.pg_current_xact_id_if_assigned()
 AND t.backend_pid=pg_catalog.pg_backend_pid() AND t.db_session_user=session_user AND t.resource_tenant_id=p_tenant
 AND t.event_id=p_event AND t.service='core-common-document-mgmt' AND t.resource_type='document-generation' AND t.resource_id=p_document;
 IF NOT FOUND THEN RAISE EXCEPTION 'Exact original document audit context required' USING ERRCODE='23514';END IF;
 c:=firefly_audit_private.context_for(c.nonce);PERFORM firefly_audit_private.require_graph(c);
 IF c.new_event IS DISTINCT FROM p_new OR NOT EXISTS(SELECT 1 FROM firefly_audit_private.completion t WHERE t.nonce=c.nonce) THEN
  RAISE EXCEPTION 'Completed original document audit append required' USING ERRCODE='23514';END IF;
 SELECT * INTO STRICT e FROM firefly_audit.audit_event t WHERE t.resource_tenant_id=p_tenant AND t.event_id=p_event;
 IF ROW(e.service,e.resource_type,e.resource_id,e.action,e.outcome,e.actor_issuer,e.actor_subject,e.actor_client_id,e.actor_platform_tenant_id,
 e.actor_staff_realm,e.actor_staff_id,e.policy_revision,e.evidence_digest,e.process_id,e.correlation_id,e.causation_id,e.occurred_seconds,e.occurred_nanos)
 IS DISTINCT FROM ROW('core-common-document-mgmt'::text,'document-generation'::text,p_document,'DOCUMENT_LOCAL_STORAGE_RECORDED'::text,'COMPLETED'::text,
 p_issuer,p_subject,p_client,p_platform,p_realm,p_staff,p_policy,p_digest,(j->>'processId')::uuid,(j->>'correlationId')::uuid,(j->>'causationId')::uuid,
 (j->>'occurredSeconds')::bigint,(j->>'occurredNanos')::integer) THEN
  RAISE EXCEPTION 'Exact retained document audit body required' USING ERRCODE='23514';END IF;
END $$;
REVOKE ALL ON FUNCTION firefly_audit.document_generation_require_audit(UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,TEXT,TEXT,BOOLEAN) FROM PUBLIC,firefly_audit_writer,document_generation_admitter;
GRANT EXECUTE ON FUNCTION firefly_audit.document_generation_require_audit(UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,TEXT,TEXT,BOOLEAN) TO document_generation_guard;
RESET ROLE;
SET LOCAL ROLE document_generation_guard;
ALTER TABLE document_generation.issuance ADD CONSTRAINT original_document_audit_fk
 FOREIGN KEY(resource_tenant_id,event_id) REFERENCES firefly_audit.audit_event(resource_tenant_id,event_id) DEFERRABLE INITIALLY DEFERRED;
RESET ROLE;
SET LOCAL ROLE firefly_audit_guard;
REVOKE REFERENCES(resource_tenant_id,event_id) ON firefly_audit.audit_event FROM document_generation_guard;
RESET ROLE;
