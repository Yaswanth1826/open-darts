import { createHash } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineString } from "firebase-functions/params";
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { accessOf, emptyUser, judgeSubscription, PlaySubscription, startCameraGame as decideStart, UserState } from "./access";

initializeApp();
// Next to the Firestore database (eur3); the app calls the same region.
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });
const db = getFirestore();

const packageName = defineString("PLAY_PACKAGE", { default: "com.opendarts" });
const productId = defineString("PLAY_PRODUCT", { default: "camera_monthly" });

const sha256 = (text: string) => createHash("sha256").update(text).digest("hex");
// googleapis is slow to load, so it is only loaded when a purchase is checked (keeps deployment and cold starts quick).
const publisher = async () => {
  const { google } = await import("googleapis");
  return google.androidpublisher({ version: "v3", auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }) });
};

function uidOf(request: { auth?: { uid: string } | undefined }): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  return request.auth.uid;
}

async function readUser(uid: string): Promise<UserState> {
  const snap = await db.doc(`users/${uid}`).get();
  return { ...emptyUser(), ...(snap.data() as Partial<UserState> | undefined) };
}

const deviceRef = (deviceId: unknown) => {
  if (typeof deviceId !== "string" || deviceId.length < 8 || deviceId.length > 200) throw new HttpsError("invalid-argument", "Missing device id.");
  return db.doc(`devices/${sha256(deviceId)}`);
};

/** Where the player stands, without spending anything. */
export const getAccess = onCall(async (request) => {
  const uid = uidOf(request);
  const device = await deviceRef(request.data?.deviceId).get();
  return accessOf(await readUser(uid), (device.data()?.freeUsed as number | undefined) ?? 0, Date.now());
});

/** Called once as a camera game starts. The free-game counter lives here, so clearing the app's data does not reset it. */
export const startCameraGame = onCall(async (request) => {
  const uid = uidOf(request);
  const userRef = db.doc(`users/${uid}`);
  const devRef = deviceRef(request.data?.deviceId);
  return db.runTransaction(async (tx) => {
    const [userSnap, devSnap] = await Promise.all([tx.get(userRef), tx.get(devRef)]);
    const user = { ...emptyUser(), ...(userSnap.data() as Partial<UserState> | undefined) };
    const result = decideStart(user, (devSnap.data()?.freeUsed as number | undefined) ?? 0, Date.now());
    if (result.allowed && !result.access.premium) {
      tx.set(userRef, { freeUsed: result.user.freeUsed }, { merge: true });
      tx.set(devRef, { freeUsed: result.deviceUsed }, { merge: true });
    }
    return { allowed: result.allowed, ...result.access };
  });
});

/** Looks the subscription up at Google, stores the result for [uid] and acknowledges the purchase. */
async function refresh(uid: string, token: string): Promise<{ ok: boolean; reason?: string; until: number }> {
  const api = await publisher();
  const { data } = await api.purchases.subscriptionsv2.get({ packageName: packageName.value(), token });
  const sub = data as PlaySubscription & { lineItems?: Array<{ productId?: string | null }> };
  const verdict = judgeSubscription(sub, productId.value(), sha256(uid), Date.now());
  const userRef = db.doc(`users/${uid}`);
  if (!verdict.ok) {
    await userRef.set({ premiumUntil: 0 }, { merge: true });
    return { ok: false, reason: verdict.reason, until: 0 };
  }
  await userRef.set({ premiumUntil: verdict.until, purchaseToken: sha256(token), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  if ((data as { acknowledgementState?: string }).acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
    await api.purchases.subscriptions.acknowledge({ packageName: packageName.value(), subscriptionId: productId.value(), token, requestBody: {} });
  }
  return { ok: true, until: verdict.until };
}

/** The app hands over a purchase token right after buying (or when restoring purchases). */
export const verifyPurchase = onCall(async (request) => {
  const uid = uidOf(request);
  const token = request.data?.purchaseToken;
  if (typeof token !== "string" || token.length < 10 || token.length > 2000) throw new HttpsError("invalid-argument", "Missing purchase token.");
  const owner = db.doc(`purchases/${sha256(token)}`);
  const known = await owner.get();
  if (known.exists && known.data()?.uid !== uid) throw new HttpsError("permission-denied", "This purchase belongs to another account.");
  const result = await refresh(uid, token);
  if (result.ok) await owner.set({ uid });
  return result;
});

/** Real-time developer notifications from Google Play (renewals, cancellations, refunds) keep the stored end date current. */
export const playNotification = onMessagePublished("play-rtdn", async (event) => {
  const message = event.data.message.json as { subscriptionNotification?: { purchaseToken?: string } } | undefined;
  const token = message?.subscriptionNotification?.purchaseToken;
  if (!token) return;
  const owner = await db.doc(`purchases/${sha256(token)}`).get();
  const uid = owner.data()?.uid as string | undefined;
  if (uid) await refresh(uid, token);
});
