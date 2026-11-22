import assert from "node:assert/strict";
import { test } from "node:test";
import { accessOf, emptyUser, FREE_CAMERA_GAMES, judgeSubscription, sessionAllowed, startCameraGame, validSessionId } from "./access";

const NOW = 1_800_000_000_000;
const DAY = 86_400_000;

test("a new account has all free games", () => {
  assert.deepEqual(accessOf(emptyUser(), 0, NOW), { premium: false, freeLeft: FREE_CAMERA_GAMES, premiumUntil: 0 });
});

test("each start spends one free game, then is refused", () => {
  let user = emptyUser();
  let device = 0;
  for (let i = 0; i < FREE_CAMERA_GAMES; i++) {
    const r = startCameraGame(user, device, NOW);
    assert.equal(r.allowed, true);
    assert.equal(r.access.freeLeft, FREE_CAMERA_GAMES - i - 1);
    user = r.user;
    device = r.deviceUsed;
  }
  const refused = startCameraGame(user, device, NOW);
  assert.equal(refused.allowed, false);
  assert.equal(refused.user.freeUsed, FREE_CAMERA_GAMES);
});

test("a new account on a used-up device gets no new trial", () => {
  const r = startCameraGame(emptyUser(), FREE_CAMERA_GAMES, NOW);
  assert.equal(r.allowed, false);
});

test("a used-up account on a fresh device stays used up", () => {
  const r = startCameraGame({ freeUsed: FREE_CAMERA_GAMES, premiumUntil: 0 }, 0, NOW);
  assert.equal(r.allowed, false);
});

test("subscribers pass without spending free games", () => {
  const user = { freeUsed: FREE_CAMERA_GAMES, premiumUntil: NOW + DAY };
  const r = startCameraGame(user, FREE_CAMERA_GAMES, NOW);
  assert.equal(r.allowed, true);
  assert.equal(r.access.premium, true);
  assert.equal(r.user.freeUsed, FREE_CAMERA_GAMES);
});

test("an ended subscription no longer passes", () => {
  const user = { freeUsed: FREE_CAMERA_GAMES, premiumUntil: NOW - 1 };
  assert.equal(startCameraGame(user, FREE_CAMERA_GAMES, NOW).allowed, false);
});

const sub = (state: string, expiry: number, account = "acc", product = "camera_scoring") => ({
  subscriptionState: state,
  lineItems: [{ productId: product, expiryTime: new Date(expiry).toISOString() }],
  externalAccountIdentifiers: { obfuscatedExternalAccountId: account },
});

test("active, grace period and cancelled-but-paid subscriptions count", () => {
  for (const state of ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]) {
    const v = judgeSubscription(sub(state, NOW + DAY), "camera_scoring", "acc", NOW);
    assert.deepEqual(v, { ok: true, until: NOW + DAY });
  }
});

test("on hold, paused, expired and pending subscriptions do not count", () => {
  for (const state of ["SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING"]) {
    const v = judgeSubscription(sub(state, NOW + DAY), "camera_scoring", "acc", NOW);
    assert.equal(v.ok, false);
  }
});

test("a purchase made by another account is refused", () => {
  const v = judgeSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", NOW + DAY, "someone-else"), "camera_scoring", "acc", NOW);
  assert.deepEqual(v, { ok: false, reason: "other-account" });
});

test("a different product is refused", () => {
  const v = judgeSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", NOW + DAY, "acc", "other"), "camera_scoring", "acc", NOW);
  assert.deepEqual(v, { ok: false, reason: "wrong-product" });
});

test("a subscription whose end has passed is refused even when marked active", () => {
  const v = judgeSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", NOW - 1), "camera_scoring", "acc", NOW);
  assert.deepEqual(v, { ok: false, reason: "expired" });
});

test("an account nobody has claimed yet accepts any session", () => {
  assert.equal(sessionAllowed(emptyUser(), "anything-at-all-1234"), true);
  assert.equal(sessionAllowed(emptyUser(), undefined), true);
});

test("only the newest sign-in may use a claimed account", () => {
  const user = { ...emptyUser(), sessionId: "newest-session-0123456789" };
  assert.equal(sessionAllowed(user, "newest-session-0123456789"), true);
  assert.equal(sessionAllowed(user, "older-session-0123456789"), false);
  assert.equal(sessionAllowed(user, undefined), false);
  assert.equal(sessionAllowed(user, 42), false);
});

test("session ids must be long enough to be unguessable", () => {
  assert.equal(validSessionId("short"), false);
  assert.equal(validSessionId("x".repeat(101)), false);
  assert.equal(validSessionId(123), false);
  assert.equal(validSessionId("0f8fad5b-d9cb-469f-a165-70867728950e"), true);
});
