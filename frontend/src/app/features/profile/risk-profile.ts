import { ProfileRequestRiskProfileEnum, RiskProfileResponseCodeEnum } from '../../api/generated';

/**
 * The generated client has one enum per DTO (`RiskProfileResponseCodeEnum`,
 * `ProfileResponseRiskProfileEnum`, `ProfileRequestRiskProfileEnum`) for the same values: the form
 * keeps the code as a string and converts it back here before saving. Typed mapping: a profile
 * added by the backend fails the compilation until it is listed here.
 */
const REQUEST_BY_CODE: Record<RiskProfileResponseCodeEnum, ProfileRequestRiskProfileEnum> = {
  [RiskProfileResponseCodeEnum.Prudent]: ProfileRequestRiskProfileEnum.Prudent,
  [RiskProfileResponseCodeEnum.Balanced]: ProfileRequestRiskProfileEnum.Balanced,
  [RiskProfileResponseCodeEnum.Dynamic]: ProfileRequestRiskProfileEnum.Dynamic,
  [RiskProfileResponseCodeEnum.Aggressive]: ProfileRequestRiskProfileEnum.Aggressive,
};

export function toRequestRiskProfile(code: string): ProfileRequestRiskProfileEnum | null {
  return Object.entries(REQUEST_BY_CODE).find(([key]) => key === code)?.[1] ?? null;
}
