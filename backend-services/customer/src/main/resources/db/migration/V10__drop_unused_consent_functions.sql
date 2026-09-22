-- =============================================================================
-- V10: Drop the unused consent query functions created in V9
-- PIN-277: schema drift outside Hibernate validate's reach
-- =============================================================================
--
-- V9 created three functions that nothing has ever called. Every consent query
-- the application performs goes through ConsentRecordRepository instead:
--
--   get_current_consents  ->  findCurrentConsentsByCustomerId (GetConsentsUseCase)
--                             the same DISTINCT ON query, as a @Query
--   get_consent_history   ->  findByCustomerId (ExportConsentHistoryUseCase,
--                             the GDPR export)
--   get_consent_version   ->  getCurrentVersion
--
-- They are dropped rather than wired up for two reasons. get_current_consents
-- derives is_required as `CASE WHEN consent_type = 'DATA_PROCESSING'`, which
-- duplicates ConsentType.required — a business rule the enum owns and the use
-- case reads, so the SQL copy can only ever drift from it. And the functions
-- did not exist in any environment until PIN-272 fixed V9's quoting, without
-- anything noticing, which is what being uncalled looks like from the outside.
--
-- ADR 0028 cited get_current_consents as the mitigation for query complexity.
-- That mitigation still holds; it lives in the repository's native query. The
-- ADR is updated to say so.
--
-- V9 is edited forward rather than in place: it applies cleanly as of PIN-272,
-- so changing it now would invalidate the Flyway checksum where it has run.

DROP FUNCTION IF EXISTS get_current_consents(UUID);
DROP FUNCTION IF EXISTS get_consent_history(UUID);
DROP FUNCTION IF EXISTS get_consent_version(UUID, VARCHAR);
