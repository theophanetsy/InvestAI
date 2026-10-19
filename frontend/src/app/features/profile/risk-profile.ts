import { ProfileRequestRiskProfileEnum } from '../../api/generated';

const REQUEST_VALUES = Object.values(ProfileRequestRiskProfileEnum);

/**
 * The generated client has one enum per DTO (`RiskProfileResponseCodeEnum`,
 * `ProfileResponseRiskProfileEnum`, `ProfileRequestRiskProfileEnum`) for the same values: the form
 * keeps the code as a string and converts it back here before saving.
 */
export function toRequestRiskProfile(code: string): ProfileRequestRiskProfileEnum | null {
  return REQUEST_VALUES.find((value) => isCode(value, code)) ?? null;
}

function isCode(value: string, code: string): boolean {
  return value === code;
}
