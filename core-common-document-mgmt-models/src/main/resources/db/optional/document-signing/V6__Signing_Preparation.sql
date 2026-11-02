-- Explicit optional/manual preparation schema. No provider operation or legacy adoption occurs here.
BEGIN;
DO $$ BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'document_signing_guard','SET')
 OR NOT pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'CREATE') THEN
  RAISE EXCEPTION 'Separately delegated signing schema installer required' USING ERRCODE='42501';
 END IF;
 IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname IN('document_signing_guard','firefly_audit_writer')
 AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication
 AND rolcanlogin=(rolname='firefly_audit_writer'))<>2
 OR pg_catalog.pg_has_role('firefly_audit_writer','document_signing_guard','MEMBER')
 OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='document_signing_guard')) THEN
  RAISE EXCEPTION 'Exact separately provisioned signing roles required' USING ERRCODE='42501';
 END IF;
END $$;
CREATE SCHEMA document_signing AUTHORIZATION document_signing_guard;

CREATE UNIQUE INDEX signing_document_scope ON public.documents(id, tenant_id);
CREATE UNIQUE INDEX signing_version_scope ON public.document_versions(id, document_id, tenant_id);
CREATE UNIQUE INDEX signing_provider_scope ON public.signature_providers(id, tenant_id);
CREATE UNIQUE INDEX signing_signature_scope ON public.document_signatures(id, document_id, document_version_id, signature_provider_id, tenant_id);
CREATE UNIQUE INDEX signing_legacy_request_scope ON public.signature_requests(id, document_signature_id, tenant_id);

-- Explicit installer-only REFERENCES for scoped FKs; revoked after installation below.
GRANT REFERENCES(id,tenant_id) ON public.documents,public.signature_providers TO document_signing_guard;
GRANT REFERENCES(id,document_id,tenant_id) ON public.document_versions TO document_signing_guard;
GRANT REFERENCES(id,document_id,document_version_id,signature_provider_id,tenant_id) ON public.document_signatures TO document_signing_guard;
GRANT REFERENCES(id,document_signature_id,tenant_id) ON public.signature_requests TO document_signing_guard;
SET LOCAL ROLE document_signing_guard;
REVOKE ALL ON SCHEMA document_signing FROM PUBLIC,firefly_audit_writer;

CREATE TABLE document_signing.account_binding (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    provider_id UUID NOT NULL,
    binding_version BIGINT NOT NULL CHECK (binding_version > 0),
    provider_code VARCHAR(32) NOT NULL CHECK (provider_code = 'DOCUSIGN'),
    account_id VARCHAR(256) NOT NULL CHECK (account_id <> '' AND account_id = pg_catalog.btrim(account_id)),
    endpoint TEXT NOT NULL CHECK (endpoint ~ '^https://[^/?#@[:space:]]+(/[^?#[:space:]]*)?$'),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL CHECK (pg_catalog.isfinite(created_at)),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (id, binding_version, tenant_id, provider_id, account_id, endpoint),
    UNIQUE (tenant_id, provider_id, account_id, endpoint, binding_version),
    FOREIGN KEY (provider_id, tenant_id) REFERENCES public.signature_providers(id, tenant_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE document_signing.envelope (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    provider_id UUID NOT NULL,
    account_binding_id UUID NOT NULL,
    account_binding_version BIGINT NOT NULL,
    account_id VARCHAR(256) NOT NULL,
    endpoint TEXT NOT NULL,
    document_signature_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PREPARING', 'PREPARED')),
    external_envelope_id VARCHAR(128) CHECK (external_envelope_id IS NULL),
    created_at TIMESTAMPTZ NOT NULL CHECK (pg_catalog.isfinite(created_at)),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id),
    UNIQUE (tenant_id, provider_id, account_id, endpoint, external_envelope_id),
    FOREIGN KEY (document_id, tenant_id) REFERENCES public.documents(id, tenant_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (document_version_id, document_id, tenant_id) REFERENCES public.document_versions(id, document_id, tenant_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (document_signature_id, document_id, document_version_id, provider_id, tenant_id) REFERENCES public.document_signatures(id, document_id, document_version_id, signature_provider_id, tenant_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (account_binding_id, account_binding_version, tenant_id, provider_id, account_id, endpoint) REFERENCES document_signing.account_binding(id, binding_version, tenant_id, provider_id, account_id, endpoint) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE document_signing.operation (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    provider_id UUID NOT NULL,
    account_binding_id UUID NOT NULL,
    account_binding_version BIGINT NOT NULL,
    document_signature_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PREPARING', 'PREPARED')),
    generation BIGINT NOT NULL CHECK (generation IN (0, 1)),
    source_fingerprint CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    intent_json TEXT NOT NULL CHECK (pg_catalog.octet_length(intent_json) <= 16384 AND pg_catalog.jsonb_typeof(intent_json::jsonb) = 'object'),
    intent_digest CHAR(64) NOT NULL CHECK (intent_digest = pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(intent_json, 'UTF8')), 'hex')),
    authority_json TEXT NOT NULL CHECK (pg_catalog.octet_length(authority_json) <= 16384 AND pg_catalog.jsonb_typeof(authority_json::jsonb) = 'object'),
    authority_digest CHAR(64) NOT NULL CHECK (authority_digest = pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(authority_json, 'UTF8')), 'hex')),
    authority_until TIMESTAMPTZ NOT NULL CHECK (pg_catalog.isfinite(authority_until)),
    audit_resource_tenant_id UUID NOT NULL CHECK (audit_resource_tenant_id <> '00000000-0000-0000-0000-000000000000'),
    audit_process_id UUID NOT NULL CHECK (audit_process_id <> '00000000-0000-0000-0000-000000000000'),
    audit_correlation_id UUID NOT NULL CHECK (audit_correlation_id <> '00000000-0000-0000-0000-000000000000'),
    audit_policy_revision VARCHAR(128) NOT NULL CHECK (audit_policy_revision ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'),
    reservation_audit_event_id UUID NOT NULL UNIQUE,
    prepared_audit_event_id UUID UNIQUE,
    expected_content_length BIGINT NOT NULL CHECK (expected_content_length BETWEEN 1 AND 10485760),
    media_type VARCHAR(100) NOT NULL CHECK (media_type = 'application/pdf'),
    content BYTEA,
    content_digest CHAR(64),
    content_length BIGINT,
    created_at TIMESTAMPTZ NOT NULL CHECK (pg_catalog.isfinite(created_at)),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (authority_until > created_at),
    CHECK ((intent_json::jsonb->>'protocol' = 'CORE_SIGNING_PREPARATION_V1') IS TRUE),
    CHECK ((intent_json::jsonb->>'signatureId' = document_signature_id::text) IS TRUE),
    CHECK ((intent_json::jsonb->>'documentId' = document_id::text) IS TRUE),
    CHECK ((intent_json::jsonb->>'documentVersionId' = document_version_id::text) IS TRUE),
    CHECK ((intent_json::jsonb->>'providerId' = provider_id::text) IS TRUE),
    CHECK ((intent_json::jsonb->>'tenantId' = tenant_id) IS TRUE),
    CHECK ((authority_json::jsonb->>'protocol' = 'CORE_SIGNING_AUTHORITY_V1') IS TRUE),
    CHECK ((pg_catalog.length(authority_json::jsonb#>>'{actor,clientId}') BETWEEN 1 AND 1000) IS TRUE),
    CHECK (((authority_json::jsonb#>>'{binding,version}')::bigint > 0) IS TRUE),
    CHECK ((authority_json::jsonb#>>'{binding,definitionDigest}' ~ '^[0-9a-f]{64}$') IS TRUE),
    CHECK ((authority_json::jsonb#>>'{binding,bindingId}' ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') IS TRUE),
    CHECK ((authority_until <= (authority_json::jsonb#>>'{binding,notAfter}')::timestamptz) IS TRUE),
    CHECK ((authority_json::jsonb#>>'{binding,resourceTenant}' = tenant_id) IS TRUE),
    CHECK ((authority_json::jsonb#>>'{actor,issuer}' = authority_json::jsonb#>>'{binding,issuer}') IS TRUE),
    CHECK ((authority_json::jsonb#>>'{actor,subject}' = authority_json::jsonb#>>'{binding,subject}') IS TRUE),
    CHECK ((authority_json::jsonb#>>'{actor,platformTenantId}' = authority_json::jsonb#>>'{binding,platformTenant}') IS TRUE),
    CHECK (((authority_json::jsonb->>'notAfter')::timestamptz = authority_until) IS TRUE),
    CHECK ((state = 'PREPARING' AND generation = 0 AND content IS NULL AND content_digest IS NULL AND content_length IS NULL AND prepared_audit_event_id IS NULL)
        OR (state = 'PREPARED' AND generation = 1 AND content IS NOT NULL AND content_digest IS NOT NULL AND content_length IS NOT NULL AND prepared_audit_event_id IS NOT NULL
            AND content_length = expected_content_length AND content_length = pg_catalog.octet_length(content)
            AND content_digest = pg_catalog.encode(pg_catalog.sha256(content), 'hex'))),
    UNIQUE (id, envelope_id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id),
    UNIQUE (envelope_id),
    FOREIGN KEY (envelope_id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id)
        REFERENCES document_signing.envelope(id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE document_signing.request_binding (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    provider_id UUID NOT NULL,
    account_binding_id UUID NOT NULL,
    account_binding_version BIGINT NOT NULL,
    document_signature_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    legacy_request_id UUID CHECK (legacy_request_id IS NULL),
    planned_slot INTEGER NOT NULL CHECK (planned_slot = 1),
    external_recipient_id VARCHAR(128) CHECK (external_recipient_id IS NULL),
    created_at TIMESTAMPTZ NOT NULL CHECK (pg_catalog.isfinite(created_at)),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (operation_id, planned_slot),
    FOREIGN KEY (operation_id, envelope_id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id)
        REFERENCES document_signing.operation(id, envelope_id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (legacy_request_id, document_signature_id, tenant_id) REFERENCES public.signature_requests(id, document_signature_id, tenant_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE FUNCTION document_signing.require_phase_audit(operation document_signing.operation, event_id UUID, expected_action TEXT) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE expected_digest TEXT;
BEGIN
    expected_digest := pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(pg_catalog.concat_ws(E'\n',
        'CORE_SIGNING_EFFECT_V2', expected_action, operation.audit_resource_tenant_id::text,
        operation.id::text, operation.envelope_id::text, operation.account_binding_id::text,
        operation.account_binding_version::text, operation.document_signature_id::text, operation.document_id::text,
        operation.document_version_id::text, operation.provider_id::text, operation.intent_digest::text,
        operation.source_fingerprint::text, operation.authority_digest::text,
        CASE WHEN expected_action = 'signing.prepare.reserve' THEN 'NONE' ELSE operation.content_digest::text END), 'UTF8')), 'hex');
    PERFORM firefly_audit.document_signing_require_audit(operation.audit_resource_tenant_id, operation.id, event_id,
        expected_action, expected_digest, operation.authority_json::jsonb#>>'{actor,issuer}',
        operation.authority_json::jsonb#>>'{actor,subject}', operation.authority_json::jsonb#>>'{actor,clientId}',
        operation.authority_json::jsonb#>>'{actor,platformTenantId}', operation.audit_policy_revision,
        operation.audit_process_id, operation.audit_correlation_id,
        CASE WHEN expected_action='signing.prepare.complete' THEN operation.reservation_audit_event_id ELSE NULL END,
        operation.state='PREPARING' OR expected_action='signing.prepare.complete');
END;
$$;

CREATE FUNCTION document_signing.guard_operation_immutability() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM document_signing.require_phase_audit(NEW, NEW.reservation_audit_event_id, 'signing.prepare.reserve');
    IF NEW.state = 'PREPARED' THEN
        PERFORM document_signing.require_phase_audit(NEW, NEW.prepared_audit_event_id, 'signing.prepare.complete');
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.state IS DISTINCT FROM 'PREPARING' OR NEW.generation IS DISTINCT FROM 0
           OR NEW.content IS NOT NULL OR NEW.content_digest IS NOT NULL OR NEW.content_length IS NOT NULL THEN
            RAISE EXCEPTION 'Signing operation must begin incomplete' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.state IS DISTINCT FROM 'PREPARING' OR NEW.state IS DISTINCT FROM 'PREPARED' THEN
        RAISE EXCEPTION 'Signing operation permits one prepared transition' USING ERRCODE = '23514';
    END IF;
    IF ROW(NEW.id, NEW.envelope_id, NEW.tenant_id, NEW.provider_id, NEW.account_binding_id, NEW.account_binding_version,
           NEW.document_signature_id, NEW.document_id, NEW.document_version_id, NEW.source_fingerprint,
           NEW.intent_json, NEW.intent_digest, NEW.authority_json, NEW.authority_digest, NEW.authority_until,
           NEW.expected_content_length, NEW.media_type, NEW.created_at, NEW.audit_resource_tenant_id,
           NEW.audit_process_id, NEW.audit_correlation_id, NEW.audit_policy_revision, NEW.reservation_audit_event_id)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.envelope_id, OLD.tenant_id, OLD.provider_id, OLD.account_binding_id, OLD.account_binding_version,
           OLD.document_signature_id, OLD.document_id, OLD.document_version_id, OLD.source_fingerprint,
           OLD.intent_json, OLD.intent_digest, OLD.authority_json, OLD.authority_digest, OLD.authority_until,
           OLD.expected_content_length, OLD.media_type, OLD.created_at, OLD.audit_resource_tenant_id,
           OLD.audit_process_id, OLD.audit_correlation_id, OLD.audit_policy_revision, OLD.reservation_audit_event_id) THEN
        RAISE EXCEPTION 'Signing preparation identity and intent are immutable' USING ERRCODE = '23514';
    END IF;
    IF OLD.content IS NOT NULL AND ROW(NEW.content, NEW.content_digest, NEW.content_length)
       IS DISTINCT FROM ROW(OLD.content, OLD.content_digest, OLD.content_length) THEN
        RAISE EXCEPTION 'Captured signing content is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER signing_operation_immutable BEFORE INSERT OR UPDATE ON document_signing.operation
    FOR EACH ROW EXECUTE FUNCTION document_signing.guard_operation_immutability();

CREATE FUNCTION document_signing.guard_account_identity() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF session_user='firefly_audit_writer' THEN
        RAISE EXCEPTION 'Runtime signing account mutation is unavailable' USING ERRCODE='23514';
    END IF;
    IF ROW(NEW.id, NEW.tenant_id, NEW.provider_id, NEW.binding_version, NEW.provider_code, NEW.account_id, NEW.endpoint, NEW.created_at)
       IS DISTINCT FROM ROW(OLD.id, OLD.tenant_id, OLD.provider_id, OLD.binding_version, OLD.provider_code, OLD.account_id, OLD.endpoint, OLD.created_at) THEN
        RAISE EXCEPTION 'Registered signing account identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER signing_account_immutable BEFORE UPDATE ON document_signing.account_binding
    FOR EACH ROW EXECUTE FUNCTION document_signing.guard_account_identity();

CREATE FUNCTION document_signing.guard_envelope_identity() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF ROW(NEW.id, NEW.tenant_id, NEW.provider_id, NEW.account_binding_id, NEW.account_binding_version, NEW.account_id, NEW.endpoint,
           NEW.document_signature_id, NEW.document_id, NEW.document_version_id, NEW.external_envelope_id, NEW.created_at)
       IS DISTINCT FROM ROW(OLD.id, OLD.tenant_id, OLD.provider_id, OLD.account_binding_id, OLD.account_binding_version, OLD.account_id, OLD.endpoint,
           OLD.document_signature_id, OLD.document_id, OLD.document_version_id, OLD.external_envelope_id, OLD.created_at)
       OR OLD.state IS DISTINCT FROM 'PREPARING' OR NEW.state IS DISTINCT FROM 'PREPARED' THEN
        RAISE EXCEPTION 'Local signing envelope identity is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER signing_envelope_immutable BEFORE UPDATE ON document_signing.envelope
    FOR EACH ROW EXECUTE FUNCTION document_signing.guard_envelope_identity();

CREATE FUNCTION document_signing.guard_planned_request() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'Planned signing request identity is immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER signing_request_immutable BEFORE UPDATE ON document_signing.request_binding
    FOR EACH ROW EXECUTE FUNCTION document_signing.guard_planned_request();

-- The root cannot commit partial envelope/request business state without the matching audited operation.
CREATE FUNCTION document_signing.require_envelope_audit_graph() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE operation document_signing.operation%ROWTYPE;
BEGIN
    SELECT * INTO operation FROM document_signing.operation o WHERE o.envelope_id = NEW.id;
    IF operation.id IS NULL OR operation.state IS DISTINCT FROM NEW.state THEN
        RAISE EXCEPTION 'Complete audited signing envelope graph required' USING ERRCODE = '23514';
    END IF;
    PERFORM document_signing.require_phase_audit(operation, operation.reservation_audit_event_id, 'signing.prepare.reserve');
    IF operation.state = 'PREPARED' THEN
        PERFORM document_signing.require_phase_audit(operation, operation.prepared_audit_event_id, 'signing.prepare.complete');
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER signing_envelope_audit_graph AFTER INSERT OR UPDATE ON document_signing.envelope
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_signing.require_envelope_audit_graph();

CREATE FUNCTION document_signing.require_planned_request_graph() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM document_signing.request_binding r WHERE r.operation_id = NEW.id
            AND r.envelope_id = NEW.envelope_id AND r.tenant_id = NEW.tenant_id
            AND r.provider_id = NEW.provider_id AND r.account_binding_id = NEW.account_binding_id
            AND r.account_binding_version = NEW.account_binding_version AND r.document_signature_id = NEW.document_signature_id
            AND r.document_id = NEW.document_id AND r.document_version_id = NEW.document_version_id AND r.planned_slot = 1) THEN
        RAISE EXCEPTION 'Exact planned signing request graph required' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER signing_operation_audit_graph AFTER INSERT OR UPDATE ON document_signing.operation
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION document_signing.require_planned_request_graph();

CREATE FUNCTION document_signing.forbid_delete() RETURNS TRIGGER LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    RAISE EXCEPTION 'Signing preparation deletion is unavailable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER signing_account_no_delete BEFORE DELETE ON document_signing.account_binding
    FOR EACH ROW EXECUTE FUNCTION document_signing.forbid_delete();
CREATE TRIGGER signing_envelope_no_delete BEFORE DELETE ON document_signing.envelope
    FOR EACH ROW EXECUTE FUNCTION document_signing.forbid_delete();
CREATE TRIGGER signing_operation_no_delete BEFORE DELETE ON document_signing.operation
    FOR EACH ROW EXECUTE FUNCTION document_signing.forbid_delete();
CREATE TRIGGER signing_request_no_delete BEFORE DELETE ON document_signing.request_binding
    FOR EACH ROW EXECUTE FUNCTION document_signing.forbid_delete();

REVOKE ALL ON ALL TABLES IN SCHEMA document_signing FROM PUBLIC,firefly_audit_writer;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA document_signing FROM PUBLIC,firefly_audit_writer;
RESET ROLE;
REVOKE REFERENCES(id,tenant_id) ON public.documents,public.signature_providers FROM document_signing_guard;
REVOKE REFERENCES(id,document_id,tenant_id) ON public.document_versions FROM document_signing_guard;
REVOKE REFERENCES(id,document_id,document_version_id,signature_provider_id,tenant_id) ON public.document_signatures FROM document_signing_guard;
REVOKE REFERENCES(id,document_signature_id,tenant_id) ON public.signature_requests FROM document_signing_guard;
COMMIT;
