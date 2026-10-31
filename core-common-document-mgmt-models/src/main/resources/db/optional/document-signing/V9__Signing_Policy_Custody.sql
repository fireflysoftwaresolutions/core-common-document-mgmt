-- Optional manual owner installation after exact V6/V7 and canonical completed-audit deployment.
-- No automatic activation, role creation, guessed backfill or runtime audit-read authority.
BEGIN;
SET LOCAL search_path=pg_catalog;
DO $$ DECLARE owner_oid OID;bridge pg_catalog.pg_proc%ROWTYPE;BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET')
 OR NOT pg_catalog.pg_has_role(current_user,'document_signing_guard','SET')
 OR pg_catalog.current_setting('server_encoding')<>'UTF8' THEN
  RAISE EXCEPTION 'Exact delegated UTF8 signing installer required' USING ERRCODE='42501';
 END IF;
 IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname IN('firefly_audit_guard','document_signing_guard','firefly_audit_writer','firefly_audit_reader')
     AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication
     AND rolcanlogin=(rolname IN('firefly_audit_writer','firefly_audit_reader')))<>4
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member IN(SELECT oid FROM pg_catalog.pg_roles
     WHERE rolname IN('firefly_audit_guard','document_signing_guard','firefly_audit_writer','firefly_audit_reader')))
 OR (SELECT nspowner FROM pg_catalog.pg_namespace WHERE nspname='document_signing') IS DISTINCT FROM
     (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard')
 OR (SELECT relowner FROM pg_catalog.pg_class WHERE oid='document_signing.operation'::pg_catalog.regclass) IS DISTINCT FROM
     (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard')
 OR (SELECT relowner FROM pg_catalog.pg_class WHERE oid='firefly_audit.audit_event'::pg_catalog.regclass) IS DISTINCT FROM
     (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard') THEN
  RAISE EXCEPTION 'Exact independent restricted signing and audit owners required' USING ERRCODE='42501';
 END IF;
 SELECT oid INTO STRICT owner_oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 IF EXISTS(SELECT 1 FROM (VALUES
     (pg_catalog.to_regprocedure('firefly_audit_private.canonical_text(text,text)'),'pg_catalog.bytea'::pg_catalog.regtype),
     (pg_catalog.to_regprocedure('firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)'),'pg_catalog.bytea'::pg_catalog.regtype),
     (pg_catalog.to_regprocedure('firefly_audit_private.require_record_hash(firefly_audit.audit_event)'),'pg_catalog.void'::pg_catalog.regtype)) wanted(id,result_type)
     LEFT JOIN pg_catalog.pg_proc p ON p.oid=wanted.id
     WHERE p.oid IS NULL OR p.proowner<>owner_oid OR p.prosecdef OR p.provolatile<>'i' OR p.prokind<>'f'
        OR p.prorettype<>wanted.result_type OR p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']
        OR EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(p.proacl,pg_catalog.acldefault('f',p.proowner))) a
             WHERE a.grantee<>owner_oid OR a.privilege_type<>'EXECUTE')) THEN
  RAISE EXCEPTION 'Exact private canonical audit prerequisites required' USING ERRCODE='42501';
 END IF;
 SELECT * INTO STRICT bridge FROM pg_catalog.pg_proc WHERE oid=pg_catalog.to_regprocedure(
    'firefly_audit.document_signing_require_audit(uuid,uuid,uuid,text,text,text,text,text,text,text,uuid,uuid,uuid,boolean)');
 IF pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(bridge.prosrc,'UTF8')),'hex')<>'8046ce8a86772e0e8db4dd36fee16b38957d12ef68fec346ec62c7c953c13653'
 OR bridge.proowner<>owner_oid OR NOT bridge.prosecdef OR bridge.provolatile<>'v' OR bridge.prorettype<>'pg_catalog.void'::pg_catalog.regtype
 OR bridge.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']
 OR EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(bridge.proacl,pg_catalog.acldefault('f',bridge.proowner))) a
    WHERE a.grantee NOT IN(owner_oid,(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard'))
       OR a.privilege_type<>'EXECUTE' OR(a.grantee<>owner_oid AND a.is_grantable))
 OR NOT pg_catalog.has_function_privilege('document_signing_guard',bridge.oid,'EXECUTE')
 OR pg_catalog.has_table_privilege('document_signing_guard','firefly_audit.audit_event','SELECT') THEN
  RAISE EXCEPTION 'Exact canonical signing bridge owner and ACL required' USING ERRCODE='42501';
 END IF;
END; $$;
DO $$ DECLARE relation TEXT;relation_oid OID;owner_oid OID;writer_oid OID;policy pg_catalog.pg_policy%ROWTYPE;
 target TEXT;expected TEXT;qual TEXT;check_expr TEXT;matches INTEGER;scope_function pg_catalog.pg_proc%ROWTYPE;BEGIN
 SELECT oid INTO STRICT owner_oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 SELECT oid INTO STRICT writer_oid FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_writer';
 SELECT * INTO STRICT scope_function FROM pg_catalog.pg_proc WHERE oid=pg_catalog.to_regprocedure('public.firefly_audit_scope_matches(uuid,text,text,uuid,uuid,boolean)');
 IF scope_function.proowner<>owner_oid OR NOT scope_function.prosecdef OR scope_function.provolatile<>'v'
 OR scope_function.prorettype<>'pg_catalog.bool'::pg_catalog.regtype OR scope_function.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']
 OR pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(scope_function.prosrc,'UTF8')),'hex')<>'af3a8ec1b4f21c4df5e4ec15afbfec52e84dd9f2d0eb8a127b1496d4a4b7b77a'
 OR EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(scope_function.proacl,pg_catalog.acldefault('f',scope_function.proowner))) a
     WHERE a.grantee NOT IN(owner_oid,writer_oid) OR a.privilege_type<>'EXECUTE' OR(a.grantee<>owner_oid AND a.is_grantable))
 OR NOT pg_catalog.has_function_privilege('firefly_audit_writer',scope_function.oid,'EXECUTE') THEN
  RAISE EXCEPTION 'Exact audit scope helper owner body and ACL required' USING ERRCODE='42501';END IF;
 FOREACH relation IN ARRAY ARRAY['audit_head','audit_event','audit_outbox'] LOOP
  relation_oid:=pg_catalog.to_regclass('firefly_audit.'||relation);
  IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_class c WHERE c.oid=relation_oid AND c.relowner=owner_oid AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity)
   OR(SELECT count(*) FROM pg_catalog.pg_policy p WHERE p.polrelid=relation_oid)<>CASE WHEN relation='audit_head' THEN 4 ELSE 3 END THEN
   RAISE EXCEPTION 'Original audit RLS policy graph required' USING ERRCODE='42501';END IF;
  target:=CASE WHEN relation='audit_head' THEN 'null::uuid' ELSE 'event_id' END;matches:=0;
  FOR policy IN SELECT * FROM pg_catalog.pg_policy p WHERE p.polrelid=relation_oid LOOP
   IF NOT policy.polpermissive THEN RAISE EXCEPTION 'Original audit RLS policy graph required' USING ERRCODE='42501';END IF;
   qual:=pg_catalog.lower(pg_catalog.regexp_replace(pg_catalog.pg_get_expr(policy.polqual,policy.polrelid),'[[:space:]]','','g'));
   check_expr:=pg_catalog.lower(pg_catalog.regexp_replace(pg_catalog.pg_get_expr(policy.polwithcheck,policy.polrelid),'[[:space:]]','','g'));
   expected:='public.firefly_audit_scope_matches(resource_tenant_id,service,resource_type,resource_id,'||target||',';
   IF policy.polname='audit_guard_integrity' AND policy.polcmd='*' AND policy.polroles=ARRAY[owner_oid]
    AND qual='true' AND check_expr='true' THEN matches:=matches+1;
   ELSIF policy.polname='audit_scoped_read' AND policy.polcmd='r' AND policy.polroles=ARRAY[writer_oid]
    AND qual=expected||'false)' AND policy.polwithcheck IS NULL THEN matches:=matches+1;
   ELSIF policy.polname='audit_scoped_insert' AND policy.polcmd='a' AND policy.polroles=ARRAY[writer_oid]
    AND policy.polqual IS NULL AND check_expr=expected||'true)' THEN matches:=matches+1;
   ELSIF relation='audit_head' AND policy.polname='audit_head_cas' AND policy.polcmd='w' AND policy.polroles=ARRAY[writer_oid]
    AND qual=expected||'true)' AND check_expr=expected||'true)' THEN matches:=matches+1;
   ELSE RAISE EXCEPTION 'Original audit RLS policy graph required' USING ERRCODE='42501';END IF;
  END LOOP;
  IF matches<>CASE WHEN relation='audit_head' THEN 4 ELSE 3 END THEN RAISE EXCEPTION 'Original audit RLS policy graph required' USING ERRCODE='42501';END IF;
 END LOOP;
 IF(SELECT count(*) FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
     WHERE n.nspname='document_signing' AND c.relname IN('account_binding','envelope','operation','request_binding') AND c.relkind='r'
       AND c.relowner=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard') AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity)<>4 THEN
  RAISE EXCEPTION 'Exact existing signing owner graph required without invented tenant RLS' USING ERRCODE='42501';END IF;
END; $$;
-- The audited parent FKs require only these temporary owner-install privileges.
SET LOCAL ROLE firefly_audit_guard;
GRANT REFERENCES(resource_tenant_id,event_id) ON firefly_audit.audit_event TO document_signing_guard;
RESET ROLE;
SET LOCAL ROLE document_signing_guard;
DO $$ DECLARE owner_oid OID;BEGIN
 SELECT oid INTO STRICT owner_oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard';
 IF EXISTS(SELECT 1 FROM (VALUES
 ('document_signing.require_phase_audit(document_signing.operation,uuid,text)','c17ac43476f57eb2f5c4eb62f61146ef1981561f42660a657a7078e086ee2d7a'),
 ('document_signing.guard_operation_immutability()','3a0c06419c345fbfa62690d6d160bec76b64feec68cb167b84794f230dea1c3b')) wanted(signature,body_hash)
  LEFT JOIN pg_catalog.pg_proc p ON p.oid=pg_catalog.to_regprocedure(wanted.signature)
  WHERE p.oid IS NULL OR p.proowner<>owner_oid OR NOT p.prosecdef OR p.provolatile<>'v'
   OR p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']
   OR pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(p.prosrc,'UTF8')),'hex')<>wanted.body_hash
   OR EXISTS(SELECT 1 FROM pg_catalog.aclexplode(coalesce(p.proacl,pg_catalog.acldefault('f',p.proowner))) a
      WHERE a.grantee<>owner_oid OR a.privilege_type<>'EXECUTE')) THEN
  RAISE EXCEPTION 'Exact prior signing guard bodies and private ACL required' USING ERRCODE='42501';END IF;
END; $$;
ALTER TABLE document_signing.operation ADD COLUMN evidence_protocol INTEGER,
 ADD COLUMN reservation_trace_digest CHAR(64),ADD COLUMN prepared_trace_digest CHAR(64);
ALTER TABLE document_signing.operation ADD CONSTRAINT signing_policy_projection CHECK ((
 (evidence_protocol IS NULL AND reservation_trace_digest IS NULL AND prepared_trace_digest IS NULL)
 OR(evidence_protocol=3 AND reservation_trace_digest ~ '^[0-9a-f]{64}$'
    AND((state='PREPARING' AND prepared_trace_digest IS NULL)OR(state='PREPARED' AND prepared_trace_digest ~ '^[0-9a-f]{64}$')))) IS TRUE);
ALTER TABLE document_signing.operation ADD CONSTRAINT signing_policy_operation_scope UNIQUE(tenant_id,id,audit_resource_tenant_id);
CREATE TABLE document_signing.policy_custody(
 id UUID PRIMARY KEY CHECK(id<>'00000000-0000-0000-0000-000000000000'),
 tenant_id VARCHAR(100) NOT NULL CHECK(pg_catalog.length(tenant_id)>0),
 resource_tenant_id UUID NOT NULL CHECK(resource_tenant_id<>'00000000-0000-0000-0000-000000000000'),
 operation_id UUID NOT NULL CHECK(operation_id<>'00000000-0000-0000-0000-000000000000'),
 event_id UUID NOT NULL CHECK(event_id<>'00000000-0000-0000-0000-000000000000'),
 phase VARCHAR(8) NOT NULL CHECK(phase IN('RESERVE','PREPARED')),protocol INTEGER NOT NULL CHECK(protocol=3),
 authorization_revision VARCHAR(128) NOT NULL CHECK(authorization_revision ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),
 authorization_decision_id TEXT NOT NULL CHECK(pg_catalog.octet_length(authorization_decision_id) BETWEEN 1 AND 1024),
 admission_revision VARCHAR(128) NOT NULL CHECK(admission_revision ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),
 admission_decision_id TEXT NOT NULL CHECK(pg_catalog.octet_length(admission_decision_id) BETWEEN 1 AND 1024),
 business_digest CHAR(64) NOT NULL CHECK(business_digest ~ '^[0-9a-f]{64}$'),
 trace_digest CHAR(64) NOT NULL CHECK(trace_digest ~ '^[0-9a-f]{64}$'),
 UNIQUE(tenant_id,event_id),UNIQUE(tenant_id,operation_id,phase),
 FOREIGN KEY(tenant_id,operation_id,resource_tenant_id) REFERENCES document_signing.operation(tenant_id,id,audit_resource_tenant_id) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(resource_tenant_id,event_id) REFERENCES firefly_audit.audit_event(resource_tenant_id,event_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE FUNCTION document_signing.policy_text(value TEXT) RETURNS BYTEA LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE encoded BYTEA;BEGIN
 IF value IS NULL OR pg_catalog.octet_length(value)>2048 THEN RAISE EXCEPTION 'Bounded policy framing required' USING ERRCODE='23514';END IF;
 encoded:=pg_catalog.convert_to(value,'UTF8');RETURN pg_catalog.int4send(pg_catalog.octet_length(encoded))||encoded;
END; $$;
CREATE FUNCTION document_signing.policy_id(value TEXT) RETURNS BYTEA LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE units INTEGER:=0;codepoint INTEGER;all_blank BOOLEAN:=true;i INTEGER;BEGIN
 IF value IS NULL OR pg_catalog.octet_length(value) NOT BETWEEN 1 AND 1024 THEN RAISE EXCEPTION 'Exact original opaque policy ID required' USING ERRCODE='23514';END IF;
 FOR i IN 1..pg_catalog.length(value) LOOP
  codepoint:=pg_catalog.ascii(pg_catalog.substr(value,i,1));units:=units+CASE WHEN codepoint>65535 THEN 2 ELSE 1 END;
  IF codepoint BETWEEN 0 AND 31 OR codepoint BETWEEN 127 AND 159 THEN RAISE EXCEPTION 'Opaque policy controls refused' USING ERRCODE='23514';END IF;
  IF codepoint NOT IN(32,5760,8192,8193,8194,8195,8196,8197,8198,8200,8201,8202,8232,8233,8287,12288) THEN all_blank:=false;END IF;
 END LOOP;
 IF units>256 OR all_blank THEN RAISE EXCEPTION 'Exact finite original policy ID required' USING ERRCODE='23514';END IF;
 RETURN document_signing.policy_text(value);
END; $$;
CREATE FUNCTION document_signing.policy_trace(phase TEXT,ar TEXT,aid TEXT,pr TEXT,pid TEXT) RETURNS BYTEA LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN
 IF phase IS NULL OR phase NOT IN('RESERVE','PREPARED') OR ar IS NULL OR pr IS NULL
 OR ar !~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$' OR pr !~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$' THEN
  RAISE EXCEPTION 'Exact policy phase and revisions required' USING ERRCODE='23514';END IF;
 RETURN document_signing.policy_text('CORE_SIGNING_POLICY_TRACE')||pg_catalog.int4send(1)||document_signing.policy_text(phase)
  ||document_signing.policy_text(CASE WHEN phase='RESERVE' THEN 'INITIAL_AUTHORIZATION' ELSE 'AFTER_STORAGE_REVALIDATION' END)
  ||document_signing.policy_text('PERMIT')||pg_catalog.int4send(0)||document_signing.policy_text(ar)||document_signing.policy_id(aid)
  ||document_signing.policy_text(CASE WHEN phase='RESERVE' THEN 'RESERVE_ADMISSION' ELSE 'PREPARED_ADMISSION' END)
  ||document_signing.policy_text('PERMIT')||pg_catalog.int4send(0)||document_signing.policy_text(pr)||document_signing.policy_id(pid);
END; $$;
CREATE FUNCTION document_signing.policy_v3_digest(business TEXT,trace TEXT) RETURNS TEXT LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN
 IF business IS NULL OR trace IS NULL OR business !~ '^[0-9a-f]{64}$' OR trace !~ '^[0-9a-f]{64}$' THEN
  RAISE EXCEPTION 'Exact policy evidence commitments required' USING ERRCODE='23514';END IF;
 RETURN pg_catalog.encode(pg_catalog.sha256(document_signing.policy_text('CORE_SIGNING_EFFECT')||pg_catalog.int4send(3)
  ||document_signing.policy_text(business)||document_signing.policy_text(trace)),'hex');
END; $$;
CREATE OR REPLACE FUNCTION document_signing.require_phase_audit(operation document_signing.operation,event_id UUID,expected_action TEXT) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE business_digest TEXT;trace_digest TEXT;expected_digest TEXT;c document_signing.policy_custody%ROWTYPE;expected_phase TEXT;BEGIN
 IF expected_action IS NULL OR expected_action NOT IN('signing.prepare.reserve','signing.prepare.complete') THEN
  RAISE EXCEPTION 'Exact signing phase required' USING ERRCODE='23514';END IF;
 business_digest:=pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(pg_catalog.concat_ws(E'\n',
  'CORE_SIGNING_EFFECT_V2',expected_action,operation.audit_resource_tenant_id::text,operation.id::text,operation.envelope_id::text,
  operation.account_binding_id::text,operation.account_binding_version::text,operation.document_signature_id::text,operation.document_id::text,
  operation.document_version_id::text,operation.provider_id::text,operation.intent_digest::text,operation.source_fingerprint::text,
  operation.authority_digest::text,CASE WHEN expected_action='signing.prepare.reserve' THEN 'NONE' ELSE operation.content_digest::text END),'UTF8')),'hex');
 expected_digest:=business_digest;
 IF operation.evidence_protocol IS NOT NULL THEN
  IF operation.evidence_protocol<>3 THEN RAISE EXCEPTION 'Exact policy custody protocol required' USING ERRCODE='23514';END IF;
  expected_phase:=CASE WHEN expected_action='signing.prepare.reserve' THEN 'RESERVE' ELSE 'PREPARED' END;
  SELECT * INTO STRICT c FROM document_signing.policy_custody t WHERE t.tenant_id=operation.tenant_id AND t.resource_tenant_id=operation.audit_resource_tenant_id
   AND t.operation_id=operation.id AND t.event_id=require_phase_audit.event_id AND t.phase=expected_phase AND t.protocol=3;
  trace_digest:=pg_catalog.encode(pg_catalog.sha256(document_signing.policy_trace(c.phase,c.authorization_revision,c.authorization_decision_id,c.admission_revision,c.admission_decision_id)),'hex');
  IF c.business_digest IS DISTINCT FROM business_digest OR c.trace_digest IS DISTINCT FROM trace_digest
   OR c.admission_revision IS DISTINCT FROM operation.audit_policy_revision
   OR trace_digest IS DISTINCT FROM CASE WHEN expected_phase='RESERVE' THEN operation.reservation_trace_digest::text ELSE operation.prepared_trace_digest::text END THEN
   RAISE EXCEPTION 'Exact original stored policy provenance required' USING ERRCODE='23514';END IF;
  expected_digest:=document_signing.policy_v3_digest(business_digest,trace_digest);
 END IF;
 PERFORM firefly_audit.document_signing_require_audit(operation.audit_resource_tenant_id,operation.id,event_id,expected_action,expected_digest,
  operation.authority_json::jsonb#>>'{actor,issuer}',operation.authority_json::jsonb#>>'{actor,subject}',operation.authority_json::jsonb#>>'{actor,clientId}',
  operation.authority_json::jsonb#>>'{actor,platformTenantId}',operation.audit_policy_revision,operation.audit_process_id,operation.audit_correlation_id,
  CASE WHEN expected_action='signing.prepare.complete' THEN operation.reservation_audit_event_id ELSE NULL END,
  operation.state='PREPARING' OR expected_action='signing.prepare.complete');
END; $$;
CREATE FUNCTION document_signing.guard_policy_operation() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.evidence_protocol IS DISTINCT FROM 3 OR NEW.reservation_trace_digest IS NULL OR NEW.prepared_trace_digest IS NOT NULL THEN
   RAISE EXCEPTION 'New signing effects require explicit original policy custody' USING ERRCODE='23514';END IF;
 ELSE
  IF OLD.evidence_protocol IS DISTINCT FROM 3 OR ROW(NEW.evidence_protocol,NEW.reservation_trace_digest)
    IS DISTINCT FROM ROW(OLD.evidence_protocol,OLD.reservation_trace_digest) THEN
   RAISE EXCEPTION 'Legacy effects cannot be silently resumed as policy custody' USING ERRCODE='23514';END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER signing_policy_operation BEFORE INSERT OR UPDATE ON document_signing.operation
 FOR EACH ROW EXECUTE FUNCTION document_signing.guard_policy_operation();
CREATE FUNCTION document_signing.guard_policy_row() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF NEW.trace_digest IS DISTINCT FROM pg_catalog.encode(pg_catalog.sha256(document_signing.policy_trace(NEW.phase,NEW.authorization_revision,
    NEW.authorization_decision_id,NEW.admission_revision,NEW.admission_decision_id)),'hex') THEN
  RAISE EXCEPTION 'Exact immutable policy trace bytes required' USING ERRCODE='23514';END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER signing_policy_row BEFORE INSERT ON document_signing.policy_custody
 FOR EACH ROW EXECUTE FUNCTION document_signing.guard_policy_row();
CREATE TRIGGER signing_policy_no_mutation BEFORE UPDATE OR DELETE ON document_signing.policy_custody
 FOR EACH ROW EXECUTE FUNCTION document_signing.forbid_delete();
CREATE FUNCTION document_signing.require_policy_reverse_graph() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE operation document_signing.operation%ROWTYPE;BEGIN
 SELECT * INTO STRICT operation FROM document_signing.operation t WHERE t.id=NEW.operation_id AND t.tenant_id=NEW.tenant_id AND t.audit_resource_tenant_id=NEW.resource_tenant_id;
 IF operation.evidence_protocol IS DISTINCT FROM 3 OR NEW.event_id IS DISTINCT FROM
   CASE WHEN NEW.phase='RESERVE' THEN operation.reservation_audit_event_id ELSE operation.prepared_audit_event_id END THEN
  RAISE EXCEPTION 'Policy custody must belong to the exact committed signing phase' USING ERRCODE='23514';END IF;
 PERFORM document_signing.require_phase_audit(operation,NEW.event_id,CASE WHEN NEW.phase='RESERVE' THEN 'signing.prepare.reserve' ELSE 'signing.prepare.complete' END);
 RETURN NULL;
END; $$;
CREATE CONSTRAINT TRIGGER signing_policy_reverse_graph AFTER INSERT ON document_signing.policy_custody
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_signing.require_policy_reverse_graph();
REVOKE ALL ON document_signing.policy_custody FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
REVOKE ALL ON FUNCTION document_signing.policy_text(TEXT),document_signing.policy_id(TEXT),document_signing.policy_trace(TEXT,TEXT,TEXT,TEXT,TEXT),
 document_signing.policy_v3_digest(TEXT,TEXT),document_signing.guard_policy_operation(),document_signing.guard_policy_row(),document_signing.require_policy_reverse_graph()
 FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
GRANT SELECT,INSERT ON document_signing.policy_custody TO firefly_audit_writer;
GRANT UPDATE(prepared_trace_digest) ON document_signing.operation TO firefly_audit_writer;
RESET ROLE;
SET LOCAL ROLE firefly_audit_guard;
REVOKE REFERENCES(resource_tenant_id,event_id) ON firefly_audit.audit_event FROM document_signing_guard;
RESET ROLE;
DO $$ BEGIN
 IF pg_catalog.has_column_privilege('document_signing_guard','firefly_audit.audit_event','resource_tenant_id','REFERENCES')
 OR pg_catalog.has_column_privilege('document_signing_guard','firefly_audit.audit_event','event_id','REFERENCES')
 OR pg_catalog.has_table_privilege('document_signing_guard','firefly_audit.audit_event','SELECT')
 OR pg_catalog.has_table_privilege('firefly_audit_writer','document_signing.policy_custody','UPDATE')
 OR pg_catalog.has_table_privilege('firefly_audit_writer','document_signing.policy_custody','DELETE')
 OR pg_catalog.has_table_privilege('firefly_audit_reader','document_signing.policy_custody','SELECT') THEN
  RAISE EXCEPTION 'Exact narrow signing custody privileges required' USING ERRCODE='42501';END IF;
END; $$;
COMMIT;
