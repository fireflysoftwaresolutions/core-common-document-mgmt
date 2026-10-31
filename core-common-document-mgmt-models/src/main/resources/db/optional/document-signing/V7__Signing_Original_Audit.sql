-- Manual owner coupling after V6 and the exact reviewed canonical immutable-audit installation.
-- No role is created and no runtime user gains audit reads or guard membership.
BEGIN;
DO $$ BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET')
 OR NOT pg_catalog.pg_has_role(current_user,'document_signing_guard','SET') THEN
  RAISE EXCEPTION 'Exact separately delegated signing and audit installer required' USING ERRCODE='42501';
 END IF;
 IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname IN('firefly_audit_guard','document_signing_guard','firefly_audit_writer')
 AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication
 AND rolcanlogin=(rolname='firefly_audit_writer'))<>3
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member IN
    (SELECT oid FROM pg_catalog.pg_roles WHERE rolname IN('firefly_audit_guard','document_signing_guard','firefly_audit_writer')))
 OR pg_catalog.pg_has_role('firefly_audit_writer','firefly_audit_guard','MEMBER')
 OR pg_catalog.pg_has_role('firefly_audit_writer','document_signing_guard','MEMBER')
 OR (SELECT relowner FROM pg_catalog.pg_class WHERE oid='firefly_audit.audit_event'::regclass) IS DISTINCT FROM
    (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard')
 OR (SELECT nspowner FROM pg_catalog.pg_namespace WHERE nspname='document_signing') IS DISTINCT FROM
    (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard') THEN
  RAISE EXCEPTION 'Exact restricted audit and signing owners required' USING ERRCODE='42501';
 END IF;
END $$;
SET LOCAL ROLE firefly_audit_guard;
DO $$ DECLARE guard_oid OID;BEGIN
 SELECT oid INTO STRICT guard_oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 IF EXISTS(SELECT 1 FROM (VALUES
    (pg_catalog.to_regprocedure('firefly_audit_private.canonical_text(text,text)'),'pg_catalog.bytea'::pg_catalog.regtype),
    (pg_catalog.to_regprocedure('firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)'),'pg_catalog.bytea'::pg_catalog.regtype),
    (pg_catalog.to_regprocedure('firefly_audit_private.require_record_hash(firefly_audit.audit_event)'),'pg_catalog.void'::pg_catalog.regtype)) wanted(id,result_type)
    LEFT JOIN pg_catalog.pg_proc p ON p.oid=wanted.id
    WHERE p.oid IS NULL OR p.proowner<>guard_oid OR p.prosecdef OR p.provolatile<>'i' OR p.prokind<>'f'
       OR p.prorettype<>wanted.result_type OR p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog'])
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_proc p
    CROSS JOIN LATERAL pg_catalog.aclexplode(coalesce(p.proacl,pg_catalog.acldefault('f',p.proowner))) a
    WHERE p.oid IN(pg_catalog.to_regprocedure('firefly_audit_private.canonical_text(text,text)'),
       pg_catalog.to_regprocedure('firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)'),
       pg_catalog.to_regprocedure('firefly_audit_private.require_record_hash(firefly_audit.audit_event)'))
       AND(a.grantee<>guard_oid OR a.privilege_type<>'EXECUTE')) THEN
  RAISE EXCEPTION 'Exact restricted canonical audit helper deployment required' USING ERRCODE='42501';
 END IF;
END $$;
GRANT USAGE ON SCHEMA firefly_audit TO document_signing_guard;
GRANT REFERENCES(resource_tenant_id,event_id) ON firefly_audit.audit_event TO document_signing_guard;
CREATE FUNCTION firefly_audit.document_signing_require_audit(p_tenant UUID,p_operation UUID,p_event UUID,p_action TEXT,p_digest TEXT,
 p_issuer TEXT,p_subject TEXT,p_client TEXT,p_platform TEXT,p_policy TEXT,p_process UUID,p_correlation UUID,p_causation UUID,p_current BOOLEAN) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c firefly_audit_private.context%ROWTYPE;e firefly_audit.audit_event%ROWTYPE;active_event firefly_audit.audit_event%ROWTYPE;
BEGIN
 IF p_action NOT IN('signing.prepare.reserve','signing.prepare.complete') OR p_current IS NULL THEN
  RAISE EXCEPTION 'Exact signing audit phase required' USING ERRCODE='23514';
 END IF;
 SELECT * INTO c FROM firefly_audit_private.context t WHERE t.xid=pg_catalog.pg_current_xact_id_if_assigned()
 AND t.backend_pid=pg_catalog.pg_backend_pid() AND t.db_session_user=session_user AND t.resource_tenant_id=p_tenant
 AND t.service='core-document-mgmt' AND t.resource_type='signing-operation' AND t.resource_id=p_operation
 AND (NOT p_current OR t.event_id=p_event);
 IF NOT FOUND THEN RAISE EXCEPTION 'Exact current signing audit context required' USING ERRCODE='23514';END IF;
 c:=firefly_audit_private.context_for(c.nonce);PERFORM firefly_audit_private.require_graph(c);
 IF c.new_event IS DISTINCT FROM true OR NOT EXISTS(SELECT 1 FROM firefly_audit_private.completion t WHERE t.nonce=c.nonce) THEN
  RAISE EXCEPTION 'Completed new signing audit append required' USING ERRCODE='23514';END IF;
 SELECT * INTO STRICT e FROM firefly_audit.audit_event t WHERE t.resource_tenant_id=p_tenant AND t.event_id=p_event;
 PERFORM firefly_audit_private.require_record_hash(e);
 IF ROW(e.service,e.resource_type,e.resource_id,e.action,e.outcome,e.actor_issuer,e.actor_subject,e.actor_client_id,e.actor_platform_tenant_id,
    e.actor_staff_realm,e.actor_staff_id,e.policy_revision,e.evidence_digest,e.process_id,e.correlation_id,e.causation_id)
 IS DISTINCT FROM ROW('core-document-mgmt'::text,'signing-operation'::text,p_operation,p_action,'COMPLETED'::text,
    p_issuer,p_subject,p_client,p_platform,NULL::text,NULL::uuid,p_policy,p_digest,p_process,p_correlation,p_causation) THEN
  RAISE EXCEPTION 'Exact immutable signing audit metadata required' USING ERRCODE='23514';END IF;
 IF NOT p_current THEN
  IF p_action<>'signing.prepare.reserve' OR c.event_id=p_event THEN
   RAISE EXCEPTION 'Exact reservation predecessor required' USING ERRCODE='23514';END IF;
  SELECT * INTO STRICT active_event FROM firefly_audit.audit_event t WHERE t.resource_tenant_id=p_tenant AND t.event_id=c.event_id;
  PERFORM firefly_audit_private.require_record_hash(active_event);
  IF ROW(active_event.service,active_event.resource_type,active_event.resource_id,active_event.action,active_event.outcome,
       active_event.actor_issuer,active_event.actor_subject,active_event.actor_client_id,active_event.actor_platform_tenant_id,
       active_event.actor_staff_realm,active_event.actor_staff_id,active_event.policy_revision,active_event.process_id,active_event.correlation_id,active_event.causation_id)
   IS DISTINCT FROM ROW('core-document-mgmt'::text,'signing-operation'::text,p_operation,'signing.prepare.complete'::text,'COMPLETED'::text,
       p_issuer,p_subject,p_client,p_platform,NULL::text,NULL::uuid,p_policy,p_process,p_correlation,p_event) THEN
   RAISE EXCEPTION 'Exact prepared audit causation required' USING ERRCODE='23514';END IF;
 END IF;
END;
$$;
REVOKE ALL ON FUNCTION firefly_audit.document_signing_require_audit(UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,UUID,UUID,BOOLEAN)
 FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
GRANT EXECUTE ON FUNCTION firefly_audit.document_signing_require_audit(UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,UUID,UUID,BOOLEAN)
 TO document_signing_guard;
RESET ROLE;
SET LOCAL ROLE document_signing_guard;
ALTER TABLE document_signing.operation ADD CONSTRAINT signing_reservation_audit_fk
 FOREIGN KEY(audit_resource_tenant_id,reservation_audit_event_id) REFERENCES firefly_audit.audit_event(resource_tenant_id, event_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE document_signing.operation ADD CONSTRAINT signing_prepared_audit_fk
 FOREIGN KEY(audit_resource_tenant_id,prepared_audit_event_id) REFERENCES firefly_audit.audit_event(resource_tenant_id, event_id) DEFERRABLE INITIALLY DEFERRED;
-- Runtime business privileges become usable only after the mandatory owner bridge and event FKs exist.
GRANT USAGE ON SCHEMA document_signing TO firefly_audit_writer;
GRANT SELECT ON document_signing.account_binding TO firefly_audit_writer;
-- PostgreSQL row locking needs column UPDATE permission; the owner trigger rejects every runtime UPDATE.
GRANT UPDATE(version) ON document_signing.account_binding TO firefly_audit_writer;
GRANT SELECT,INSERT ON document_signing.envelope,document_signing.operation,document_signing.request_binding TO firefly_audit_writer;
GRANT UPDATE(state,version) ON document_signing.envelope TO firefly_audit_writer;
GRANT UPDATE(state,generation,content,content_digest,content_length,prepared_audit_event_id,version) ON document_signing.operation TO firefly_audit_writer;
RESET ROLE;
SET LOCAL ROLE firefly_audit_guard;
REVOKE REFERENCES(resource_tenant_id,event_id) ON firefly_audit.audit_event FROM document_signing_guard;
RESET ROLE;
DO $$ DECLARE f pg_catalog.pg_proc%ROWTYPE;guard_oid OID;signing_oid OID;relation TEXT;BEGIN
 FOREACH relation IN ARRAY ARRAY['public.documents','public.document_versions','public.signature_providers','public.document_signatures'] LOOP
  IF NOT pg_catalog.has_table_privilege('firefly_audit_writer',relation,'SELECT')
  OR NOT pg_catalog.has_any_column_privilege('firefly_audit_writer',relation,'UPDATE') THEN
   RAISE EXCEPTION 'Separately governed metadata read and row-lock privileges required' USING ERRCODE='42501';
  END IF;
 END LOOP;
 SELECT oid INTO STRICT guard_oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 SELECT oid INTO STRICT signing_oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard';
 SELECT * INTO STRICT f FROM pg_catalog.pg_proc WHERE oid='firefly_audit.document_signing_require_audit(uuid,uuid,uuid,text,text,text,text,text,text,text,uuid,uuid,uuid,boolean)'::regprocedure;
 IF f.proowner<>guard_oid OR NOT f.prosecdef OR f.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']::text[]
 OR EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(f.proacl,pg_catalog.acldefault('f',f.proowner))) a
    WHERE a.grantee NOT IN(guard_oid,signing_oid) OR a.privilege_type<>'EXECUTE' OR (a.grantee=signing_oid AND a.is_grantable))
 OR NOT pg_catalog.has_function_privilege('document_signing_guard',f.oid,'EXECUTE')
 OR pg_catalog.has_table_privilege('document_signing_guard','firefly_audit.audit_event','SELECT')
 OR pg_catalog.has_column_privilege('document_signing_guard','firefly_audit.audit_event','resource_tenant_id','REFERENCES')
 OR pg_catalog.has_column_privilege('document_signing_guard','firefly_audit.audit_event','event_id','REFERENCES') THEN
  RAISE EXCEPTION 'Exact signing audit bridge owner signature and ACL required' USING ERRCODE='42501';
 END IF;
END $$;
COMMIT;
