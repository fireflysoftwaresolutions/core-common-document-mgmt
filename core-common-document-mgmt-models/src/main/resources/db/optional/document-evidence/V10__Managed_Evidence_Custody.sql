-- Optional owner installation; V1-V9 and existing document custody are unchanged.
-- Provision the independent NOLOGIN document_evidence_guard and its two empty schemas first.
BEGIN;
SET LOCAL search_path=pg_catalog;
DO $$ DECLARE owner_oid OID;guard_oid OID;BEGIN
 SELECT oid INTO STRICT guard_oid FROM pg_catalog.pg_roles WHERE rolname='document_evidence_guard'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication;
 SELECT relowner INTO STRICT owner_oid FROM pg_catalog.pg_class WHERE oid='public.documents'::pg_catalog.regclass AND relkind='r';
 IF owner_oid IS DISTINCT FROM (SELECT oid FROM pg_catalog.pg_roles WHERE rolname=current_user)
  OR NOT pg_catalog.pg_has_role(current_user,guard_oid,'SET')
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=guard_oid)
  OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_writer' AND rolcanlogin
    AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)
  OR pg_catalog.pg_has_role('firefly_audit_writer',guard_oid,'MEMBER')
  OR pg_catalog.current_setting('server_encoding')<>'UTF8'
  OR (SELECT count(*) FROM pg_catalog.pg_namespace WHERE nspname IN('document_evidence','document_evidence_private') AND nspowner=guard_oid)<>2
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_namespace n,LATERAL pg_catalog.aclexplode(coalesce(n.nspacl,pg_catalog.acldefault('n',n.nspowner))) a
    WHERE n.nspname IN('document_evidence','document_evidence_private') AND a.grantee<>guard_oid)
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN('document_evidence','document_evidence_private')) THEN
  RAISE EXCEPTION 'Exact independent managed evidence installer required' USING ERRCODE='42501';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
  JOIN pg_catalog.pg_roles r ON r.oid=p.proowner WHERE n.nspname='public' AND p.proname='firefly_audit_require_completed'
  AND p.proargtypes='2950 2950 20 25 16'::pg_catalog.oidvector AND p.prosecdef AND r.rolname='firefly_audit_guard'
  AND p.proconfig=ARRAY['search_path=pg_catalog'] AND p.provolatile='v'
  AND pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(p.prosrc,'UTF8')),'hex')='c839ff938bc0695dd61a0d9a80ec537860025f051bc9e02f19572b91e6b0dccf'
  AND pg_catalog.has_function_privilege('document_evidence_guard',p.oid,'EXECUTE')) THEN
  RAISE EXCEPTION 'Published completed canonical audit deployment required' USING ERRCODE='42501';
 END IF;
END $$;
GRANT SELECT,REFERENCES ON public.documents TO document_evidence_guard;
SET LOCAL ROLE document_evidence_guard;
CREATE TABLE document_evidence.content(
 document_id UUID PRIMARY KEY CHECK(document_id<>'00000000-0000-0000-0000-000000000000'),
 resource_tenant_id UUID NOT NULL CHECK(resource_tenant_id<>'00000000-0000-0000-0000-000000000000'),
 content BYTEA NOT NULL CHECK(pg_catalog.octet_length(content) BETWEEN 1 AND 2097152),
 content_digest TEXT NOT NULL CHECK((content_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 content_length BIGINT NOT NULL CHECK(content_length=pg_catalog.octet_length(content)),
 CHECK(content_digest=pg_catalog.encode(pg_catalog.sha256(content),'hex')),
 UNIQUE(document_id,resource_tenant_id,content_digest,content_length),
 FOREIGN KEY(document_id) REFERENCES public.documents(id) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE document_evidence.custody(
 document_id UUID PRIMARY KEY CHECK(document_id<>'00000000-0000-0000-0000-000000000000'),
 resource_tenant_id UUID NOT NULL,action_id UUID NOT NULL CHECK(action_id<>'00000000-0000-0000-0000-000000000000'),
 application_id UUID NOT NULL CHECK(application_id<>'00000000-0000-0000-0000-000000000000'),
 party_id UUID NOT NULL CHECK(party_id<>'00000000-0000-0000-0000-000000000000'),
 file_name VARCHAR(255) NOT NULL CHECK(pg_catalog.length(file_name)>0 AND file_name=pg_catalog.btrim(file_name) AND file_name NOT IN('.','..') AND file_name !~ E'[/\\\\[:cntrl:]]'),
 media_type VARCHAR(100) NOT NULL CHECK((media_type COLLATE "C") ~ '^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$'),
 document_type_code VARCHAR(50) NOT NULL CHECK((document_type_code COLLATE "C") ~ '^[A-Z][A-Z0-9_]{0,49}$'),
 content_digest TEXT NOT NULL CHECK((content_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),content_length BIGINT NOT NULL CHECK(content_length BETWEEN 1 AND 2097152),
 intent_digest TEXT NOT NULL CHECK((intent_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 effect_digest TEXT NOT NULL CHECK((effect_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,actor_client_id TEXT NOT NULL,actor_platform_tenant_id VARCHAR(100) NOT NULL,
 binding_id UUID NOT NULL CHECK(binding_id<>'00000000-0000-0000-0000-000000000000'),binding_version BIGINT NOT NULL CHECK(binding_version>0),
 binding_digest TEXT NOT NULL CHECK((binding_digest COLLATE "C") ~ '^[0-9a-f]{64}$'),
 audit_context_nonce UUID NOT NULL CHECK(audit_context_nonce<>'00000000-0000-0000-0000-000000000000'),
 audit_event_id UUID NOT NULL CHECK(audit_event_id<>'00000000-0000-0000-0000-000000000000'),
 audit_sequence BIGINT NOT NULL CHECK(audit_sequence>0),audit_record_hash TEXT NOT NULL CHECK((audit_record_hash COLLATE "C") ~ '^[0-9a-f]{64}$'),
 policy_revision VARCHAR(128) NOT NULL CHECK((policy_revision COLLATE "C") ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(resource_tenant_id,action_id),UNIQUE(resource_tenant_id,audit_event_id),
 FOREIGN KEY(document_id,resource_tenant_id,content_digest,content_length) REFERENCES document_evidence.content(document_id,resource_tenant_id,content_digest,content_length) DEFERRABLE INITIALLY DEFERRED
);
CREATE FUNCTION document_evidence_private.text(value TEXT) RETURNS BYTEA
LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE encoded BYTEA;BEGIN
 IF value IS NULL OR pg_catalog.octet_length(value)>4096 THEN RAISE EXCEPTION 'Bounded evidence framing required' USING ERRCODE='23514';END IF;
 encoded:=pg_catalog.convert_to(value,'UTF8');RETURN pg_catalog.int4send(pg_catalog.octet_length(encoded))||encoded;
END $$;
CREATE FUNCTION document_evidence_private.require_graph(document UUID) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c document_evidence.custody%ROWTYPE;b document_evidence.content%ROWTYPE;d public.documents%ROWTYPE;
 intent TEXT;effect TEXT;seconds BIGINT;nanos INTEGER;completed RECORD;
BEGIN
 SELECT * INTO STRICT c FROM document_evidence.custody WHERE document_id=document;
 SELECT * INTO STRICT b FROM document_evidence.content WHERE document_id=document;
 SELECT * INTO STRICT d FROM public.documents WHERE id=document;
 seconds:=pg_catalog.floor(extract(epoch FROM c.created_at))::bigint;
 nanos:=((extract(epoch FROM c.created_at)-seconds)*1000000000)::integer;
 intent:=pg_catalog.encode(pg_catalog.sha256(document_evidence_private.text('FIREFLY_DOCUMENT_EVIDENCE_UPLOAD_1')
  ||pg_catalog.uuid_send(c.action_id)||pg_catalog.uuid_send(c.application_id)||pg_catalog.uuid_send(c.party_id)
  ||document_evidence_private.text(c.file_name)||document_evidence_private.text(c.media_type)||document_evidence_private.text(c.document_type_code)
  ||pg_catalog.int4send(c.content_length::integer)||document_evidence_private.text(c.content_digest)),'hex');
 effect:=pg_catalog.encode(pg_catalog.sha256(document_evidence_private.text('FIREFLY_DOCUMENT_EVIDENCE_EFFECT_1')
  ||document_evidence_private.text(intent)||pg_catalog.uuid_send(c.resource_tenant_id)||pg_catalog.uuid_send(c.document_id)
  ||pg_catalog.uuid_send(c.binding_id)||pg_catalog.int8send(c.binding_version)||document_evidence_private.text(c.binding_digest)
  ||document_evidence_private.text(c.actor_issuer)||document_evidence_private.text(c.actor_subject)||document_evidence_private.text(c.actor_client_id)
  ||document_evidence_private.text(c.actor_platform_tenant_id)||document_evidence_private.text(c.policy_revision)
  ||pg_catalog.int8send(seconds)||pg_catalog.int4send(nanos)),'hex');
 IF c.resource_tenant_id IS DISTINCT FROM b.resource_tenant_id OR c.content_digest IS DISTINCT FROM b.content_digest OR c.content_length IS DISTINCT FROM b.content_length
  OR c.content_length IS DISTINCT FROM pg_catalog.octet_length(b.content)::bigint OR c.content_digest IS DISTINCT FROM pg_catalog.encode(pg_catalog.sha256(b.content),'hex')
  OR c.intent_digest IS DISTINCT FROM intent OR c.effect_digest IS DISTINCT FROM effect
  OR (d.name,d.description,d.file_name,d.file_extension,d.mime_type,d.file_size,d.document_type::text,d.document_status::text,d.storage_type::text,d.storage_path,d.security_level::text,
      d.folder_id,d.is_encrypted,d.is_indexed,d.is_locked,d.locked_by,d.locked_until,d.expiration_date,d.retention_date,d.tenant_id,d.created_at,d.created_by,d.updated_at,d.updated_by,d.version,d.checksum)
     IS DISTINCT FROM (c.file_name,NULL::text,c.file_name,NULL::varchar,c.media_type,c.content_length,'DOCUMENT'::text,'UNDER_REVIEW'::text,'DATABASE'::text,'document-evidence/'||c.document_id::text,'RESTRICTED'::text,
      NULL::uuid,false,false,false,NULL::varchar,NULL::timestamptz,NULL::timestamptz,NULL::timestamptz,c.actor_platform_tenant_id,c.created_at,NULL::varchar,NULL::timestamptz,NULL::varchar,0::bigint,c.content_digest) THEN
  RAISE EXCEPTION 'Exact immutable managed evidence graph required' USING ERRCODE='23514';
 END IF;
 SELECT * INTO STRICT completed FROM public.firefly_audit_require_completed(c.audit_context_nonce,c.audit_event_id,c.audit_sequence,c.audit_record_hash,false);
 IF (completed.resource_tenant_id,completed.service,completed.resource_type,completed.resource_id,completed.event_id,
     completed.actor_issuer,completed.actor_subject,completed.actor_client_id,completed.actor_platform_tenant_id,completed.actor_staff_realm,completed.actor_staff_id,
     completed.action,completed.outcome,completed.process_id,completed.correlation_id,completed.causation_id,completed.occurred_seconds,completed.occurred_nanos,completed.evidence_digest,completed.policy_revision)
    IS DISTINCT FROM(c.resource_tenant_id,'core-document-management'::text,'document'::text,c.document_id,c.audit_event_id,
     c.actor_issuer,c.actor_subject,c.actor_client_id,c.actor_platform_tenant_id,NULL::text,NULL::uuid,
     'document.evidence.upload'::text,'COMPLETED'::text,c.action_id,c.action_id,NULL::uuid,seconds,nanos,c.effect_digest,c.policy_revision) THEN
  RAISE EXCEPTION 'Exact completed managed evidence audit required' USING ERRCODE='23514';
 END IF;
END $$;
CREATE FUNCTION document_evidence_private.graph_insert() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF TG_TABLE_SCHEMA='public' THEN
  IF NEW.storage_path='document-evidence/'||NEW.id::text OR EXISTS(SELECT 1 FROM document_evidence.custody WHERE document_id=NEW.id) THEN PERFORM document_evidence_private.require_graph(NEW.id);END IF;
 ELSE PERFORM document_evidence_private.require_graph(NEW.document_id);END IF;
 RETURN NULL;
END $$;
CREATE FUNCTION document_evidence_private.immutable() RETURNS TRIGGER
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 IF TG_TABLE_SCHEMA<>'public' THEN
  RAISE EXCEPTION 'Managed evidence custody cannot be mutated or deleted' USING ERRCODE='23514';
 ELSIF TG_OP='TRUNCATE' THEN
  IF EXISTS(SELECT 1 FROM document_evidence.custody) THEN RAISE EXCEPTION 'Managed evidence custody cannot be mutated or deleted' USING ERRCODE='23514';END IF;
 ELSIF EXISTS(SELECT 1 FROM document_evidence.custody WHERE document_id=OLD.id) THEN
  RAISE EXCEPTION 'Managed evidence custody cannot be mutated or deleted' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD;ELSIF TG_OP='UPDATE' THEN RETURN NEW;ELSE RETURN NULL;END IF;
END $$;
CREATE CONSTRAINT TRIGGER evidence_content_graph AFTER INSERT ON document_evidence.content DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_evidence_private.graph_insert();
CREATE CONSTRAINT TRIGGER evidence_custody_graph AFTER INSERT ON document_evidence.custody DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_evidence_private.graph_insert();
CREATE TRIGGER evidence_content_immutable BEFORE UPDATE OR DELETE ON document_evidence.content FOR EACH ROW EXECUTE FUNCTION document_evidence_private.immutable();
CREATE TRIGGER evidence_custody_immutable BEFORE UPDATE OR DELETE ON document_evidence.custody FOR EACH ROW EXECUTE FUNCTION document_evidence_private.immutable();
CREATE TRIGGER evidence_content_no_truncate BEFORE TRUNCATE ON document_evidence.content FOR EACH STATEMENT EXECUTE FUNCTION document_evidence_private.immutable();
CREATE TRIGGER evidence_custody_no_truncate BEFORE TRUNCATE ON document_evidence.custody FOR EACH STATEMENT EXECUTE FUNCTION document_evidence_private.immutable();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA document_evidence_private FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA document_evidence FROM PUBLIC;
DO $$ BEGIN
 EXECUTE pg_catalog.format('GRANT USAGE ON SCHEMA document_evidence_private TO %I',session_user);
 EXECUTE pg_catalog.format('GRANT EXECUTE ON FUNCTION document_evidence_private.graph_insert(),document_evidence_private.immutable() TO %I',session_user);
END $$;
GRANT USAGE ON SCHEMA document_evidence TO firefly_audit_writer;
GRANT SELECT,INSERT ON document_evidence.content,document_evidence.custody TO firefly_audit_writer;
RESET ROLE;
CREATE CONSTRAINT TRIGGER evidence_legacy_graph AFTER INSERT ON public.documents DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_evidence_private.graph_insert();
CREATE TRIGGER evidence_legacy_immutable BEFORE UPDATE OR DELETE ON public.documents FOR EACH ROW EXECUTE FUNCTION document_evidence_private.immutable();
CREATE TRIGGER evidence_legacy_no_truncate BEFORE TRUNCATE ON public.documents FOR EACH STATEMENT EXECUTE FUNCTION document_evidence_private.immutable();
-- Deployment grants are explicit and scoped to the existing canonical writer login.
GRANT SELECT,INSERT ON public.documents TO firefly_audit_writer;
SET LOCAL ROLE document_evidence_guard;
DO $$ BEGIN
 EXECUTE pg_catalog.format('REVOKE EXECUTE ON FUNCTION document_evidence_private.graph_insert(),document_evidence_private.immutable() FROM %I',session_user);
 EXECUTE pg_catalog.format('REVOKE USAGE ON SCHEMA document_evidence_private FROM %I',session_user);
END $$;
RESET ROLE;
COMMIT;
