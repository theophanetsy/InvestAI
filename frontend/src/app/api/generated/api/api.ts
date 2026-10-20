export * from './auth.service';
import { AuthService } from './auth.service';
export * from './market.service';
import { MarketService } from './market.service';
export * from './portfolio.service';
import { PortfolioService } from './portfolio.service';
export * from './system.service';
import { SystemService } from './system.service';
export const APIS = [AuthService, MarketService, PortfolioService, SystemService];
