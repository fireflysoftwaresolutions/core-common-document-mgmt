# Owned local document storage and pending provider intent

This optional source component adds a tenant-scoped local storage transaction to the existing document owner. It consumes the exact selection snapshot and rendered bytes as untrusted inputs. It does not activate the legacy `DocumentPort`, change FreeMarker rendering, replace stationery, start a provider worker, or make document generation available.

## Local effect and authority

`SpringDataDocumentGenerationCommitService` constructs its bounded root from one verified factory/client/template/transaction-manager graph. The caller's original technical budget is retained; the current snapshot and a five-second initial limit can only narrow it. A missing current admission result, wrong actor/tenant/document/event/digest/policy, expired grant, or grant beyond the owner snapshot refuses before SQL acquisition. A technical budget, snapshot DTO, audit event or actor field is not a permission grant.

The separately configured `DocumentGenerationAdmission` must use real actor authentication, issuer/delegation and current-owner policy to create the protected native grant. No implementation, default grant, signed-claim shortcut or sandbox authority is supplied. A genuine `TransactionalAuditAppender` and actual current `AuditAppendAuthority` are independently mandatory. There is no automatic Spring bean or property that can enable this implementation without those prerequisites.

One actual `firefly_audit_writer` physical session performs the local transaction. This exact session identity preserves the existing framework audit contract. The root is physically read/write at READ COMMITTED; protected grant/context checks and row ownership are native. The exact immutable audit append precedes any business read. Ordinary persistence uses Spring Data derived lookup and assigned-ID entity inserts; native functions handle only admission and final graph integrity.

The local graph contains four immutable mapped rows: rendered document bytes and SHA-256; complete exact-source intent metadata and its digest; issuance identity paired to the exact original audit event; and a provider intent in `PENDING_PROVIDER`. Rows bind resource tenant/document/event and request digest. An exact replay cannot append a second business/outbox graph or change the retained audit event. A conflicting request refuses. Any error marks the same owning scope rollback-only; success is returned only from actual committed, completed, clean root facts. A timeout or late response failure is not evidence of no effect and must not trigger an automatic retry.

The document-owned `DOCUMENT_LOCAL_STORAGE_V1` intent includes selection, raw canonical source/schema/layout bytes, rendered content hash/length, current observation/native/effective evidence, actor, policy and causal event identity. Its event `evidenceDigest` is excluded from the digest input to avoid a circular hash, then required to equal that digest. This is the document owner's intent protocol; it does not duplicate an upstream owner's canonicalization algorithm.

## Optional schema and deployment

Public default Flyway V1–V3 are unchanged. Install the two new files from `db/optional/document-generation` only through an explicitly reviewed transactional migration location after the exact genuine framework immutable-audit deployment. They are new aggregate migrations, not restoration of historical private signature/binding files. A database with incompatible private historical V4/V5 requires an explicit separate migration plan; never baseline, repair, overwrite, or relabel that history.

Provision `document_generation_guard` as non-login/non-super/non-bypass/non-inheriting, and `document_generation_admitter` as a separately trusted login with no guard or other role membership. Preserve the exact restricted `firefly_audit_writer` login. The installer needs explicit SET delegation to both document and audit guards plus database schema creation permission. Application roles get no private-schema access, legacy-table grant, public-schema creation or broad role alias. Only the trusted admitter can issue/revoke protected finite grants. Document functions use pinned `pg_catalog` search paths and fully qualified helper calls.

The current context binds grant, nonce, assigned transaction ID, backend PID, physical session, resource tenant/document/event and digest. Arbitrary SET/GUC values cannot create it. Forced RLS scopes the four aggregate tables. Native completion verifies the entire immutable graph plus the actual completed audit context/hash graph and exact original event body. Issuance retains a permanent FK to that actual audit event. Private grant/context/completion rows remain immutable evidence; no successful cleanup deletes them.

A real gate must independently pin role memberships/attributes, schema and base/function ACLs, definer identity/path, all function bodies, forced RLS/policies, mapped schema, exact audit deployment and original V1–V3 history. No SQL parser, privilege, transaction, native catalogue or crash/restart proof is established by source controls alone.

Deferred finite commit checks follow the controlled owning-root model. PostgreSQL does not make this an adversarial raw-session commit-deadline guarantee: an authorized raw session can change deferrable constraint timing. No claim that arbitrary `SET CONSTRAINTS` is disabled is made. Final application budget checks and actual native/late-commit proof remain required; this limitation stays open.

## Provider and recovery boundary

The committed result explicitly reports `PENDING_PROVIDER`, never delivered, signed or financially issued. No provider is called while the SQL transaction is open. The durable intent retains event, request and content identity for a later worker using the existing document storage/provider ports and explicit scoped authority. Provider delivery requires a durable attempt/outcome journal, idempotency/readback, monitored unknown outcomes, immutable audit and actual restart/recovery qualification before activation. Those pieces are not supplied by a successful local SQL commit.

Within the still-current original snapshot/budget, exact local replay can read the retained graph without another local mutation. Long-lived read/recovery after the original window, provider scheduling/acknowledgement and expiry recovery need separately admitted lifecycle operations; this source never renews the original issue authority to fake them.

A coherent original document reactor/SDK build, original signed owner/delegation integration, actual native schema/audit transaction, rendering/storage/provider acceptance, complete product families and financial/legal/signing/release acceptance remain open.
