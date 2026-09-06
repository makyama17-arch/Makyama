const express = require("express");
const cors = require("cors");
const bcrypt = require("bcryptjs");
const admin = require("firebase-admin");
const crypto = require("crypto");
const path = require("path");

const app = express();
const PORT = process.env.PORT || 10000;

app.use(cors());
app.use(express.json({ limit: "2mb" }));
app.use(express.static(__dirname));

/* =========================
   FIREBASE
========================= */

if (!process.env.FIREBASE_SERVICE_ACCOUNT) {
  console.error("❌ FIREBASE_SERVICE_ACCOUNT haijawekwa kwenye Render.");
  process.exit(1);
}

let serviceAccount;

try {
  serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} catch (err) {
  console.error("❌ FIREBASE_SERVICE_ACCOUNT si JSON sahihi.");
  process.exit(1);
}

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      "https://makyama-e5e89-default-rtdb.firebaseio.com"
  });
}

const db = admin.database();

/* =========================
   HELPERS
========================= */

function makeTrackingId() {
  return (
    "DAG-" +
    Math.floor(100000 + Math.random() * 900000)
  );
}

function makePin() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function makeUid() {
  return crypto.randomUUID();
}

function nowISO() {
  return new Date().toISOString();
}

function cleanText(value, max = 200) {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, max);
}

/*
  Reverse geocoding is optional.
  If Nominatim cannot be reached, coordinates
  will still be stored.
*/
async function reverseGeocode(lat, lng) {
  try {
    const url =
      "https://nominatim.openstreetmap.org/reverse" +
      `?lat=${encodeURIComponent(lat)}` +
      `&lon=${encodeURIComponent(lng)}` +
      "&format=json&zoom=18";

    const response = await fetch(url, {
      headers: {
        "User-Agent": "MAKYAMA-Dagaa-Transport/1.0"
      }
    });

    if (!response.ok) return "";

    const data = await response.json();

    return data.display_name || "";
  } catch (err) {
    return "";
  }
}

function validLocation(location) {
  if (!location) return false;

  const lat = Number(location.lat);
  const lng = Number(location.lng);

  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= -90 &&
    lat <= 90 &&
    lng >= -180 &&
    lng <= 180
  );
}

/* =========================
   AUTH MIDDLEWARE
========================= */

async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization || "";

    if (!header.startsWith("Bearer ")) {
      return res.status(401).json({
        ok: false,
        error: "Authorization token haipo."
      });
    }

    const token = header.substring(7);

    const decoded = await admin.auth().verifyIdToken(token);

    req.user = decoded;

    next();
  } catch (err) {
    console.error("AUTH ERROR:", err.message);

    return res.status(401).json({
      ok: false,
      error: "Session imekwisha au token si sahihi."
    });
  }
}

/* =========================
   ROOT
========================= */

app.get("/", (req, res) => {
  res.sendFile(path.join(__dirname, "index.html"));
});

/* =========================
   HEALTH CHECK
========================= */

app.get("/api/health", (req, res) => {
  res.json({
    ok: true,
    service: "MAKYAMA Dagaa Transport",
    time: nowISO()
  });
});

/* =========================
   CREATE CARGO
========================= */

app.post("/api/create-cargo", async (req, res) => {
  try {
    const cargoName = cleanText(req.body.cargoName);
    const origin = cleanText(req.body.origin);
    const destination = cleanText(req.body.destination);
    const bossName = cleanText(req.body.bossName);

    let agents = Array.isArray(req.body.agents)
      ? req.body.agents
      : [];

    agents = agents
      .map((agent) => ({
        name: cleanText(agent?.name)
      }))
      .filter((agent) => agent.name);

    if (!cargoName) {
      return res.status(400).json({
        ok: false,
        error: "Jina la mzigo linahitajika."
      });
    }

    if (!origin) {
      return res.status(400).json({
        ok: false,
        error: "Sehemu ya kuanzia inahitajika."
      });
    }

    if (!destination) {
      return res.status(400).json({
        ok: false,
        error: "Sehemu ya mwisho inahitajika."
      });
    }

    if (!bossName) {
      return res.status(400).json({
        ok: false,
        error: "Jina la Boss linahitajika."
      });
    }

    if (agents.length < 1) {
      return res.status(400).json({
        ok: false,
        error: "Weka angalau Agent mmoja."
      });
    }

    if (agents.length > 20) {
      return res.status(400).json({
        ok: false,
        error: "Agents ni wengi sana."
      });
    }

    let trackingId;

    for (let i = 0; i < 20; i++) {
      const candidate = makeTrackingId();

      const snapshot = await db
        .ref(`cargo/${candidate}`)
        .once("value");

      if (!snapshot.exists()) {
        trackingId = candidate;
        break;
      }
    }

    if (!trackingId) {
      return res.status(500).json({
        ok: false,
        error: "Imeshindikana kutengeneza Tracking ID."
      });
    }

    /* =========================
       BOSS PIN
    ========================= */

    const bossPin = makePin();
    const bossPinHash = await bcrypt.hash(bossPin, 12);

    const stages = {};

    /*
      Stage 1 = Agent 1
      Stage 2 = Agent 2
      etc.
    */

    const returnedAgents = [];

    for (let i = 0; i < agents.length; i++) {
      const stageNumber = i + 1;

      const pin = makePin();
      const pinHash = await bcrypt.hash(pin, 12);

      const uid = makeUid();

      stages[`stage_${stageNumber}`] = {
        stage: stageNumber,
        name: agents[i].name,
        uid,

        private: {
          pinHash
        },

        status:
          stageNumber === 1
            ? "WAITING_FOR_RELEASE"
            : "WAITING_FOR_RECEIVE",

        history: {}
      };

      returnedAgents.push({
        stage: stageNumber,
        name: agents[i].name,
        pin
      });
    }

    const cargo = {
      trackingId,

      cargoName,
      origin,
      destination,
      bossName,

      status: "WAITING_FOR_RELEASE",

      createdAt: nowISO(),

      boss: {
        uid: makeUid(),
        name: bossName,
        private: {
          pinHash: bossPinHash
        }
      },

      stages
    };

    await db.ref(`cargo/${trackingId}`).set(cargo);

    return res.json({
      ok: true,

      message: "Mzigo umefanikiwa kusajiliwa.",

      trackingId,

      boss: {
        name: bossName,
        pin: bossPin
      },

      agents: returnedAgents
    });
  } catch (err) {
    console.error("CREATE CARGO ERROR:", err);

    return res.status(500).json({
      ok: false,
      error: "Server error wakati wa kusajili mzigo."
    });
  }
});

/* =========================
   LOGIN
========================= */

app.post("/api/login", async (req, res) => {
  try {
    const trackingId = cleanText(req.body.trackingId, 50).toUpperCase();
    const pin = cleanText(req.body.pin, 20);

    if (!trackingId || !pin) {
      return res.status(400).json({
        ok: false,
        error: "Tracking ID na PIN vinahitajika."
      });
    }

    const snapshot = await db
      .ref(`cargo/${trackingId}`)
      .once("value");

    if (!snapshot.exists()) {
      return res.status(404).json({
        ok: false,
        error: "Mzigo haujapatikana."
      });
    }

    const cargo = snapshot.val();

    /* =========================
       CHECK BOSS
    ========================= */

    if (cargo.boss?.private?.pinHash) {
      const match = await bcrypt.compare(
        pin,
        cargo.boss.private.pinHash
      );

      if (match) {
        const uid = cargo.boss.uid;

        try {
          await admin.auth().deleteUser(uid);
        } catch (_) {}

        try {
          await admin.auth().createUser({
            uid
          });
        } catch (_) {}

        const token = await admin.auth().createCustomToken(
          uid,
          {
            role: "boss",
            trackingId,
            supervisor: true
          }
        );

        return res.json({
          ok: true,
          role: "boss",
          name: cargo.boss.name,
          trackingId,
          token
        });
      }
    }

    /* =========================
       CHECK AGENTS
    ========================= */

    const stages = cargo.stages || {};

    for (const key of Object.keys(stages)) {
      const stage = stages[key];

      if (!stage.private?.pinHash) continue;

      const match = await bcrypt.compare(
        pin,
        stage.private.pinHash
      );

      if (match) {
        const uid = stage.uid;

        try {
          await admin.auth().deleteUser(uid);
        } catch (_) {}

        try {
          await admin.auth().createUser({
            uid
          });
        } catch (_) {}

        const token = await admin.auth().createCustomToken(
          uid,
          {
            role: "agent",
            trackingId,
            stage: stage.stage,
            supervisor: stage.stage === 1
          }
        );

        return res.json({
          ok: true,
          role: "agent",
          stage: stage.stage,
          name: stage.name,
          trackingId,
          token
        });
      }
    }

    return res.status(401).json({
      ok: false,
      error: "PIN si sahihi."
    });
  } catch (err) {
    console.error("LOGIN ERROR:", err);

    return res.status(500).json({
      ok: false,
      error: "Server error wakati wa login."
    });
  }
});

/* =========================
   PUBLIC CARGO TRACKING
========================= */

app.get("/api/cargo/:trackingId", async (req, res) => {
  try {
    const trackingId = cleanText(
      req.params.trackingId,
      50
    ).toUpperCase();

    const snapshot = await db
      .ref(`cargo/${trackingId}`)
      .once("value");

    if (!snapshot.exists()) {
      return res.status(404).json({
        ok: false,
        error: "Mzigo haujapatikana."
      });
    }

    const cargo = snapshot.val();

    const publicStages = {};

    for (const key of Object.keys(cargo.stages || {})) {
      const stage = cargo.stages[key];

      publicStages[key] = {
        stage: stage.stage,
        name: stage.name,
        status: stage.status,
        history: stage.history || {}
      };
    }

    return res.json({
      ok: true,

      cargo: {
        trackingId: cargo.trackingId,
        cargoName: cargo.cargoName,
        origin: cargo.origin,
        destination: cargo.destination,
        bossName: cargo.bossName,
        status: cargo.status,
        createdAt: cargo.createdAt,
        stages: publicStages
      }
    });
  } catch (err) {
    console.error("TRACK ERROR:", err);

    return res.status(500).json({
      ok: false,
      error: "Imeshindikana kufuatilia mzigo."
    });
  }
});

/* =========================
   AGENT ACTION
========================= */

app.post("/api/agent-action", requireAuth, async (req, res) => {
  try {
    if (req.user.role !== "agent") {
      return res.status(403).json({
        ok: false,
        error: "Hii ni kwa Agent pekee."
      });
    }

    const trackingId = cleanText(
      req.user.trackingId,
      50
    ).toUpperCase();

    const stageNumber = Number(req.user.stage);

    const action = cleanText(req.body.action, 30).toUpperCase();

    const location = req.body.location || null;

    if (!["RELEASE", "RECEIVE"].includes(action)) {
      return res.status(400).json({
        ok: false,
        error: "Action si sahihi."
      });
    }

    const cargoRef = db.ref(`cargo/${trackingId}`);

    const snapshot = await cargoRef.once("value");

    if (!snapshot.exists()) {
      return res.status(404).json({
        ok: false,
        error: "Mzigo haujapatikana."
      });
    }

    const cargo = snapshot.val();

    const stageKey = `stage_${stageNumber}`;

    const stage = cargo.stages?.[stageKey];

    if (!stage) {
      return res.status(404).json({
        ok: false,
        error: "Agent stage haijapatikana."
      });
    }

    /*
      Agent 1:
      RELEASE only

      Agent 2+:
      RECEIVE then RELEASE
    */

    if (stageNumber === 1) {
      if (action !== "RELEASE") {
        return res.status(400).json({
          ok: false,
          error: "Agent 1 anatakiwa kuanza kwa NIMETOA."
        });
      }

      if (stage.status !== "WAITING_FOR_RELEASE") {
        return res.status(400).json({
          ok: false,
          error: "Agent 1 tayari ameshafanya action hii."
        });
      }
    } else {
      if (
        action === "RECEIVE" &&
        stage.status !== "WAITING_FOR_RECEIVE"
      ) {
        return res.status(400).json({
          ok: false,
          error: "Mzigo haujasubiri kupokelewa na agent huyu."
        });
      }

      if (
        action === "RELEASE" &&
        stage.status !== "WAITING_FOR_RELEASE_AFTER_RECEIVE"
      ) {
        return res.status(400).json({
          ok: false,
          error: "Agent lazima apokee mzigo kwanza."
        });
      }
    }

    let address = "";

    if (validLocation(location)) {
      address = await reverseGeocode(
        Number(location.lat),
        Number(location.lng)
      );
    }

    const eventId = makeUid();

    const event = {
      id: eventId,

      action,

      stage: stageNumber,

      agentName: stage.name,

      timestamp: nowISO(),

      location: validLocation(location)
        ? {
            lat: Number(location.lat),
            lng: Number(location.lng),
            address: address || ""
          }
        : null
    };

    await cargoRef
      .child(`stages/${stageKey}/history/${eventId}`)
      .set(event);

    /* =========================
       UPDATE STATUS
    ========================= */

    if (stageNumber === 1 && action === "RELEASE") {
      await cargoRef
        .child(`stages/${stageKey}/status`)
        .set("RELEASED");

      if (cargo.stages?.stage_2) {
        await cargoRef
          .child("stages/stage_2/status")
          .set("WAITING_FOR_RECEIVE");

        await cargoRef
          .child("status")
          .set("WAITING_FOR_RECEIVE");
      } else {
        await cargoRef
          .child("status")
          .set("WAITING_FOR_BOSS");
      }
    }

    if (stageNumber > 1 && action === "RECEIVE") {
      await cargoRef
        .child(`stages/${stageKey}/status`)
        .set("WAITING_FOR_RELEASE_AFTER_RECEIVE");

      await cargoRef
        .child("status")
        .set(`AGENT_${stageNumber}_RECEIVED`);
    }

    if (stageNumber > 1 && action === "RELEASE") {
      await cargoRef
        .child(`stages/${stageKey}/status`)
        .set("RELEASED");

      const nextStage =
        cargo.stages?.[`stage_${stageNumber + 1}`];

      if (nextStage) {
        await cargoRef
          .child(
            `stages/stage_${stageNumber + 1}/status`
          )
          .set("WAITING_FOR_RECEIVE");

        await cargoRef
          .child("status")
          .set(
            `WAITING_FOR_AGENT_${stageNumber + 1}`
          );
      } else {
        await cargoRef
          .child("status")
          .set("WAITING_FOR_BOSS");
      }
    }

    return res.json({
      ok: true,
      message:
        action === "RECEIVE"
          ? "Mzigo umepokelewa."
          : "Mzigo umetolewa.",

      event
    });
  } catch (err) {
    console.error("AGENT ACTION ERROR:", err);

    return res.status(500).json({
      ok: false,
      error: "Imeshindikana kuhifadhi action."
    });
  }
});

/* =========================
   BOSS RECEIVE
========================= */

app.post("/api/boss-receive", requireAuth, async (req, res) => {
  try {
    if (req.user.role !== "boss") {
      return res.status(403).json({
        ok: false,
        error: "Boss pekee ndiye anaweza kupokea mzigo wa mwisho."
      });
    }

    const trackingId = cleanText(
      req.user.trackingId,
      50
    ).toUpperCase();

    const location = req.body.location || null;

    const cargoRef = db.ref(`cargo/${trackingId}`);

    const snapshot = await cargoRef.once("value");

    if (!snapshot.exists()) {
      return res.status(404).json({
        ok: false,
        error: "Mzigo haujapatikana."
      });
    }

    const cargo = snapshot.val();

    if (cargo.status !== "WAITING_FOR_BOSS") {
      return res.status(400).json({
        ok: false,
        error: "Mzigo bado haujafika hatua ya kupokelewa na Boss."
      });
    }

    let address = "";

    if (validLocation(location)) {
      address = await reverseGeocode(
        Number(location.lat),
        Number(location.lng)
      );
    }

    const eventId = makeUid();

    const event = {
      id: eventId,

      action: "BOSS_RECEIVE",

      stage: "BOSS",

      agentName: cargo.bossName,

      timestamp: nowISO(),

      location: validLocation(location)
        ? {
            lat: Number(location.lat),
            lng: Number(location.lng),
            address: address || ""
          }
        : null
    };

    await cargoRef
      .child(`boss/history/${eventId}`)
      .set(event);

    await cargoRef
      .child("status")
      .set("DELIVERED");

    return res.json({
      ok: true,
      message: "Boss amepokea mzigo.",
      event
    });
  } catch (err) {
    console.error("BOSS RECEIVE ERROR:", err);

    return res.status(500).json({
      ok: false,
      error: "Imeshindikana kuhifadhi mapokezi ya Boss."
    });
  }
});

/* =========================
   RESET AGENT PIN
   Boss OR Agent 1
========================= */

app.post(
  "/api/reset-agent-pin",
  requireAuth,
  async (req, res) => {
    try {
      const trackingId = cleanText(
        req.user.trackingId,
        50
      ).toUpperCase();

      const targetStage = Number(req.body.stage);

      if (!Number.isInteger(targetStage) || targetStage < 1) {
        return res.status(400).json({
          ok: false,
          error: "Agent stage si sahihi."
        });
      }

      /*
        Only:
        Boss
        Agent 1
      */

      const allowed =
        req.user.role === "boss" ||
        (
          req.user.role === "agent" &&
          Number(req.user.stage) === 1
        );

      if (!allowed) {
        return res.status(403).json({
          ok: false,
          error:
            "Ni Boss au Agent 1 pekee anaweza kutoa/reset PIN."
        });
      }

      /*
        Prevent Agent 1 from resetting
        his own PIN through this endpoint.
      */

      if (
        req.user.role === "agent" &&
        Number(req.user.stage) === targetStage
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Agent 1 hawezi kujipa PIN mpya kupitia mfumo huu."
        });
      }

      const cargoRef = db.ref(`cargo/${trackingId}`);

      const snapshot = await cargoRef.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error: "Mzigo haujapatikana."
        });
      }

      const cargo = snapshot.val();

      const stageKey = `stage_${targetStage}`;

      const stage = cargo.stages?.[stageKey];

      if (!stage) {
        return res.status(404).json({
          ok: false,
          error: "Agent huyo hajapatikana."
        });
      }

      const newPin = makePin();

      const newPinHash = await bcrypt.hash(
        newPin,
        12
      );

      await cargoRef
        .child(
          `stages/${stageKey}/private/pinHash`
        )
        .set(newPinHash);

      /*
        Invalidate old Firebase account
        so old login session cannot continue.
      */

      if (stage.uid) {
        try {
          await admin.auth().deleteUser(stage.uid);
        } catch (_) {}
      }

      const newUid = makeUid();

      try {
        await admin.auth().createUser({
          uid: newUid
        });
      } catch (_) {}

      await cargoRef
        .child(`stages/${stageKey}/uid`)
        .set(newUid);

      return res.json({
        ok: true,

        message:
          `PIN mpya ya ${stage.name} imetengenezwa.`,

        stage: targetStage,

        agentName: stage.name,

        pin: newPin
      });
    } catch (err) {
      console.error("RESET AGENT PIN ERROR:", err);

      return res.status(500).json({
        ok: false,
        error: "Imeshindikana kutengeneza PIN mpya."
      });
    }
  }
);

/* =========================
   START SERVER
========================= */

app.listen(PORT, "0.0.0.0", () => {
  console.log(
    `MAKYAMA Dagaa server running on ${PORT}`
  );
});
