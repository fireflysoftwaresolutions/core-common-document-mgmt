-- Optional, transactionally installed after original V1-V3 and genuine immutable-audit schema.
-- This is a new owner aggregate, not historical private signature/binding migrations.
DO $$ BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'document_generation_guard','SET')
 OR NOT pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'CREATE') THEN
  RAISE EXCEPTION 'Separately delegated document installer required' USING ERRCODE='42501';
 END IF;
 IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname IN('document_generation_guard','document_generation_admitter','firefly_audit_writer')
 AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication
 AND rolcanlogin=(rolname<>'document_generation_guard'))<>3
 OR pg_catalog.pg_has_role('firefly_audit_writer','document_generation_guard','MEMBER')
 OR pg_catalog.pg_has_role('document_generation_admitter','document_generation_guard','MEMBER')
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members m WHERE m.member IN
  (SELECT oid FROM pg_catalog.pg_roles WHERE rolname IN('document_generation_guard','document_generation_admitter','firefly_audit_writer'))) THEN
  RAISE EXCEPTION 'Exact separately provisioned document roles required' USING ERRCODE='42501';
 END IF;
END $$;
CREATE SCHEMA document_generation AUTHORIZATION document_generation_guard;
CREATE SCHEMA document_generation_private AUTHORIZATION document_generation_guard;
SET LOCAL ROLE document_generation_guard;
REVOKE ALL ON SCHEMA document_generation,document_generation_private FROM PUBLIC,firefly_audit_writer,document_generation_admitter;
GRANT USAGE ON SCHEMA document_generation TO firefly_audit_writer,document_generation_admitter;
CREATE TABLE document_generation.document(
 id UUID PRIMARY KEY,resource_tenant_id UUID NOT NULL,media_type TEXT NOT NULL CHECK(media_type IN('text/html','text/plain')),
 content BYTEA NOT NULL CHECK(pg_catalog.octet_length(content) BETWEEN 1 AND 2097152),
 content_digest TEXT NOT NULL CHECK(content_digest=pg_catalog.encode(pg_catalog.sha256(content),'hex')),
 UNIQUE(resource_tenant_id,id)
);
CREATE TABLE document_generation.metadata(
 id UUID PRIMARY KEY,resource_tenant_id UUID NOT NULL,intent_json TEXT NOT NULL CHECK(pg_catalog.octet_length(intent_json) BETWEEN 1 AND 2097152),
 request_digest TEXT NOT NULL CHECK(request_digest=pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(intent_json,'UTF8')),'hex')),
 UNIQUE(resource_tenant_id,id,request_digest),
 FOREIGN KEY(resource_tenant_id,id) REFERENCES document_generation.document(resource_tenant_id,id) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE document_generation.issuance(
 id UUID PRIMARY KEY,resource_tenant_id UUID NOT NULL,event_id UUID NOT NULL,request_digest TEXT NOT NULL,
 state TEXT NOT NULL CHECK(state='LOCAL_STORED'),UNIQUE(resource_tenant_id,event_id),UNIQUE(resource_tenant_id,id,event_id,request_digest),
 FOREIGN KEY(resource_tenant_id,id,request_digest) REFERENCES document_generation.metadata(resource_tenant_id,id,request_digest) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE document_generation.provider_intent(
 id UUID PRIMARY KEY,resource_tenant_id UUID NOT NULL,event_id UUID NOT NULL,request_digest TEXT NOT NULL,
 state TEXT NOT NULL CHECK(state='PENDING_PROVIDER'),
 FOREIGN KEY(resource_tenant_id,id,event_id,request_digest) REFERENCES document_generation.issuance(resource_tenant_id,id,event_id,request_digest) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE document_generation_private.admission(
 grant_id UUID PRIMARY KEY,resource_tenant_id UUID NOT NULL,document_id UUID NOT NULL,event_id UUID NOT NULL,
 request_digest TEXT NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,
 actor_client_id TEXT NOT NULL,actor_platform_tenant_id TEXT NOT NULL,actor_staff_realm TEXT,actor_staff_id UUID,
 policy_revision TEXT NOT NULL,granted_at TIMESTAMPTZ NOT NULL,until_time TIMESTAMPTZ NOT NULL,
 CHECK(until_time>granted_at AND until_time<=granted_at+interval '5 seconds'),CHECK((actor_staff_realm IS NULL)=(actor_staff_id IS NULL))
);
CREATE TABLE document_generation_private.revocation(grant_id UUID PRIMARY KEY REFERENCES document_generation_private.admission(grant_id),revoked_at TIMESTAMPTZ NOT NULL);
CREATE TABLE document_generation_private.context(
 nonce UUID PRIMARY KEY,grant_id UUID NOT NULL UNIQUE REFERENCES document_generation_private.admission(grant_id),
 xid XID8 NOT NULL,backend_pid INTEGER NOT NULL,db_session_user NAME NOT NULL,
 resource_tenant_id UUID NOT NULL,document_id UUID NOT NULL,event_id UUID NOT NULL,request_digest TEXT NOT NULL,
 installed_at TIMESTAMPTZ NOT NULL,until_time TIMESTAMPTZ NOT NULL,new_issuance BOOLEAN NOT NULL,
 UNIQUE(xid,backend_pid,db_session_user)
);
CREATE TABLE document_generation_private.completion(nonce UUID PRIMARY KEY REFERENCES document_generation_private.context(nonce));
CREATE FUNCTION document_generation_private.immutable_row() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SET search_path=pg_catalog AS $$ BEGIN
 RAISE EXCEPTION 'Immutable document generation evidence' USING ERRCODE='23514';
END $$;
CREATE FUNCTION document_generation.admit(p_grant UUID,p_tenant UUID,p_document UUID,p_event UUID,p_digest TEXT,
 p_issuer TEXT,p_subject TEXT,p_client TEXT,p_platform TEXT,p_realm TEXT,p_staff UUID,p_policy TEXT,p_until TIMESTAMPTZ) RETURNS UUID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE observed TIMESTAMPTZ:=pg_catalog.clock_timestamp();
BEGIN
 IF session_user<>'document_generation_admitter' OR p_grant IS NULL OR p_tenant IS NULL OR p_document IS NULL OR p_event IS NULL
 OR p_tenant='00000000-0000-0000-0000-000000000000'::uuid OR p_event='00000000-0000-0000-0000-000000000000'::uuid
 OR p_grant='00000000-0000-0000-0000-000000000000'::uuid OR p_document='00000000-0000-0000-0000-000000000000'::uuid
 OR p_digest IS NULL OR p_digest !~ '^[0-9a-f]{64}$' OR p_issuer IS NULL OR pg_catalog.length(p_issuer) NOT BETWEEN 1 AND 2048
 OR p_subject IS NULL OR pg_catalog.length(p_subject) NOT BETWEEN 1 AND 2048 OR p_client IS NULL OR pg_catalog.length(p_client) NOT BETWEEN 1 AND 2048
 OR p_platform IS NULL OR pg_catalog.length(p_platform) NOT BETWEEN 1 AND 2048 OR p_policy IS NULL OR pg_catalog.length(p_policy) NOT BETWEEN 1 AND 2048
 OR (p_realm IS NULL)<>(p_staff IS NULL) OR p_until IS NULL OR p_until<=observed OR p_until>observed+interval '5 seconds' THEN
  RAISE EXCEPTION 'Genuine finite document admission required' USING ERRCODE='42501';
 END IF;
 INSERT INTO document_generation_private.admission VALUES(p_grant,p_tenant,p_document,p_event,p_digest,p_issuer,p_subject,p_client,p_platform,p_realm,p_staff,p_policy,observed,p_until);
 RETURN p_grant;
END $$;
CREATE FUNCTION document_generation.revoke(p_grant UUID) RETURNS UUID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$ BEGIN
 IF session_user<>'document_generation_admitter' THEN RAISE EXCEPTION 'Document admitter required' USING ERRCODE='42501';END IF;
 INSERT INTO document_generation_private.revocation VALUES(p_grant,pg_catalog.clock_timestamp());RETURN p_grant;
END $$;
CREATE FUNCTION document_generation_private.current_context() RETURNS document_generation_private.context
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c document_generation_private.context%ROWTYPE;observed TIMESTAMPTZ:=pg_catalog.clock_timestamp();
BEGIN
 SELECT * INTO c FROM document_generation_private.context t WHERE t.xid=pg_catalog.pg_current_xact_id_if_assigned()
 AND t.backend_pid=pg_catalog.pg_backend_pid() AND t.db_session_user=session_user;
 IF NOT FOUND OR session_user<>'firefly_audit_writer' OR observed<c.installed_at OR observed>=c.until_time
 OR EXISTS(SELECT 1 FROM document_generation_private.revocation r WHERE r.grant_id=c.grant_id) THEN
  RAISE EXCEPTION 'Current protected document scope required' USING ERRCODE='42501';
 END IF;RETURN c;
END $$;
CREATE FUNCTION document_generation.scope_matches(p_tenant UUID,p_document UUID,p_event UUID) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c document_generation_private.context%ROWTYPE;
BEGIN c:=document_generation_private.current_context();
 RETURN c.resource_tenant_id=p_tenant AND c.document_id=p_document AND (p_event IS NULL OR c.event_id=p_event);
END $$;
CREATE FUNCTION document_generation.enter(p_grant UUID,p_nonce UUID,p_tenant UUID,p_document UUID,p_event UUID,p_digest TEXT)
RETURNS TABLE(context_nonce UUID,db_now_millis BIGINT,until_millis BIGINT,new_issuance BOOLEAN)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE a document_generation_private.admission%ROWTYPE;observed TIMESTAMPTZ:=pg_catalog.clock_timestamp();fresh BOOLEAN;r pg_catalog.pg_roles%ROWTYPE;
BEGIN
 SELECT * INTO r FROM pg_catalog.pg_roles WHERE rolname=session_user;
 IF session_user<>'firefly_audit_writer' OR r.rolsuper OR r.rolbypassrls OR r.rolinherit OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication
 OR pg_catalog.pg_has_role(session_user,'document_generation_guard','MEMBER')
 OR pg_catalog.has_schema_privilege(session_user,'document_generation_private','USAGE')
 OR pg_catalog.has_schema_privilege(session_user,'document_generation','CREATE')
 OR p_nonce IS NULL OR p_nonce='00000000-0000-0000-0000-000000000000'::uuid
 OR pg_catalog.to_regprocedure('firefly_audit.document_generation_require_audit(uuid,uuid,uuid,text,text,text,text,text,text,uuid,text,text,boolean)') IS NULL THEN
  RAISE EXCEPTION 'Exact installed document/audit writer capability required' USING ERRCODE='42501';
 END IF;
 SELECT * INTO a FROM document_generation_private.admission t WHERE t.grant_id=p_grant;
 IF NOT FOUND OR ROW(a.resource_tenant_id,a.document_id,a.event_id,a.request_digest) IS DISTINCT FROM ROW(p_tenant,p_document,p_event,p_digest)
 OR observed<a.granted_at OR observed>=a.until_time OR EXISTS(SELECT 1 FROM document_generation_private.revocation v WHERE v.grant_id=p_grant) THEN
  RAISE EXCEPTION 'Exact current native document admission required' USING ERRCODE='42501';
 END IF;
 SELECT NOT EXISTS(SELECT 1 FROM document_generation.issuance t WHERE t.resource_tenant_id=p_tenant AND t.id=p_document) INTO fresh;
 INSERT INTO document_generation_private.context VALUES(p_nonce,p_grant,pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),session_user,p_tenant,p_document,p_event,p_digest,observed,a.until_time,fresh);
 RETURN QUERY SELECT p_nonce,pg_catalog.floor(extract(epoch FROM observed)*1000)::bigint,pg_catalog.floor(extract(epoch FROM a.until_time)*1000)::bigint,fresh;
END $$;
CREATE FUNCTION document_generation_private.require_graph(c document_generation_private.context) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE d document_generation.document%ROWTYPE;m document_generation.metadata%ROWTYPE;i document_generation.issuance%ROWTYPE;
 p document_generation.provider_intent%ROWTYPE;a document_generation_private.admission%ROWTYPE;j JSONB;
BEGIN
 SELECT * INTO STRICT d FROM document_generation.document t WHERE t.resource_tenant_id=c.resource_tenant_id AND t.id=c.document_id;
 SELECT * INTO STRICT m FROM document_generation.metadata t WHERE t.resource_tenant_id=c.resource_tenant_id AND t.id=c.document_id;
 SELECT * INTO STRICT i FROM document_generation.issuance t WHERE t.resource_tenant_id=c.resource_tenant_id AND t.id=c.document_id;
 SELECT * INTO STRICT p FROM document_generation.provider_intent t WHERE t.resource_tenant_id=c.resource_tenant_id AND t.id=c.document_id;
 SELECT * INTO STRICT a FROM document_generation_private.admission t WHERE t.grant_id=c.grant_id;j:=m.intent_json::jsonb;
 IF ROW(i.event_id,i.request_digest,p.event_id,p.request_digest,m.request_digest) IS DISTINCT FROM ROW(c.event_id,c.request_digest,c.event_id,c.request_digest,c.request_digest)
 OR j->>'protocol' IS DISTINCT FROM 'DOCUMENT_LOCAL_STORAGE_V1' OR j->>'resourceTenantId' IS DISTINCT FROM c.resource_tenant_id::text
 OR j->>'documentId' IS DISTINCT FROM c.document_id::text OR j->>'eventId' IS DISTINCT FROM c.event_id::text
 OR j->>'contentDigest' IS DISTINCT FROM d.content_digest OR (j->>'contentLength')::bigint IS DISTINCT FROM pg_catalog.octet_length(d.content)::bigint
 OR j->>'mediaType' IS DISTINCT FROM d.media_type OR j->'selection'->'product'->>'tenantId' IS DISTINCT FROM c.resource_tenant_id::text
 OR j->'selection'->>'purpose' IS DISTINCT FROM 'DEPOSIT_ACCOUNT_ENROLLMENT'
 OR j->'actor'->>'issuer' IS DISTINCT FROM a.actor_issuer OR j->'actor'->>'subject' IS DISTINCT FROM a.actor_subject
 OR j->'actor'->>'clientId' IS DISTINCT FROM a.actor_client_id OR j->'actor'->>'platformTenantId' IS DISTINCT FROM a.actor_platform_tenant_id
 OR j->'actor'->>'staffRealm' IS DISTINCT FROM a.actor_staff_realm OR (j->'actor'->>'staffId')::uuid IS DISTINCT FROM a.actor_staff_id
 OR j->>'policyRevision' IS DISTINCT FROM a.policy_revision OR j->>'mastersProvenance' IS DISTINCT FROM 'EFFECTIVE_ONLY'
 OR j->'selection'->'template'->>'digest' IS DISTINCT FROM pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(j->>'sourceCanonicalJson','UTF8')),'hex')
 OR j->'selection'->'layout'->>'digest' IS DISTINCT FROM pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to('MASTERS_DOCUMENT_LAYOUT_V1'||chr(10)||(j->>'layoutCanonicalJson'),'UTF8')),'hex')
 OR j->>'effectiveUntilEpochMillis' IS NULL OR j->>'observedAtEpochMillis' IS NULL
 OR j->>'issuerNativeUntilEpochMillis' IS NULL OR j->>'issuerEffectiveUntilEpochMillis' IS NULL OR j->>'issuerObservedAtEpochMillis' IS NULL
 OR j->>'sourceCanonicalJson' IS NULL OR j->>'schemaCanonicalJson' IS NULL OR j->>'layoutCanonicalJson' IS NULL
 OR j->'selection'->'template'->>'digest' IS NULL OR j->'selection'->'layout'->>'digest' IS NULL
 OR (j->>'effectiveUntilEpochMillis')::bigint<=(j->>'observedAtEpochMillis')::bigint
 OR (j->>'effectiveUntilEpochMillis')::bigint-(j->>'observedAtEpochMillis')::bigint>5000
 OR pg_catalog.to_timestamp((j->>'effectiveUntilEpochMillis')::numeric/1000)<c.until_time
 OR (j->>'issuerEffectiveUntilEpochMillis')::bigint>(j->>'issuerNativeUntilEpochMillis')::bigint
 OR (j->>'issuerNativeUntilEpochMillis')::bigint-(j->>'issuerObservedAtEpochMillis')::bigint>5000 THEN
  RAISE EXCEPTION 'Exact immutable document graph required' USING ERRCODE='23514';
 END IF;
 PERFORM firefly_audit.document_generation_require_audit(c.resource_tenant_id,c.document_id,c.event_id,c.request_digest,
  a.actor_issuer,a.actor_subject,a.actor_client_id,a.actor_platform_tenant_id,a.actor_staff_realm,a.actor_staff_id,a.policy_revision,m.intent_json,c.new_issuance);
END $$;
CREATE FUNCTION document_generation.complete(p_nonce UUID) RETURNS UUID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c document_generation_private.context%ROWTYPE;
BEGIN c:=document_generation_private.current_context();IF c.nonce IS DISTINCT FROM p_nonce THEN RAISE EXCEPTION 'Exact document nonce required' USING ERRCODE='42501';END IF;
 PERFORM document_generation_private.require_graph(c);INSERT INTO document_generation_private.completion VALUES(p_nonce);RETURN p_nonce;
END $$;
CREATE FUNCTION document_generation_private.commit_guard() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c document_generation_private.context%ROWTYPE;
BEGIN c:=document_generation_private.current_context();
 IF NOT EXISTS(SELECT 1 FROM document_generation_private.completion t WHERE t.nonce=c.nonce) THEN RAISE EXCEPTION 'Completed document graph required' USING ERRCODE='23514';END IF;
 PERFORM document_generation_private.require_graph(c);RETURN NULL;
END $$;
DO $$ DECLARE n TEXT;BEGIN
 FOREACH n IN ARRAY ARRAY['document','metadata','issuance','provider_intent'] LOOP
  EXECUTE pg_catalog.format('ALTER TABLE document_generation.%I ENABLE ROW LEVEL SECURITY',n);
  EXECUTE pg_catalog.format('ALTER TABLE document_generation.%I FORCE ROW LEVEL SECURITY',n);
  EXECUTE pg_catalog.format('CREATE POLICY owner_integrity ON document_generation.%I TO document_generation_guard USING(true) WITH CHECK(true)',n);
  EXECUTE pg_catalog.format('CREATE POLICY writer_scope ON document_generation.%I TO firefly_audit_writer USING(document_generation.scope_matches(resource_tenant_id,id,%s)) WITH CHECK(document_generation.scope_matches(resource_tenant_id,id,%s))',n,CASE WHEN n IN('issuance','provider_intent')THEN'event_id'ELSE'NULL::uuid'END,CASE WHEN n IN('issuance','provider_intent')THEN'event_id'ELSE'NULL::uuid'END);
  EXECUTE pg_catalog.format('CREATE TRIGGER immutable_generation BEFORE UPDATE OR DELETE ON document_generation.%I FOR EACH ROW EXECUTE FUNCTION document_generation_private.immutable_row()',n);
  EXECUTE pg_catalog.format('CREATE CONSTRAINT TRIGGER generation_commit_guard AFTER INSERT ON document_generation.%I DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_generation_private.commit_guard()',n);
 END LOOP;
 FOREACH n IN ARRAY ARRAY['admission','revocation','context','completion'] LOOP
  EXECUTE pg_catalog.format('CREATE TRIGGER immutable_generation BEFORE UPDATE OR DELETE ON document_generation_private.%I FOR EACH ROW EXECUTE FUNCTION document_generation_private.immutable_row()',n);
 END LOOP;
END $$;
CREATE CONSTRAINT TRIGGER generation_context_commit_guard AFTER INSERT ON document_generation_private.context DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_generation_private.commit_guard();
REVOKE ALL ON ALL TABLES IN SCHEMA document_generation,document_generation_private FROM PUBLIC,firefly_audit_writer,document_generation_admitter;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA document_generation,document_generation_private FROM PUBLIC,firefly_audit_writer,document_generation_admitter;
GRANT SELECT,INSERT ON document_generation.document,document_generation.metadata,document_generation.issuance,document_generation.provider_intent TO firefly_audit_writer;
GRANT EXECUTE ON FUNCTION document_generation.admit(UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,TEXT,TIMESTAMPTZ),document_generation.revoke(UUID) TO document_generation_admitter;
GRANT EXECUTE ON FUNCTION document_generation.enter(UUID,UUID,UUID,UUID,UUID,TEXT),document_generation.complete(UUID),document_generation.scope_matches(UUID,UUID,UUID) TO firefly_audit_writer;
RESET ROLE;
