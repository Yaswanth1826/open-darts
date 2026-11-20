/** The rules for who may use the camera. Pure functions, so they are tested without Firebase. */

export const FREE_CAMERA_GAMES = 3;

/** What is stored per account (`users/{uid}`). Only the server writes it. */
export interface UserState {
  freeUsed: number;
  /** Subscription end in epoch milliseconds, 0 when there is none. */
  premiumUntil: number;
}

export interface Access {
  premium: boolean;
  freeLeft: number;
  premiumUntil: number;
}

export const emptyUser = (): UserState => ({ freeUsed: 0, premiumUntil: 0 });

/**
 * The free games are counted per account and per device, and the higher number counts, so a new Google account on the
 * same phone does not bring a new trial.
 */
export function accessOf(user: UserState, deviceUsed: number, now: number): Access {
  const premium = user.premiumUntil > now;
  const used = Math.max(user.freeUsed, deviceUsed);
  return { premium, freeLeft: Math.max(0, FREE_CAMERA_GAMES - used), premiumUntil: user.premiumUntil };
}

export interface StartResult {
  allowed: boolean;
  access: Access;
  user: UserState;
  deviceUsed: number;
}

/** A camera game is starting: subscribers pass for free, everyone else spends one free game or is refused. */
export function startCameraGame(user: UserState, deviceUsed: number, now: number): StartResult {
  const before = accessOf(user, deviceUsed, now);
  if (before.premium) return { allowed: true, access: before, user, deviceUsed };
  if (before.freeLeft <= 0) return { allowed: false, access: before, user, deviceUsed };
  const used = Math.max(user.freeUsed, deviceUsed) + 1;
  const next = { ...user, freeUsed: used };
  return { allowed: true, access: accessOf(next, used, now), user: next, deviceUsed: used };
}

/** The parts of a Google Play `subscriptionsv2` answer that matter here. */
export interface PlaySubscription {
  subscriptionState?: string | null;
  lineItems?: Array<{ productId?: string | null; expiryTime?: string | null }> | null;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string | null } | null;
}

/** Subscription states in which the player has paid for time they have not used up yet. */
const ENTITLED = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  // Cancelled means "will not renew": the paid period still runs to its end.
  "SUBSCRIPTION_STATE_CANCELED",
]);

export type Verdict = { ok: true; until: number } | { ok: false; reason: string };

/** Decides whether a Play subscription gives [accountId]'s owner the camera, and until when. */
export function judgeSubscription(sub: PlaySubscription, productId: string, accountId: string, now: number): Verdict {
  const line = sub.lineItems?.find((item) => item.productId === productId);
  if (!line) return { ok: false, reason: "wrong-product" };
  if (sub.externalAccountIdentifiers?.obfuscatedExternalAccountId !== accountId) return { ok: false, reason: "other-account" };
  if (!ENTITLED.has(sub.subscriptionState ?? "")) return { ok: false, reason: "not-active" };
  const until = Date.parse(line.expiryTime ?? "");
  if (!Number.isFinite(until) || until <= now) return { ok: false, reason: "expired" };
  return { ok: true, until };
}
