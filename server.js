// server.js
require("dotenv").config();

const express = require("express");
const cors = require("cors");
const bcrypt = require("bcryptjs");
const crypto = require("crypto");
const admin = require("firebase-admin");

const app = express();

app.use(cors({
  origin: true,
  methods: ["GET","POST","PATCH","DELETE","OPTIONS"],
  allowedHeaders: ["Content-Type","Authorization"]
}));

app.use(express.json({limit:"1mb"}));

const PORT = process.env.PORT || 3000;
const DATABASE_URL =
  process.env.FIREBASE_DATABASE_URL ||
  "https://makyama-e5e89-default-rtdb.firebaseio.com";

let serviceAccount;

try {
  serviceAccount = JSON.parse(
    process.env.FIREBASE_SERVICE_ACCOUNT
  );
} catch (error) {
  console.error("FIREBASE_SERVICE_ACCOUNT si JSON sahihi.");
  process.exit(1);
}

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: DATABASE_URL
  });
}

const db = admin.database();

function nowISO() {
  return new Date().toISOString();
}

function cleanText(value, max = 500) {
  return String(value ?? "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, max);
}

function normalizePhone(phone) {
  return cleanText(phone, 30).replace(/[^\d+]/g, "");
}

function makeTrackingId() {
  return "MKT-" + Math.floor(100000 + Math.random() * 900000);
}

function makePin() {
  return String(
    Math.floor(100000 + Math.random() * 900000)
  );
}

function makeUid(prefix = "user") {
  return (
    prefix +
    "_" +
    crypto.randomBytes(12).toString("hex")
  );
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

async function reverseGeocode(location) {
  if (!validLocation(location)) return null;

  try {
    const lat = Number(location.lat);
    const lng = Number(location.lng);

    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}`,
      {
        headers: {
          "User-Agent": "MAKYAMA-TRANSPORT/1.0"
        }
      }
    );

    if (!response.ok) return null;

    const data = await response.json();

    return {
      lat,
      lng,
      address:
        data.display_name ||
        `${lat}, ${lng}`
    };
  } catch (error) {
    console.error(
      "REVERSE GEOCODE ERROR:",
      error.message
    );

    return {
      lat: Number(location.lat),
      lng: Number(location.lng),
      address: `${location.lat}, ${location.lng}`
    };
  }
}

async function requireAuth(req, res, next) {
  try {
    const header =
      req.headers.authorization || "";

    if (!header.startsWith("Bearer ")) {
      return res.status(401).json({
        ok: false,
        error: "Authorization token haipo."
      });
    }

    const token = header.substring(7);

    const decoded =
      await admin.auth().verifyIdToken(
        token,
        true
      );

    req.user = decoded;
    next();
  } catch (error) {
    console.error(
      "AUTH ERROR:",
      error.message
    );

    return res.status(401).json({
      ok: false,
      error:
        "Session imekwisha au token si sahihi."
    });
  }
}

async function requireAdmin(req, res, next) {
  await requireAuth(req, res, () => {
    if (
      req.user.role !== "admin" ||
      req.user.admin !== true
    ) {
      return res.status(403).json({
        ok: false,
        error: "Admin access required."
      });
    }

    next();
  });
}

async function getCargo(trackingId) {
  const snapshot = await db
    .ref(`cargo/${trackingId}`)
    .once("value");

  if (!snapshot.exists()) {
    return null;
  }

  return snapshot.val();
}

function removePrivateData(cargo) {
  const copy = JSON.parse(
    JSON.stringify(cargo)
  );

  delete copy.private;

  if (copy.boss) {
    delete copy.boss.private;
  }

  if (copy.stages) {
    const stages = Array.isArray(copy.stages)
      ? copy.stages
      : Object.values(copy.stages);

    stages.forEach(stage => {
      if (stage.private) {
        delete stage.private;
      }
    });
  }

  return copy;
}

function makePublicCargo(cargo) {
  return {
    trackingId: cargo.trackingId,
    cargoName: cargo.cargoName,
    origin: cargo.origin,
    destination: cargo.destination,
    status: cargo.status,
    createdAt: cargo.createdAt
  };
}

function makeAgentCargo(cargo, stageNumber) {
  const result = {
    trackingId: cargo.trackingId,
    cargoName: cargo.cargoName,
    origin: cargo.origin,
    destination: cargo.destination,
    status: cargo.status,
    createdAt: cargo.createdAt,
    stages: []
  };

  const stages = Array.isArray(cargo.stages)
    ? cargo.stages
    : Object.values(cargo.stages || {});

  const ownStage = stages.find(
    stage =>
      Number(stage.stage) ===
      Number(stageNumber)
  );

  if (ownStage) {
    const copy = JSON.parse(
      JSON.stringify(ownStage)
    );

    delete copy.private;

    result.stages = [copy];
  }

  if (Number(stageNumber) === 1) {
    result.stages = stages.map(stage => {
      const copy = JSON.parse(
        JSON.stringify(stage)
      );

      delete copy.private;
      return copy;
    });
  }

  return result;
}

function makeAdminCargo(cargo) {
  return removePrivateData(cargo);
}

async function createFirebaseUser(uid, displayName) {
  try {
    return await admin.auth().getUser(uid);
  } catch (error) {
    return await admin.auth().createUser({
      uid,
      displayName: displayName || undefined
    });
  }
}

async function deleteFirebaseUser(uid) {
  if (!uid) return;

  try {
    await admin.auth().deleteUser(uid);
  } catch (error) {
    if (
      error.code !==
      "auth/user-not-found"
    ) {
      console.error(
        "DELETE FIREBASE USER:",
        error.message
      );
    }
  }
}

async function sendNotificationToRecipient(
  trackingId,
  recipientKey,
  title,
  body
) {
  try {
    const snapshot = await db
      .ref(
        `deviceTokens/${trackingId}/${recipientKey}`
      )
      .once("value");

    if (!snapshot.exists()) return;

    const tokens = [];
    const tokenIds = [];

    snapshot.forEach(child => {
      const value = child.val();

      if (value?.token) {
        tokens.push(value.token);
        tokenIds.push(child.key);
      }
    });

    if (!tokens.length) return;

    const response =
      await admin.messaging().sendEachForMulticast({
        tokens,
        notification: {
          title,
          body
        },
        data: {
          trackingId
        }
      });

    const invalidCodes = new Set([
      "messaging/registration-token-not-registered",
      "messaging/invalid-registration-token"
    ]);

    const removals = [];

    response.responses.forEach(
      (item, index) => {
        if (
          !item.success &&
          invalidCodes.has(
            item.error?.code
          )
        ) {
          removals.push(
            db
              .ref(
                `deviceTokens/${trackingId}/${recipientKey}/${tokenIds[index]}`
              )
              .remove()
          );
        }
      }
    );

    await Promise.all(removals);
  } catch (error) {
    console.error(
      "NOTIFICATION ERROR:",
      error.message
    );
  }
}

async function notifyCargoAgents(
  cargo,
  title,
  body
) {
  const jobs = [];

  const stages = Array.isArray(cargo.stages)
    ? cargo.stages
    : Object.values(cargo.stages || {});

  stages.forEach(stage => {
    jobs.push(
      sendNotificationToRecipient(
        cargo.trackingId,
        `stage_${stage.stage}`,
        title,
        body
      )
    );
  });

  await Promise.all(jobs);
}

app.get("/", (req, res) => {
  res.json({
    ok: true,
    service: "MAKYAMA TRANSPORT",
    status: "online"
  });
});

app.get("/health", (req, res) => {
  res.json({
    ok: true,
    service: "MAKYAMA TRANSPORT",
    time: nowISO()
  });
});

/* =========================================================
   CREATE CARGO
========================================================= */

app.post(
  "/api/create-cargo",
  async (req, res) => {
    try {
      const cargoName =
        cleanText(req.body.cargoName, 200);

      const origin =
        cleanText(req.body.origin, 200);

      const destination =
        cleanText(req.body.destination, 200);

      const bossName =
        cleanText(req.body.bossName, 150);

      let agents =
        Array.isArray(req.body.agents)
          ? req.body.agents
          : [];

      if (
        !cargoName ||
        !origin ||
        !destination ||
        !bossName
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Cargo name, origin, destination na Boss name vinahitajika."
        });
      }

      if (
        agents.length < 1 ||
        agents.length > 20
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Agents lazima wawe kati ya 1 na 20."
        });
      }

      agents = agents.map(
        (agent, index) => ({
          name:
            cleanText(
              agent?.name,
              150
            ) ||
            `Agent ${index + 1}`
        })
      );

      let trackingId;

      do {
        trackingId =
          makeTrackingId();
      } while (
        await db
          .ref(`cargo/${trackingId}`)
          .once("value")
          .then(snapshot =>
            snapshot.exists()
          )
      );

      const bossUid =
        makeUid("boss");

      const bossPin =
        makePin();

      const bossPinHash =
        await bcrypt.hash(
          bossPin,
          12
        );

      const stages = [];

      const returnedAgents = [];

      for (
        let i = 0;
        i < agents.length;
        i++
      ) {
        const stageNumber = i + 1;
        const uid =
          makeUid(
            `agent${stageNumber}`
          );

        const pin =
          makePin();

        const pinHash =
          await bcrypt.hash(
            pin,
            12
          );

        stages.push({
          stage: stageNumber,
          name: agents[i].name,
          uid,
          status:
            stageNumber === 1
              ? "WAITING_FOR_RELEASE"
              : "WAITING_FOR_RECEIVE",
          history: [],
          private: {
            pinHash
          }
        });

        returnedAgents.push({
          stage: stageNumber,
          name: agents[i].name,
          pin
        });

        await createFirebaseUser(
          uid,
          agents[i].name
        );
      }

      await createFirebaseUser(
        bossUid,
        bossName
      );

      const cargo = {
        trackingId,
        cargoName,
        origin,
        destination,
        bossName,
        status:
          "WAITING_FOR_RELEASE",
        createdAt: nowISO(),
        stages,
        boss: {
          uid: bossUid,
          name: bossName,
          history: [],
          private: {
            pinHash: bossPinHash
          }
        }
      };

      await db
        .ref(`cargo/${trackingId}`)
        .set(cargo);

      return res.json({
        ok: true,
        trackingId,
        boss: {
          name: bossName,
          pin: bossPin
        },
        agents: returnedAgents
      });
    } catch (error) {
      console.error(
        "CREATE CARGO ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Imeshindikana kusajili mzigo."
      });
    }
  }
);

/* =========================================================
   CARGO LOGIN
========================================================= */

app.post(
  "/api/login",
  async (req, res) => {
    try {
      const trackingId =
        cleanText(
          req.body.trackingId,
          50
        ).toUpperCase();

      const pin =
        cleanText(
          req.body.pin,
          50
        );

      if (!trackingId || !pin) {
        return res.status(400).json({
          ok: false,
          error:
            "Tracking ID na PIN vinahitajika."
        });
      }

      const cargo =
        await getCargo(trackingId);

      if (!cargo) {
        return res.status(404).json({
          ok: false,
          error:
            "Tracking ID haijapatikana."
        });
      }

      if (
        cargo.boss?.private?.pinHash &&
        await bcrypt.compare(
          pin,
          cargo.boss.private.pinHash
        )
      ) {
        const uid =
          cargo.boss.uid;

        await createFirebaseUser(
          uid,
          cargo.bossName
        );

        const token =
          await admin.auth()
            .createCustomToken(
              uid,
              {
                role: "boss",
                trackingId,
                supervisor: true
              }
            );

        return res.json({
          ok: true,
          token,
          role: "boss",
          name: cargo.bossName,
          trackingId
        });
      }

      const stages =
        Array.isArray(cargo.stages)
          ? cargo.stages
          : Object.values(
              cargo.stages || {}
            );

      for (const stage of stages) {
        if (
          stage.private?.pinHash &&
          await bcrypt.compare(
            pin,
            stage.private.pinHash
          )
        ) {
          const uid =
            stage.uid;

          await createFirebaseUser(
            uid,
            stage.name
          );

          const token =
            await admin.auth()
              .createCustomToken(
                uid,
                {
                  role: "agent",
                  trackingId,
                  stage: Number(stage.stage),
                  supervisor:
                    Number(stage.stage) === 1
                }
              );

          return res.json({
            ok: true,
            token,
            role: "agent",
            stage: Number(stage.stage),
            name: stage.name,
            trackingId
          });
        }
      }

      return res.status(401).json({
        ok: false,
        error:
          "Tracking ID au PIN si sahihi."
      });
    } catch (error) {
      console.error(
        "LOGIN ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Login imeshindikana."
      });
    }
  }
);

/* =========================================================
   PUBLIC / PRIVATE CARGO
========================================================= */

app.get(
  "/api/cargo/:trackingId",
  async (req, res) => {
    try {
      const trackingId =
        cleanText(
          req.params.trackingId,
          50
        ).toUpperCase();

      const cargo =
        await getCargo(trackingId);

      if (!cargo) {
        return res.status(404).json({
          ok: false,
          error:
            "Mzigo haujapatikana."
        });
      }

      const header =
        req.headers.authorization || "";

      if (!header.startsWith("Bearer ")) {
        return res.json({
          ok: true,
          cargo:
            makePublicCargo(cargo)
        });
      }

      const token =
        header.substring(7);

      let decoded;

      try {
        decoded =
          await admin.auth()
            .verifyIdToken(
              token,
              true
            );
      } catch {
        return res.json({
          ok: true,
          cargo:
            makePublicCargo(cargo)
        });
      }

      if (
        decoded.trackingId !==
        trackingId
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Huna ruhusa ya mzigo huu."
        });
      }

      if (
        decoded.role === "boss"
      ) {
        if (
          decoded.uid !==
          cargo.boss?.uid
        ) {
          return res.status(403).json({
            ok: false,
            error:
              "Boss account si ya mzigo huu."
          });
        }

        return res.json({
          ok: true,
          cargo:
            removePrivateData(
              cargo
            )
        });
      }

      if (
        decoded.role === "agent"
      ) {
        const stage =
          Number(decoded.stage);

        const stages =
          Array.isArray(cargo.stages)
            ? cargo.stages
            : Object.values(
                cargo.stages || {}
              );

        const target =
          stages.find(
            item =>
              Number(item.stage) ===
              stage
          );

        if (
          !target ||
          target.uid !==
            decoded.uid
        ) {
          return res.status(403).json({
            ok: false,
            error:
              "Agent account si ya hatua hii."
          });
        }

        return res.json({
          ok: true,
          cargo:
            makeAgentCargo(
              cargo,
              stage
            )
        });
      }

      if (
        decoded.role === "platform_agent" ||
        decoded.role === "transporter"
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Platform account haina access ya cargo dashboard."
        });
      }

      if (decoded.role === "admin") {
        return res.json({
          ok: true,
          cargo:
            makeAdminCargo(cargo)
        });
      }

      return res.status(403).json({
        ok: false,
        error: "Role haijulikani."
      });
    } catch (error) {
      console.error(
        "GET CARGO ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Imeshindikana kupata mzigo."
      });
    }
  }
);

/* =========================================================
   AGENT ACTION
========================================================= */

app.post(
  "/api/agent-action",
  requireAuth,
  async (req, res) => {
    try {
      if (
        req.user.role !== "agent"
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Agent access required."
        });
      }

      const trackingId =
        cleanText(
          req.user.trackingId,
          50
        ).toUpperCase();

      const action =
        cleanText(
          req.body.action,
          30
        ).toUpperCase();

      const stageNumber =
        Number(req.user.stage);

      if (
        !["RECEIVE","RELEASE"]
          .includes(action)
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Action si sahihi."
        });
      }

      const cargoRef =
        db.ref(
          `cargo/${trackingId}`
        );

      const snapshot =
        await cargoRef.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Mzigo haujapatikana."
        });
      }

      const cargo =
        snapshot.val();

      const stages =
        Array.isArray(cargo.stages)
          ? cargo.stages
          : Object.values(
              cargo.stages || {}
            );

      const index =
        stages.findIndex(
          stage =>
            Number(stage.stage) ===
            stageNumber
        );

      if (index < 0) {
        return res.status(404).json({
          ok: false,
          error:
            "Agent hakupatikana."
        });
      }

      const stage =
        stages[index];

      if (
        stage.uid !==
        req.user.uid
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Agent account si sahihi."
        });
      }

      if (stageNumber === 1) {
        if (
          action !== "RELEASE" ||
          stage.status !==
            "WAITING_FOR_RELEASE"
        ) {
          return res.status(400).json({
            ok: false,
            error:
              "Agent 1 anaweza RELEASE tu katika hatua yake."
          });
        }
      } else {
        if (
          action === "RECEIVE" &&
          stage.status !==
            "WAITING_FOR_RECEIVE"
        ) {
          return res.status(400).json({
            ok: false,
            error:
              "Mzigo haujasubiri kupokelewa na Agent huyu."
          });
        }

        if (
          action === "RELEASE" &&
          stage.status !==
            "WAITING_FOR_RELEASE_AFTER_RECEIVE"
        ) {
          return res.status(400).json({
            ok: false,
            error:
              "Agent lazima RECEIVE kwanza."
          });
        }
      }

      let location =
        await reverseGeocode(
          req.body.location
        );

      const event = {
        action,
        stage: stageNumber,
        agentName:
          stage.name,
        timestamp: nowISO()
      };

      if (location) {
        event.location =
          location;
      }

      if (!Array.isArray(stage.history)) {
        stage.history = [];
      }

      stage.history.push(event);

      let nextStage =
        stages.find(
          item =>
            Number(item.stage) ===
            stageNumber + 1
        );

      if (
        action === "RECEIVE"
      ) {
        stage.status =
          "WAITING_FOR_RELEASE_AFTER_RECEIVE";

        cargo.status =
          `AGENT_${stageNumber}_RECEIVED`;
      }

      if (
        action === "RELEASE"
      ) {
        stage.status =
          "RELEASED";

        if (nextStage) {
          nextStage.status =
            "WAITING_FOR_RECEIVE";

          cargo.status =
            `WAITING_FOR_AGENT_${stageNumber + 1}`;

          await sendNotificationToRecipient(
            trackingId,
            `stage_${stageNumber + 1}`,
            "📦 Mzigo umewasili kwa hatua inayofuata",
            `Mzigo ${trackingId} unasubiri kupokelewa na Agent ${stageNumber + 1}.`
          );
        } else {
          cargo.status =
            "WAITING_FOR_BOSS";

          await Promise.all([
            sendNotificationToRecipient(
              trackingId,
              "stage_1",
              "🚚 Mzigo umetolewa",
              `Mzigo ${trackingId} umetolewa na Agent wa mwisho.`
            ),
            sendNotificationToRecipient(
              trackingId,
              "boss",
              "🚚 Mzigo umetolewa",
              `Mzigo ${trackingId} umetolewa na Agent wa mwisho.`
            )
          ]);
        }
      }

      cargo.stages = stages;

      await cargoRef.set(cargo);

      return res.json({
        ok: true,
        action,
        status:
          cargo.status,
        location
      });
    } catch (error) {
      console.error(
        "AGENT ACTION ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Agent action imeshindikana."
      });
    }
  }
);

/* =========================================================
   BOSS RECEIVE
========================================================= */

app.post(
  "/api/boss-receive",
  requireAuth,
  async (req, res) => {
    try {
      if (
        req.user.role !== "boss"
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Boss access required."
        });
      }

      const trackingId =
        cleanText(
          req.user.trackingId,
          50
        ).toUpperCase();

      const cargoRef =
        db.ref(
          `cargo/${trackingId}`
        );

      const snapshot =
        await cargoRef.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Mzigo haujapatikana."
        });
      }

      const cargo =
        snapshot.val();

      if (
        cargo.boss?.uid !==
        req.user.uid
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Boss account si ya mzigo huu."
        });
      }

      if (
        cargo.status !==
        "WAITING_FOR_BOSS"
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Mzigo haujasubiri kupokelewa na Boss."
        });
      }

      const location =
        await reverseGeocode(
          req.body.location
        );

      const event = {
        action: "BOSS_RECEIVE",
        stage: "BOSS",
        agentName:
          cargo.bossName ||
          "Boss",
        timestamp: nowISO()
      };

      if (location) {
        event.location =
          location;
      }

      if (
        !Array.isArray(
          cargo.boss.history
        )
      ) {
        cargo.boss.history = [];
      }

      cargo.boss.history.push(
        event
      );

      cargo.status =
        "DELIVERED";

      await cargoRef.set(cargo);

      await notifyCargoAgents(
        cargo,
        "✅ Mzigo umepokelewa",
        `Mzigo ${trackingId} umepokelewa na Boss.`
      );

      return res.json({
        ok: true,
        status: "DELIVERED",
        location
      });
    } catch (error) {
      console.error(
        "BOSS RECEIVE ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Boss receive imeshindikana."
      });
    }
  }
);

/* =========================================================
   RESET AGENT PIN
========================================================= */

app.post(
  "/api/reset-agent-pin",
  requireAuth,
  async (req, res) => {
    try {
      const trackingId =
        cleanText(
          req.user.trackingId,
          50
        ).toUpperCase();

      const stageNumber =
        Number(req.body.stage);

      if (
        !Number.isInteger(
          stageNumber
        ) ||
        stageNumber < 2
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Stage lazima iwe 2 au zaidi."
        });
      }

      if (
        req.user.role !== "boss" &&
        !(
          req.user.role === "agent" &&
          Number(req.user.stage) === 1
        )
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Huna ruhusa ya kubadilisha PIN."
        });
      }

      const cargoRef =
        db.ref(
          `cargo/${trackingId}`
        );

      const snapshot =
        await cargoRef.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Mzigo haujapatikana."
        });
      }

      const cargo =
        snapshot.val();

      if (
        req.user.role === "boss" &&
        cargo.boss?.uid !==
          req.user.uid
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Boss account si sahihi."
        });
      }

      if (
        req.user.role === "agent" &&
        req.user.stage !== 1
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Agent 1 pekee anaweza reset PIN."
        });
      }

      const stages =
        Array.isArray(cargo.stages)
          ? cargo.stages
          : Object.values(
              cargo.stages || {}
            );

      const index =
        stages.findIndex(
          stage =>
            Number(stage.stage) ===
            stageNumber
        );

      if (index < 0) {
        return res.status(404).json({
          ok: false,
          error:
            "Agent huyo hakupatikana."
        });
      }

      const stage =
        stages[index];

      const newPin =
        makePin();

      const pinHash =
        await bcrypt.hash(
          newPin,
          12
        );

      const oldUid =
        stage.uid;

      const newUid =
        makeUid(
          `agent${stageNumber}`
        );

      stage.uid = newUid;

      stage.private = {
        pinHash
      };

      stages[index] =
        stage;

      cargo.stages =
        stages;

      await deleteFirebaseUser(
        oldUid
      );

      await createFirebaseUser(
        newUid,
        stage.name
      );

      /*
        DEVICE TOKENS ziko nje ya cargo.
        Hii ndiyo path sahihi.
      */
      await db
        .ref(
          `deviceTokens/${trackingId}/stage_${stageNumber}`
        )
        .remove();

      await cargoRef.set(cargo);

      return res.json({
        ok: true,
        stage: stageNumber,
        pin: newPin
      });
    } catch (error) {
      console.error(
        "RESET PIN ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "PIN reset imeshindikana."
      });
    }
  }
);

/* =========================================================
   DEVICE REGISTRATION
========================================================= */

app.post(
  "/api/register-device",
  requireAuth,
  async (req, res) => {
    try {
      const token =
        cleanText(
          req.body.token,
          5000
        );

      if (!token) {
        return res.status(400).json({
          ok: false,
          error:
            "FCM token haipo."
        });
      }

      const trackingId =
        req.user.trackingId;

      if (!trackingId) {
        return res.status(403).json({
          ok: false,
          error:
            "Account hii haina cargo tracking."
        });
      }

      const cargo =
        await getCargo(
          trackingId
        );

      if (!cargo) {
        return res.status(404).json({
          ok: false,
          error:
            "Cargo haipo."
        });
      }

      let recipientKey;

      if (
        req.user.role === "boss"
      ) {
        if (
          cargo.boss?.uid !==
          req.user.uid
        ) {
          return res.status(403).json({
            ok: false,
            error:
              "Boss account si sahihi."
          });
        }

        recipientKey = "boss";
      } else if (
        req.user.role === "agent"
      ) {
        const stage =
          Number(req.user.stage);

        const stages =
          Array.isArray(cargo.stages)
            ? cargo.stages
            : Object.values(
                cargo.stages || {}
              );

        const stageData =
          stages.find(
            item =>
              Number(item.stage) ===
              stage
          );

        if (
          !stageData ||
          stageData.uid !==
            req.user.uid
        ) {
          return res.status(403).json({
            ok: false,
            error:
              "Agent account si sahihi."
          });
        }

        recipientKey =
          `stage_${stage}`;
      } else {
        return res.status(403).json({
          ok: false,
          error:
            "Account hii haiwezi kusajili device."
        });
      }

      const tokenId =
        crypto
          .createHash("sha256")
          .update(token)
          .digest("hex");

      await db
        .ref(
          `deviceTokens/${trackingId}/${recipientKey}/${tokenId}`
        )
        .set({
          token,
          uid: req.user.uid,
          updatedAt: nowISO()
        });

      return res.json({
        ok: true
      });
    } catch (error) {
      console.error(
        "REGISTER DEVICE ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Device registration imeshindikana."
      });
    }
  }
);

/* =========================================================
   PLATFORM REGISTRATION HELPERS
========================================================= */

async function phoneExists(
  phone,
  exceptUid = null
) {
  const normalized =
    normalizePhone(phone);

  const [agentsSnap, transportersSnap] =
    await Promise.all([
      db.ref("platformAgents")
        .once("value"),
      db.ref("transporters")
        .once("value")
    ]);

  let found = false;

  agentsSnap.forEach(child => {
    const value = child.val();

    if (
      child.key !== exceptUid &&
      normalizePhone(value?.phone) ===
        normalized
    ) {
      found = true;
    }
  });

  transportersSnap.forEach(child => {
    const value = child.val();

    if (
      child.key !== exceptUid &&
      normalizePhone(value?.phone) ===
        normalized
    ) {
      found = true;
    }
  });

  return found;
}

/* =========================================================
   PLATFORM AGENT REGISTER
========================================================= */

app.post(
  "/api/register-platform-agent",
  async (req, res) => {
    try {
      const name =
        cleanText(
          req.body.name,
          150
        );

      const phone =
        normalizePhone(
          req.body.phone
        );

      const location =
        cleanText(
          req.body.location,
          150
        );

      const region =
        cleanText(
          req.body.region,
          150
        );

      const serviceType =
        cleanText(
          req.body.serviceType,
          150
        );

      const description =
        cleanText(
          req.body.description,
          500
        );

      const pin =
        cleanText(
          req.body.pin,
          50
        );

      if (
        !name ||
        !phone ||
        !pin
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Jina, simu na PIN vinahitajika."
        });
      }

      if (
        await phoneExists(phone)
      ) {
        return res.status(409).json({
          ok: false,
          error:
            "Namba hii tayari imesajiliwa."
        });
      }

      const uid =
        makeUid("platformAgent");

      const pinHash =
        await bcrypt.hash(
          pin,
          12
        );

      const profile = {
        uid,
        profileType: "agent",
        name,
        phone,
        location,
        region,
        serviceType,
        description,
        status: "active",
        approvedAt: nowISO(),
        createdAt: nowISO(),
        private: {
          pinHash
        }
      };

      await db
        .ref(
          `platformAgents/${uid}`
        )
        .set(profile);

      await createFirebaseUser(
        uid,
        name
      );

      return res.json({
        ok: true,
        uid,
        status: "active",
        message:
          "Usajili umefanikiwa. Akaunti yako imekuwa active automatically."
      });
    } catch (error) {
      console.error(
        "PLATFORM AGENT REGISTER ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Agent registration imeshindikana."
      });
    }
  }
);

/* =========================================================
   TRANSPORTER REGISTER
========================================================= */

app.post(
  "/api/register-platform-transporter",
  async (req, res) => {
    try {
      const name =
        cleanText(
          req.body.businessName ||
          req.body.name,
          180
        );

      const phone =
        normalizePhone(
          req.body.phone
        );

      const location =
        cleanText(
          req.body.location,
          150
        );

      const region =
        cleanText(
          req.body.region,
          150
        );

      const transportType =
        cleanText(
          req.body.transportType,
          150
        );

      const routes =
        cleanText(
          req.body.routes,
          300
        );

      const cargoTypes =
        cleanText(
          req.body.cargoTypes,
          300
        );

      const availability =
        cleanText(
          req.body.availability,
          30
        ) || "available";

      const description =
        cleanText(
          req.body.description,
          500
        );

      const pin =
        cleanText(
          req.body.pin,
          50
        );

      if (
        !name ||
        !phone ||
        !pin
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Jina, simu na PIN vinahitajika."
        });
      }

      if (
        await phoneExists(phone)
      ) {
        return res.status(409).json({
          ok: false,
          error:
            "Namba hii tayari imesajiliwa."
        });
      }

      const uid =
        makeUid("transporter");

      const pinHash =
        await bcrypt.hash(
          pin,
          12
        );

      const profile = {
        uid,
        profileType: "transporter",
        name,
        businessName: name,
        phone,
        location,
        region,
        transportType,
        routes,
        cargoTypes,
        availability,
        description,
        status: "active",
        approvedAt: nowISO(),
        createdAt: nowISO(),
        private: {
          pinHash
        }
      };

      await db
        .ref(
          `transporters/${uid}`
        )
        .set(profile);

      await createFirebaseUser(
        uid,
        name
      );

      return res.json({
        ok: true,
        uid,
        status: "active",
        message:
          "Usajili umefanikiwa. Transporter wako ameonekana active automatically."
      });
    } catch (error) {
      console.error(
        "TRANSPORTER REGISTER ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Transporter registration imeshindikana."
      });
    }
  }
);

/* =========================================================
   PLATFORM LOGIN
========================================================= */

app.post(
  "/api/platform-login",
  async (req, res) => {
    try {
      const type =
        cleanText(
          req.body.type,
          30
        );

      const phone =
        normalizePhone(
          req.body.phone
        );

      const pin =
        cleanText(
          req.body.pin,
          50
        );

      if (
        !["agent","transporter"]
          .includes(type)
      ) {
        return res.status(400).json({
          ok: false,
          error:
            "Account type si sahihi."
        });
      }

      let collection =
        type === "agent"
          ? "platformAgents"
          : "transporters";

      const snapshot =
        await db
          .ref(collection)
          .once("value");

      let profile = null;

      snapshot.forEach(child => {
        const value =
          child.val();

        if (
          normalizePhone(
            value?.phone
          ) === phone
        ) {
          profile = value;
        }
      });

      if (!profile) {
        return res.status(401).json({
          ok: false,
          error:
            "Namba au PIN si sahihi."
        });
      }

      if (
        profile.status !==
        "active"
      ) {
        return res.status(403).json({
          ok: false,
          error:
            "Akaunti hii haiko active."
        });
      }

      const valid =
        await bcrypt.compare(
          pin,
          profile.private?.pinHash ||
            ""
        );

      if (!valid) {
        return res.status(401).json({
          ok: false,
          error:
            "Namba au PIN si sahihi."
        });
      }

      await createFirebaseUser(
        profile.uid,
        profile.name ||
          profile.businessName
      );

      const role =
        type === "agent"
          ? "platform_agent"
          : "transporter";

      const token =
        await admin.auth()
          .createCustomToken(
            profile.uid,
            {
              role,
              profileType: type,
              profileId: profile.uid
            }
          );

      return res.json({
        ok: true,
        token,
        role,
        profileType: type,
        profileId: profile.uid,
        name:
          profile.name ||
          profile.businessName
      });
    } catch (error) {
      console.error(
        "PLATFORM LOGIN ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Platform login imeshindikana."
      });
    }
  }
);

/* =========================================================
   PUBLIC DIRECTORY
========================================================= */

function publicAgent(profile) {
  return {
    uid: profile.uid,
    profileType: "agent",
    name: profile.name,
    phone: profile.phone,
    location: profile.location,
    region: profile.region,
    serviceType:
      profile.serviceType,
    description:
      profile.description,
    status: profile.status,
    createdAt: profile.createdAt
  };
}

function publicTransporter(profile) {
  return {
    uid: profile.uid,
    profileType: "transporter",
    name:
      profile.name ||
      profile.businessName,
    businessName:
      profile.businessName,
    phone: profile.phone,
    location: profile.location,
    region: profile.region,
    transportType:
      profile.transportType,
    routes:
      profile.routes,
    cargoTypes:
      profile.cargoTypes,
    availability:
      profile.availability,
    description:
      profile.description,
    status: profile.status,
    createdAt: profile.createdAt
  };
}

function matchesSearch(
  profile,
  query
) {
  if (!query) return true;

  const text =
    [
      profile.name,
      profile.businessName,
      profile.phone,
      profile.location,
      profile.region,
      profile.serviceType,
      profile.transportType,
      profile.routes,
      profile.cargoTypes,
      profile.description
    ]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();

  return text.includes(
    query.toLowerCase()
  );
}

app.get(
  "/api/agents",
  async (req, res) => {
    try {
      const search =
        cleanText(
          req.query.search,
          150
        );

      const snapshot =
        await db
          .ref("platformAgents")
          .once("value");

      const agents = [];

      snapshot.forEach(child => {
        const profile =
          child.val();

        if (
          profile?.status !==
          "active"
        ) {
          return;
        }

        if (
          !matchesSearch(
            profile,
            search
          )
        ) {
          return;
        }

        agents.push(
          publicAgent(profile)
        );
      });

      agents.sort(
        (a,b) =>
          new Date(b.createdAt) -
          new Date(a.createdAt)
      );

      return res.json({
        ok: true,
        agents
      });
    } catch (error) {
      console.error(
        "AGENTS LIST ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Imeshindikana kupata Agents."
      });
    }
  }
);

app.get(
  "/api/transporters",
  async (req, res) => {
    try {
      const search =
        cleanText(
          req.query.search,
          150
        );

      const snapshot =
        await db
          .ref("transporters")
          .once("value");

      const transporters = [];

      snapshot.forEach(child => {
        const profile =
          child.val();

        if (
          profile?.status !==
          "active"
        ) {
          return;
        }

        if (
          !matchesSearch(
            profile,
            search
          )
        ) {
          return;
        }

        transporters.push(
          publicTransporter(profile)
        );
      });

      transporters.sort(
        (a,b) =>
          new Date(b.createdAt) -
          new Date(a.createdAt)
      );

      return res.json({
        ok: true,
        transporters
      });
    } catch (error) {
      console.error(
        "TRANSPORTERS LIST ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Imeshindikana kupata Transporters."
      });
    }
  }
);

/* =========================================================
   PUBLIC PROFILE
========================================================= */

app.get(
  "/api/agents/:uid",
  async (req, res) => {
    try {
      const snapshot =
        await db
          .ref(
            `platformAgents/${req.params.uid}`
          )
          .once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Agent hakupatikana."
        });
      }

      const profile =
        snapshot.val();

      if (
        profile.status !==
        "active"
      ) {
        return res.status(404).json({
          ok: false,
          error:
            "Agent hayupo active."
        });
      }

      return res.json({
        ok: true,
        profile:
          publicAgent(profile)
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Imeshindikana kupata profile."
      });
    }
  }
);

app.get(
  "/api/transporters/:uid",
  async (req, res) => {
    try {
      const snapshot =
        await db
          .ref(
            `transporters/${req.params.uid}`
          )
          .once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Transporter hakupatikana."
        });
      }

      const profile =
        snapshot.val();

      if (
        profile.status !==
        "active"
      ) {
        return res.status(404).json({
          ok: false,
          error:
            "Transporter hayupo active."
        });
      }

      return res.json({
        ok: true,
        profile:
          publicTransporter(profile)
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Imeshindikana kupata profile."
      });
    }
  }
);

/* =========================================================
   ADMIN LOGIN
========================================================= */

async function ensureAdminFirebaseUser() {
  const username =
    process.env.ADMIN_USERNAME;

  const uid =
    "admin_" +
    crypto
      .createHash("sha256")
      .update(
        username || "makyama-admin"
      )
      .digest("hex")
      .slice(0, 28);

  await createFirebaseUser(
    uid,
    "MAKYAMA ADMIN"
  );

  return uid;
}

app.post(
  "/api/admin/login",
  async (req, res) => {
    try {
      const username =
        cleanText(
          req.body.username,
          100
        );

      const password =
        String(
          req.body.password || ""
        );

      if (
        !process.env.ADMIN_USERNAME ||
        !process.env.ADMIN_PASSWORD
      ) {
        return res.status(503).json({
          ok: false,
          error:
            "Admin credentials hazijawekwa kwenye Render Environment Variables."
        });
      }

      if (
        username !==
          process.env.ADMIN_USERNAME ||
        password !==
          process.env.ADMIN_PASSWORD
      ) {
        return res.status(401).json({
          ok: false,
          error:
            "Admin username au password si sahihi."
        });
      }

      const uid =
        await ensureAdminFirebaseUser();

      const token =
        await admin.auth()
          .createCustomToken(
            uid,
            {
              role: "admin",
              admin: true
            }
          );

      return res.json({
        ok: true,
        token,
        role: "admin"
      });
    } catch (error) {
      console.error(
        "ADMIN LOGIN ERROR:",
        error
      );

      return res.status(500).json({
        ok: false,
        error:
          "Admin login imeshindikana."
      });
    }
  }
);

/* =========================================================
   ADMIN STATS
========================================================= */

app.get(
  "/api/admin/stats",
  requireAdmin,
  async (req, res) => {
    try {
      const [
        agents,
        transporters,
        cargos
      ] = await Promise.all([
        db.ref("platformAgents")
          .once("value"),
        db.ref("transporters")
          .once("value"),
        db.ref("cargo")
          .once("value")
      ]);

      return res.json({
        ok: true,
        stats: {
          agents:
            agents.numChildren(),
          transporters:
            transporters.numChildren(),
          cargos:
            cargos.numChildren()
        }
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Stats hazikupatikana."
      });
    }
  }
);

/* =========================================================
   ADMIN AGENTS
========================================================= */

app.get(
  "/api/admin/agents",
  requireAdmin,
  async (req, res) => {
    try {
      const snapshot =
        await db
          .ref("platformAgents")
          .once("value");

      const agents = [];

      snapshot.forEach(child => {
        const profile =
          child.val();

        agents.push(
          publicAgent(profile)
        );
      });

      return res.json({
        ok: true,
        agents
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Admin Agents hazikupatikana."
      });
    }
  }
);

/* =========================================================
   ADMIN TRANSPORTERS
========================================================= */

app.get(
  "/api/admin/transporters",
  requireAdmin,
  async (req, res) => {
    try {
      const snapshot =
        await db
          .ref("transporters")
          .once("value");

      const transporters = [];

      snapshot.forEach(child => {
        transporters.push(
          publicTransporter(
            child.val()
          )
        );
      });

      return res.json({
        ok: true,
        transporters
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Admin Transporters hazikupatikana."
      });
    }
  }
);

/* =========================================================
   ADMIN CARGOS
========================================================= */

app.get(
  "/api/admin/cargos",
  requireAdmin,
  async (req, res) => {
    try {
      const snapshot =
        await db
          .ref("cargo")
          .once("value");

      const cargos = [];

      snapshot.forEach(child => {
        cargos.push(
          makeAdminCargo(
            child.val()
          )
        );
      });

      return res.json({
        ok: true,
        cargos
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Admin cargos hazikupatikana."
      });
    }
  }
);

/* =========================================================
   ADMIN EDIT AGENT
========================================================= */

app.patch(
  "/api/admin/agents/:uid",
  requireAdmin,
  async (req, res) => {
    try {
      const ref =
        db.ref(
          `platformAgents/${req.params.uid}`
        );

      const snapshot =
        await ref.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Agent hakupatikana."
        });
      }

      const old =
        snapshot.val();

      const allowed = [
        "name",
        "phone",
        "location",
        "region",
        "serviceType",
        "description",
        "status"
      ];

      const update = {};

      allowed.forEach(key => {
        if (
          req.body[key] !==
          undefined
        ) {
          update[key] =
            cleanText(
              req.body[key],
              key === "description"
                ? 500
                : 180
            );
        }
      });

      await ref.update(update);

      return res.json({
        ok: true,
        profile:
          publicAgent({
            ...old,
            ...update
          })
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Agent edit imeshindikana."
      });
    }
  }
);

/* =========================================================
   ADMIN EDIT TRANSPORTER
========================================================= */

app.patch(
  "/api/admin/transporters/:uid",
  requireAdmin,
  async (req, res) => {
    try {
      const ref =
        db.ref(
          `transporters/${req.params.uid}`
        );

      const snapshot =
        await ref.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Transporter hakupatikana."
        });
      }

      const old =
        snapshot.val();

      const allowed = [
        "name",
        "businessName",
        "phone",
        "location",
        "region",
        "transportType",
        "routes",
        "cargoTypes",
        "availability",
        "description",
        "status"
      ];

      const update = {};

      allowed.forEach(key => {
        if (
          req.body[key] !==
          undefined
        ) {
          update[key] =
            cleanText(
              req.body[key],
              key === "description"
                ? 500
                : 300
            );
        }
      });

      await ref.update(update);

      return res.json({
        ok: true,
        profile:
          publicTransporter({
            ...old,
            ...update
          })
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Transporter edit imeshindikana."
      });
    }
  }
);

/* =========================================================
   ADMIN DELETE AGENT
========================================================= */

app.delete(
  "/api/admin/agents/:uid",
  requireAdmin,
  async (req, res) => {
    try {
      const uid =
        req.params.uid;

      const ref =
        db.ref(
          `platformAgents/${uid}`
        );

      const snapshot =
        await ref.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Agent hakupatikana."
        });
      }

      await ref.remove();
      await deleteFirebaseUser(uid);

      return res.json({
        ok: true
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Agent delete imeshindikana."
      });
    }
  }
);

/* =========================================================
   ADMIN DELETE TRANSPORTER
========================================================= */

app.delete(
  "/api/admin/transporters/:uid",
  requireAdmin,
  async (req, res) => {
    try {
      const uid =
        req.params.uid;

      const ref =
        db.ref(
          `transporters/${uid}`
        );

      const snapshot =
        await ref.once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          ok: false,
          error:
            "Transporter hakupatikana."
        });
      }

      await ref.remove();
      await deleteFirebaseUser(uid);

      return res.json({
        ok: true
      });
    } catch (error) {
      return res.status(500).json({
        ok: false,
        error:
          "Transporter delete imeshindikana."
      });
    }
  }
);

/* =========================================================
   404
========================================================= */

app.use(
  (req, res) => {
    res.status(404).json({
      ok: false,
      error:
        "Endpoint haijapatikana."
    });
  }
);

/* =========================================================
   ERROR
========================================================= */

app.use(
  (error, req, res, next) => {
    console.error(
      "SERVER ERROR:",
      error
    );

    res.status(500).json({
      ok: false,
      error:
        "Server error."
    });
  }
);

app.listen(
  PORT,
  () => {
    console.log(
      `🚚 MAKYAMA TRANSPORT server running on ${PORT}`
    );
  }
);
