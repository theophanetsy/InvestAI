-- PEA eligibility (phase 2.3, spec docs/specs/phase-2-03-profile-portfolio.md, criterion 7).
-- Only the owner's correction is stored: the rule (PeaEligibilityRule) is applied when reading, so
-- neither a change of the rule nor the weekly reference refresh (which never writes these columns)
-- can overwrite a correction.

ALTER TABLE instrument
    ADD COLUMN pea_eligibility_correction   varchar(16)
        CHECK (pea_eligibility_correction IN ('ELIGIBLE', 'NOT_ELIGIBLE', 'UNKNOWN')),
    ADD COLUMN pea_eligibility_corrected_at timestamptz,
    ADD CONSTRAINT instrument_pea_eligibility_correction_time
        CHECK ((pea_eligibility_correction IS NULL) = (pea_eligibility_corrected_at IS NULL));

COMMENT ON COLUMN instrument.pea_eligibility_correction IS
    'PEA eligibility set by the owner, null when the rule applies (EU/EEA shares eligible, other '
    'shares not eligible, funds and ETFs unknown, configured benchmarks eligible).';
