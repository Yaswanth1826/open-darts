import assert from "node:assert/strict";
import { test } from "node:test";
import { accessOf, emptyUser, FREE_CAMERA_GAMES, judgeSubscription, startCameraGame } from "./access";

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

const sub = (state: string, expiry: number, account = "acc", product = "camera_monthly") => ({
  subscriptionState: state,
  lineItems: [{ productId: product, expiryTime: new Date(expiry).toISOString() }],
  externalAccountIdentifiers: { obfuscatedExternalAccountId: account },
});

test("active, grace period and cancelled-but-paid subscriptions count", () => {
  for (const state of ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]) {
    const v = judgeSubscription(sub(state, NOW + DAY), "camera_monthly", "acc", NOW);
    assert.deepEqual(v, { ok: true, until: NOW + DAY });
  }
});

test("on hold, paused, expired and pending subscriptions do not count", () => {
  for (const state of ["SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING"]) {
    const v = judgeSubscription(sub(state, NOW + DAY), "camera_monthly", "acc", NOW);
    assert.equal(v.ok, false);
  }
});

test("a purchase made by another account is refused", () => {
  const v = judgeSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", NOW + DAY, "someone-else"), "camera_monthly", "acc", NOW);
  assert.deepEqual(v, { ok: false, reason: "other-account" });
});

test("a different product is refused", () => {
  const v = judgeSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", NOW + DAY, "acc", "other"), "camera_monthly", "acc", NOW);
  assert.deepEqual(v, { ok: false, reason: "wrong-product" });
});

test("a subscription whose end has passed is refused even when marked active", () => {
  const v = judgeSubscription(sub("SUBSCRIPTION_STATE_ACTIVE", NOW - 1), "camera_monthly", "acc", NOW);
  assert.deepEqual(v, { ok: false, reason: "expired" });
});
