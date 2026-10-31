-- Optional forward migration after the existing V4/V5 and exact canonical audit helper installation.
-- Run transactionally as the explicitly delegated owner; existing V5 and all runtime ACLs stay unchanged.
BEGIN;
DO $$ BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET')
 OR NOT pg_catalog.pg_has_role(current_user,'document_generation_guard','SET') THEN
  RAISE EXCEPTION 'Separately delegated document and audit installer required' USING ERRCODE='42501';
 END IF;
 IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname IN('firefly_audit_guard','document_generation_guard')
    AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit
    AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)<>2
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member IN
    (SELECT oid FROM pg_catalog.pg_roles WHERE rolname IN('firefly_audit_guard','document_generation_guard'))) THEN
  RAISE EXCEPTION 'Exact restricted document and audit owners required' USING ERRCODE='42501';
 END IF;
END $$;
SET LOCAL ROLE firefly_audit_guard;
DO $$ DECLARE guard_oid OID;generation_guard_oid OID;f pg_catalog.pg_proc%ROWTYPE;BEGIN
 SELECT oid INTO STRICT guard_oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 SELECT oid INTO STRICT generation_guard_oid FROM pg_catalog.pg_roles WHERE rolname='document_generation_guard';
 SELECT * INTO STRICT f FROM pg_catalog.pg_proc WHERE oid=pg_catalog.to_regprocedure(
  'firefly_audit.document_generation_require_audit(uuid,uuid,uuid,text,text,text,text,text,text,uuid,text,text,boolean)');
 IF f.proowner<>guard_oid OR NOT f.prosecdef OR f.provolatile<>'v' OR f.prokind<>'f'
 OR f.prorettype<>'pg_catalog.void'::pg_catalog.regtype OR f.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']
 OR EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(f.proacl,pg_catalog.acldefault('f',f.proowner))) a
    WHERE a.grantee NOT IN(guard_oid,generation_guard_oid) OR a.privilege_type<>'EXECUTE'
       OR (a.grantee=generation_guard_oid AND a.is_grantable))
 OR NOT EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(f.proacl,pg_catalog.acldefault('f',f.proowner))) a
    WHERE a.grantee=generation_guard_oid AND a.privilege_type='EXECUTE' AND NOT a.is_grantable) THEN
  RAISE EXCEPTION 'Exact installed document audit bridge required' USING ERRCODE='42501';
 END IF;
END $$;
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
CREATE OR REPLACE FUNCTION firefly_audit.document_generation_require_audit(p_tenant UUID,p_document UUID,p_event UUID,p_digest TEXT,
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
 PERFORM firefly_audit_private.require_record_hash(e);
 IF ROW(e.service,e.resource_type,e.resource_id,e.action,e.outcome,e.actor_issuer,e.actor_subject,e.actor_client_id,e.actor_platform_tenant_id,
 e.actor_staff_realm,e.actor_staff_id,e.policy_revision,e.evidence_digest,e.process_id,e.correlation_id,e.causation_id,e.occurred_seconds,e.occurred_nanos)
 IS DISTINCT FROM ROW('core-common-document-mgmt'::text,'document-generation'::text,p_document,'DOCUMENT_LOCAL_STORAGE_RECORDED'::text,'COMPLETED'::text,
 p_issuer,p_subject,p_client,p_platform,p_realm,p_staff,p_policy,p_digest,(j->>'processId')::uuid,(j->>'correlationId')::uuid,(j->>'causationId')::uuid,
 (j->>'occurredSeconds')::bigint,(j->>'occurredNanos')::integer) THEN
  RAISE EXCEPTION 'Exact retained document audit body required' USING ERRCODE='23514';END IF;
END $$;
RESET ROLE;
COMMIT;
