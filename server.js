import express from "express";
import cors from "cors";
import bcrypt from "bcryptjs";
import admin from "firebase-admin";
import crypto from "crypto";

const app = express();

app.use(cors());
app.use(express.json());

/*
  FIREBASE ADMIN

  Render:
  Firebase service account JSON iwe kwenye environment variable:
  FIREBASE_SERVICE_ACCOUNT
*/

const serviceAccount = JSON.parse(
  process.env.FIREBASE_SERVICE_ACCOUNT
);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://makyama-e5e89-default-rtdb.firebaseio.com"
});

const db = admin.database();
const auth = admin.auth();

function trackingId() {
  return "DAG-" + Math.floor(100000 + Math.random() * 900000);
}

function pin() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function uid() {
  return "u_" + crypto.randomUUID();
}

/* =========================
   CREATE CARGO
========================= */

app.post("/api/create-cargo", async (req, res) => {
  try {
    const {
      cargoName,
      origin,
      destination,
      agents,
      bossName
    } = req.body;

    if (
      !cargoName ||
      !origin ||
      !destination ||
      !Array.isArray(agents) ||
      agents.length === 0 ||
      !bossName
    ) {
      return res.status(400).json({
        error: "Taarifa zote zinahitajika."
      });
    }

    const id = trackingId();

    const bossUid = uid();
    const bossPin = pin();

    const stages = {};

    for (let i = 0; i < agents.length; i++) {
      const stageNumber = i + 1;

      const agentUid = uid();
      const agentPin = pin();

      const hashedPin = await bcrypt.hash(agentPin, 12);

      stages[`stage_${stageNumber}`] = {
        order: stageNumber,
        uid: agentUid,

        public: {
          order: stageNumber,
          status:
            stageNumber === 1
              ? "READY_TO_RELEASE"
              : "WAITING"
        },

        private: {
          agentName: agents[i],
          pinHash: hashedPin
        }
      };
    }

    const bossHashedPin = await bcrypt.hash(bossPin, 12);

    const cargo = {
      public: {
        cargoName,
        origin,
        destination,
        status: "IN_TRANSPORT",
        createdAt: Date.now()
      },

      supervisor: {
        bossName,
        bossUid,
        bossPinHash: bossHashedPin
      },

      stages,

      history: {}
    };

    await db.ref(`cargo/${id}`).set(cargo);

    /*
      Return PINs ONLY once.
      Do not store plain PINs in database.
    */

    const credentials = [];

    for (let i = 0; i < agents.length; i++) {
      credentials.push({
        agentNumber: i + 1,
        agentName: agents[i],
        pin: await getOriginalPinNotPossible()
      });
    }

    /*
      Since hashes cannot be reversed,
      generate fresh credentials separately.
    */

    const freshStages = {};

    for (let i = 0; i < agents.length; i++) {
      const stageNumber = i + 1;
      const newPin = pin();

      freshStages[`stage_${stageNumber}`] = {
        uid: stages[`stage_${stageNumber}`].uid,
        pin: newPin
      };

      const newHash = await bcrypt.hash(newPin, 12);

      await db
        .ref(`cargo/${id}/stages/stage_${stageNumber}/private/pinHash`)
        .set(newHash);
    }

    return res.json({
      success: true,
      trackingId: id,

      boss: {
        name: bossName,
        pin: bossPin,
        uid: bossUid
      },

      agents: agents.map((name, i) => ({
        number: i + 1,
        name,
        uid: freshStages[`stage_${i + 1}`].uid,
        pin: freshStages[`stage_${i + 1}`].pin
      }))
    });

  } catch (error) {
    console.error(error);

    res.status(500).json({
      error: "Imeshindikana kutengeneza cargo."
    });
  }
});

/*
  Dummy function.
  Haifanyi chochote; imewekwa ili tusitumie plain PIN kutoka DB.
*/
async function getOriginalPinNotPossible() {
  return null;
}

/* =========================
   LOGIN
========================= */

app.post("/api/login", async (req, res) => {
  try {
    const {
      trackingId,
      pin: suppliedPin
    } = req.body;

    if (!trackingId || !suppliedPin) {
      return res.status(400).json({
        error: "Tracking ID na PIN vinahitajika."
      });
    }

    const snapshot = await db
      .ref(`cargo/${trackingId}`)
      .once("value");

    if (!snapshot.exists()) {
      return res.status(401).json({
        error: "Tracking ID au PIN sio sahihi."
      });
    }

    const cargo = snapshot.val();

    /* =========================
       CHECK BOSS
    ========================= */

    if (cargo.supervisor?.bossPinHash) {
      const bossCorrect = await bcrypt.compare(
        suppliedPin,
        cargo.supervisor.bossPinHash
      );

      if (bossCorrect) {
        const token = await auth.createCustomToken(
          cargo.supervisor.bossUid,
          {
            role: "boss",
            trackingId,
            supervisor: true
          }
        );

        return res.json({
          success: true,
          token,
          role: "boss",
          trackingId
        });
      }
    }

    /* =========================
       CHECK AGENTS
    ========================= */

    for (const [stageId, stage] of Object.entries(
      cargo.stages || {}
    )) {

      const hash = stage.private?.pinHash;

      if (!hash) continue;

      const correct = await bcrypt.compare(
        suppliedPin,
        hash
      );

      if (correct) {

        const stageNumber = stage.order;

        const supervisor =
          stageNumber === 1;

        const token = await auth.createCustomToken(
          stage.uid,
          {
            role: "agent",
            trackingId,
            stage: stageNumber,
            supervisor
          }
        );

        return res.json({
          success: true,
          token,
          role: "agent",
          stage: stageNumber,
          supervisor,
          trackingId
        });
      }
    }

    return res.status(401).json({
      error: "Tracking ID au PIN sio sahihi."
    });

  } catch (error) {
    console.error(error);

    res.status(500).json({
      error: "Login imeshindikana."
    });
  }
});

/* =========================
   AUTH MIDDLEWARE
========================= */

async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization || "";

    if (!header.startsWith("Bearer ")) {
      return res.status(401).json({
        error: "Hujaingia kwenye mfumo."
      });
    }

    const token = header.substring(7);

    const decoded = await auth.verifyIdToken(token);

    req.user = decoded;

    next();

  } catch {
    res.status(401).json({
      error: "Session sio halali."
    });
  }
}

/* =========================
   LOCATION
========================= */

async function getPlaceName(lat, lon) {

  try {

    const url =
      `https://nominatim.openstreetmap.org/reverse` +
      `?format=json&lat=${encodeURIComponent(lat)}` +
      `&lon=${encodeURIComponent(lon)}` +
      `&zoom=18&addressdetails=1`;

    const response = await fetch(url, {
      headers: {
        "User-Agent":
          "Makyama-Dagaa-Transport/1.0"
      }
    });

    const data = await response.json();

    return data.display_name ||
      `${lat}, ${lon}`;

  } catch {

    return `${lat}, ${lon}`;
  }
}

/* =========================
   AGENT ACTION
========================= */

app.post(
  "/api/agent-action",
  requireAuth,
  async (req, res) => {

    try {

      if (req.user.role !== "agent") {
        return res.status(403).json({
          error: "Huruhusiwi."
        });
      }

      const {
        trackingId,
        action,
        lat,
        lon
      } = req.body;

      if (
        req.user.trackingId !== trackingId
      ) {
        return res.status(403).json({
          error: "Cargo sio yako."
        });
      }

      if (
        typeof lat !== "number" ||
        typeof lon !== "number"
      ) {
        return res.status(400).json({
          error: "Location inahitajika."
        });
      }

      const stageNumber = Number(
        req.user.stage
      );

      const stageRef = db.ref(
        `cargo/${trackingId}/stages/stage_${stageNumber}`
      );

      const snap = await stageRef.once("value");

      if (!snap.exists()) {
        return res.status(404).json({
          error: "Stage haipo."
        });
      }

      const stage = snap.val();

      const locationName =
        await getPlaceName(lat, lon);

      const now = Date.now();

      /* =========================
         AGENT 1: ONLY NIMETOA
      ========================= */

      if (stageNumber === 1) {

        if (action !== "RELEASE") {
          return res.status(403).json({
            error:
              "Agent wa kwanza anaweza kufanya NIMETOA tu."
          });
        }

        if (
          stage.public.status ===
          "RELEASED"
        ) {
          return res.status(400).json({
            error: "Mizigo tayari imetolewa."
          });
        }

        await stageRef.update({
          public: {
            ...stage.public,
            status: "RELEASED",
            releasedAt: now,
            releasedLocationName:
              locationName,
            releasedLocation: {
              lat,
              lon
            }
          }
        });

        await db.ref(
          `cargo/${trackingId}/history`
        ).push({
          type: "NIMETOA",
          stage: stageNumber,
          locationName,
          location: { lat, lon },
          timestamp: now
        });

        return res.json({
          success: true,
          message: "NIMETOA imehifadhiwa."
        });
      }

      /* =========================
         AGENT 2+
      ========================= */

      if (action === "RECEIVE") {

        if (
          stage.public.status !==
          "READY_TO_RECEIVE"
        ) {
          return res.status(400).json({
            error:
              "Bado hujaruhusiwa kupokea mzigo."
          });
        }

        await stageRef.update({
          public: {
            ...stage.public,
            status: "RECEIVED",
            receivedAt: now,
            receivedLocationName:
              locationName,
            receivedLocation: {
              lat,
              lon
            }
          }
        });

        await db.ref(
          `cargo/${trackingId}/history`
        ).push({
          type: "NIMEPOKEA",
          stage: stageNumber,
          locationName,
          location: { lat, lon },
          timestamp: now
        });

        return res.json({
          success: true,
          message:
            "NIMEPOKEA imehifadhiwa."
        });
      }

      if (action === "RELEASE") {

        if (
          stage.public.status !==
          "RECEIVED"
        ) {
          return res.status(400).json({
            error:
              "Lazima kwanza ufanye NIMEPOKEA."
          });
        }

        await stageRef.update({
          public: {
            ...stage.public,
            status: "RELEASED",
            releasedAt: now,
            releasedLocationName:
              locationName,
            releasedLocation: {
              lat,
              lon
            }
          }
        });

        await db.ref(
          `cargo/${trackingId}/history`
        ).push({
          type: "NIMETOA",
          stage: stageNumber,
          locationName,
          location: { lat, lon },
          timestamp: now
        });

        /*
          Fungua stage inayofuata.
        */

        const nextStage =
          stageNumber + 1;

        const nextRef = db.ref(
          `cargo/${trackingId}/stages/stage_${nextStage}`
        );

        const nextSnap =
          await nextRef.once("value");

        if (nextSnap.exists()) {

          const next = nextSnap.val();

          await nextRef.child(
            "public/status"
          ).set("READY_TO_RECEIVE");

        } else {

          await db.ref(
            `cargo/${trackingId}/public/status`
          ).set("WAITING_FOR_BOSS");
        }

        return res.json({
          success: true,
          message:
            "NIMETOA imehifadhiwa."
        });
      }

      return res.status(400).json({
        error: "Action haijulikani."
      });

    } catch (error) {

      console.error(error);

      res.status(500).json({
        error:
          "Action haikuweza kuhifadhiwa."
      });
    }
  }
);

/* =========================
   BOSS RECEIVE
========================= */

app.post(
  "/api/boss-receive",
  requireAuth,
  async (req, res) => {

    try {

      if (
        req.user.role !== "boss" ||
        req.user.supervisor !== true
      ) {
        return res.status(403).json({
          error: "Boss pekee ndiye anaruhusiwa."
        });
      }

      const {
        trackingId,
        lat,
        lon
      } = req.body;

      if (
        req.user.trackingId !== trackingId
      ) {
        return res.status(403).json({
          error: "Cargo sio yako."
        });
      }

      const cargoRef =
        db.ref(`cargo/${trackingId}`);

      const snap =
        await cargoRef.once("value");

      if (!snap.exists()) {
        return res.status(404).json({
          error: "Cargo haipo."
        });
      }

      const cargo = snap.val();

      if (
        cargo.public.status !==
        "WAITING_FOR_BOSS"
      ) {
        return res.status(400).json({
          error:
            "Mizigo bado haijafika kwa Boss."
        });
      }

      const locationName =
        await getPlaceName(lat, lon);

      const now = Date.now();

      await cargoRef.update({
        "public/status": "COMPLETED",
        "public/updatedAt": now,

        "supervisor/bossReceivedAt": now,

        "supervisor/bossReceivedLocationName":
          locationName,

        "supervisor/bossReceivedLocation": {
          lat,
          lon
        }
      });

      await db.ref(
        `cargo/${trackingId}/history`
      ).push({
        type: "BOSS_NIMEPOKEA",
        locationName,
        location: { lat, lon },
        timestamp: now
      });

      res.json({
        success: true,
        message:
          "Boss amepokea mzigo."
      });

    } catch (error) {

      console.error(error);

      res.status(500).json({
        error:
          "Boss receive imeshindikana."
      });
    }
  }
);

/* =========================
   SERVER
========================= */

const PORT =
  process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log(
    `MAKYAMA Dagaa server running on ${PORT}`
  );
});
