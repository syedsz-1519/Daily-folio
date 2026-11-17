const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any FolioNote collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("entries").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("entries").doc("e1").set({
      userId: ALICE_UID,
      title: "Sneaky Entry",
      content: "Should fail",
      mood: "CALM",
      tags: [],
      pinned: false,
      totalSocialMinutes: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("socialSnapshots").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("entitlement").doc("premium").get()
  );
});

test("Authenticated owner: valid JournalEntry create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const entriesCol = aliceDb.collection("users").doc(ALICE_UID).collection("entries");

  // Empty subcollection list must succeed
  await assertSucceeds(entriesCol.get());

  // Valid create payload (mirrors JournalRepository.createEntry without @DocumentId 'id')
  await assertSucceeds(
    entriesCol.doc("entry_1").set({
      userId: ALICE_UID,
      title: "Morning Pages",
      content: "Focused on deep work before opening Instagram.",
      mood: "FOCUSED",
      tags: ["morning", "clarity"],
      pinned: true,
      sharedUrl: "https://youtube.com/watch?v=dQw4w9WgXcQ",
      sharedPlatform: "YouTube",
      socialSnapshotSummary: "Instagram 18m · YouTube 12m",
      totalSocialMinutes: 30,
      createdAt: created,
      updatedAt: created,
    })
  );

  // List returns Alice's entries
  await assertSucceeds(entriesCol.get());

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    entriesCol.doc("entry_1").update({
      title: "Morning Pages (Revised)",
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete succeeds
  await assertSucceeds(entriesCol.doc("entry_1").delete());
});

test("JournalEntry rejects @DocumentId 'id' field, future timestamps, invalid mood, and ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const entriesCol = aliceDb.collection("users").doc(ALICE_UID).collection("entries");

  // Reject 'id' field inside body
  await assertFails(
    entriesCol.doc("entry_bad_id").set({
      id: "entry_bad_id",
      userId: ALICE_UID,
      title: "Bad ID",
      content: "Body",
      mood: "CALM",
      tags: [],
      pinned: false,
      totalSocialMinutes: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    entriesCol.doc("entry_future").set({
      userId: ALICE_UID,
      title: "Future Entry",
      content: "Body",
      mood: "CALM",
      tags: [],
      pinned: false,
      totalSocialMinutes: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Reject invalid mood enum
  await assertFails(
    entriesCol.doc("entry_bad_mood").set({
      userId: ALICE_UID,
      title: "Bad Mood",
      content: "Body",
      mood: "ANGRY",
      tags: [],
      pinned: false,
      totalSocialMinutes: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field (Shadow Update Test)
  await assertFails(
    entriesCol.doc("entry_ghost").set({
      userId: ALICE_UID,
      title: "Ghost",
      content: "Body",
      mood: "CALM",
      tags: [],
      pinned: false,
      totalSocialMinutes: 0,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt mutation on update
  await assertSucceeds(
    entriesCol.doc("entry_imm").set({
      userId: ALICE_UID,
      title: "Immutable Test",
      content: "Body",
      mood: "CALM",
      tags: [],
      pinned: false,
      totalSocialMinutes: 10,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    entriesCol.doc("entry_imm").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation blocks Bob from Alice's entries, snapshots, and entitlement", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("entries").doc("a1").set({
      userId: ALICE_UID,
      title: "Private Note",
      content: "Secret",
      mood: "REFLECTIVE",
      tags: [],
      pinned: false,
      totalSocialMinutes: 15,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(ALICE_UID).collection("entitlement").doc("premium").set({
      userId: ALICE_UID,
      adFree: true,
      planType: "LIFETIME",
      productId: "folionote_ad_free_lifetime",
      purchaseToken: "tok_abc",
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("entries").doc("a1").get()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("entries").get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("entries")
      .doc("a1")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("entitlement").doc("premium").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("socialSnapshots").doc("s1").set({
      userId: BOB_UID,
      sourceType: "USAGE_STATS",
      label: "2026-11-17",
      totalMinutes: 40,
      subscriberCount: 0,
      viewCount: 0,
      videoCount: 0,
      platformMinutes: { Instagram: 25, YouTube: 15 },
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("SocialSnapshot and UserEntitlement validation & lifecycle succeed for owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 4000);

  const snapRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("socialSnapshots")
    .doc("usage_2026_11_17");

  await assertSucceeds(
    snapRef.set({
      userId: ALICE_UID,
      sourceType: "USAGE_STATS",
      label: "2026-11-17",
      totalMinutes: 65,
      subscriberCount: 0,
      viewCount: 0,
      videoCount: 0,
      platformMinutes: { Instagram: 40, YouTube: 25 },
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    snapRef.update({
      totalMinutes: 72,
      platformMinutes: { Instagram: 42, YouTube: 30 },
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const entRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("entitlement")
    .doc("premium");

  await assertSucceeds(
    entRef.set({
      userId: ALICE_UID,
      adFree: false,
      planType: "FREE",
      productId: "",
      purchaseToken: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    entRef.update({
      adFree: true,
      planType: "MONTHLY",
      productId: "folionote_premium_monthly",
      purchaseToken: "play_token_xyz",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid planType
  await assertFails(
    entRef.update({
      planType: "HACKED_VIP",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
