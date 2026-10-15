export * from './auth.service';
import { AuthService } from './auth.service';
export * from './system.service';
import { SystemService } from './system.service';
export const APIS = [AuthService, SystemService];
