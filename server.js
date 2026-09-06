import express from "express";
import cors from "cors";
import bcrypt from "bcryptjs";
import admin from "firebase-admin";
import crypto from "crypto";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();

app.use(cors());
app.use(express.json({ limit: "1mb" }));

// Serve index.html, CSS, JS and other frontend files
app.use(express.static(__dirname));

/* =========================
   FIREBASE ADMIN
========================= */

if (!process.env.FIREBASE_SERVICE_ACCOUNT) {
  console.error("FIREBASE_SERVICE_ACCOUNT haijawekwa kwenye Render.");
  process.exit(1);
}

let serviceAccount;

try {
  serviceAccount = JSON.parse(
    process.env.FIREBASE_SERVICE_ACCOUNT
  );
} catch (error) {
  console.error(
    "FIREBASE_SERVICE_ACCOUNT si JSON sahihi."
  );
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL:
    "https://makyama-e5e89-default-rtdb.firebaseio.com"
});

const db = admin.database();
const auth = admin.auth();

/* =========================
   HELPERS
========================= */

function trackingId() {
  return (
    "DAG-" +
    Math.floor(100000 + Math.random() * 900000)
  );
}

function pin() {
  return String(
    Math.floor(100000 + Math.random() * 900000)
  );
}

function uid() {
  return "u_" + crypto.randomUUID();
}

async function getPlaceName(lat, lon) {
  try {
    const url =
      "https://nominatim.openstreetmap.org/reverse" +
      `?format=json&lat=${encodeURIComponent(lat)}` +
      `&lon=${encodeURIComponent(lon)}` +
      "&zoom=18&addressdetails=1";

    const response = await fetch(url, {
      headers: {
        "User-Agent":
          "Makyama-Dagaa-Transport/1.0"
      }
    });

    if (!response.ok) {
      return `${lat}, ${lon}`;
    }

    const data = await response.json();

    return (
      data.display_name ||
      `${lat}, ${lon}`
    );
  } catch {
    return `${lat}, ${lon}`;
  }
}

/* =========================
   CREATE CARGO
========================= */

app.post(
  "/api/create-cargo",
  async (req, res) => {
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
          error:
            "Taarifa zote zinahitajika."
        });
      }

      if (agents.length > 50) {
        return res.status(400).json({
          error:
            "Idadi ya mawakala imezidi 50."
        });
      }

      const id = trackingId();

      const bossUid = uid();
      const bossPin = pin();

      const stages = {};
      const agentCredentials = [];

      for (
        let i = 0;
        i < agents.length;
        i++
      ) {
        const stageNumber = i + 1;

        const agentUid = uid();
        const agentPin = pin();

        const hashedPin =
          await bcrypt.hash(agentPin, 12);

        stages[
          `stage_${stageNumber}`
        ] = {
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

        // PIN is returned once to the creator.
        // Plain PIN is NOT stored in Firebase.
        agentCredentials.push({
          number: stageNumber,
          name: agents[i],
          uid: agentUid,
          pin: agentPin
        });
      }

      const bossHashedPin =
        await bcrypt.hash(
          bossPin,
          12
        );

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

      await db
        .ref(`cargo/${id}`)
        .set(cargo);

      return res.json({
        success: true,

        trackingId: id,

        boss: {
          name: bossName,
          pin: bossPin,
          uid: bossUid
        },

        agents: agentCredentials
      });

    } catch (error) {
      console.error(
        "CREATE CARGO ERROR:",
        error
      );

      return res.status(500).json({
        error:
          "Imeshindikana kutengeneza cargo."
      });
    }
  }
);

/* =========================
   LOGIN
========================= */

app.post(
  "/api/login",
  async (req, res) => {
    try {
      const {
        trackingId,
        pin: suppliedPin
      } = req.body;

      if (
        !trackingId ||
        !suppliedPin
      ) {
        return res.status(400).json({
          error:
            "Tracking ID na PIN vinahitajika."
        });
      }

      const cleanTrackingId =
        String(trackingId)
          .trim()
          .toUpperCase();

      const supplied =
        String(suppliedPin).trim();

      const snapshot =
        await db
          .ref(
            `cargo/${cleanTrackingId}`
          )
          .once("value");

      if (!snapshot.exists()) {
        return res.status(401).json({
          error:
            "Tracking ID au PIN sio sahihi."
        });
      }

      const cargo =
        snapshot.val();

      /* =========================
         BOSS LOGIN
      ========================= */

      if (
        cargo.supervisor?.bossPinHash
      ) {
        const correct =
          await bcrypt.compare(
            supplied,
            cargo.supervisor
              .bossPinHash
          );

        if (correct) {
          const token =
            await auth.createCustomToken(
              cargo.supervisor.bossUid,
              {
                role: "boss",
                trackingId:
                  cleanTrackingId,
                supervisor: true
              }
            );

          return res.json({
            success: true,
            token,
            role: "boss",
            trackingId:
              cleanTrackingId,
            bossName:
              cargo.supervisor
                .bossName
          });
        }
      }

      /* =========================
         AGENT LOGIN
      ========================= */

      for (
        const [stageId, stage]
        of Object.entries(
          cargo.stages || {}
        )
      ) {
        const hash =
          stage.private?.pinHash;

        if (!hash) continue;

        const correct =
          await bcrypt.compare(
            supplied,
            hash
          );

        if (correct) {
          const stageNumber =
            Number(stage.order);

          const supervisor =
            stageNumber === 1;

          const token =
            await auth.createCustomToken(
              stage.uid,
              {
                role: "agent",
                trackingId:
                  cleanTrackingId,
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
            trackingId:
              cleanTrackingId,
            agentName:
              stage.private.agentName
          });
        }
      }

      return res.status(401).json({
        error:
          "Tracking ID au PIN sio sahihi."
      });

    } catch (error) {
      console.error(
        "LOGIN ERROR:",
        error
      );

      return res.status(500).json({
        error:
          "Login imeshindikana."
      });
    }
  }
);

/* =========================
   AUTH MIDDLEWARE
========================= */

async function requireAuth(
  req,
  res,
  next
) {
  try {
    const header =
      req.headers.authorization || "";

    if (
      !header.startsWith(
        "Bearer "
      )
    ) {
      return res.status(401).json({
        error:
          "Hujaingia kwenye mfumo."
      });
    }

    const token =
      header.substring(7);

    const decoded =
      await auth.verifyIdToken(
        token
      );

    req.user = decoded;

    next();

  } catch (error) {
    return res.status(401).json({
      error:
        "Session sio halali."
    });
  }
}

/* =========================
   GET CARGO
========================= */

app.get(
  "/api/cargo/:trackingId",
  async (req, res) => {
    try {
      const trackingId =
        String(
          req.params.trackingId
        )
          .trim()
          .toUpperCase();

      const snapshot =
        await db
          .ref(`cargo/${trackingId}`)
          .once("value");

      if (!snapshot.exists()) {
        return res.status(404).json({
          error:
            "Cargo haipo."
        });
      }

      const cargo =
        snapshot.val();

      const publicStages =
        Object.entries(
          cargo.stages || {}
        ).map(
          ([id, stage]) => ({
            id,
            order: stage.order,
            status:
              stage.public?.status ||
              "WAITING",

            agentName:
              stage.private?.agentName ||
              `Agent ${stage.order}`,

            releasedAt:
              stage.public?.releasedAt ||
              null,

            releasedLocationName:
              stage.public
                ?.releasedLocationName ||
              null,

            receivedAt:
              stage.public?.receivedAt ||
              null,

            receivedLocationName:
              stage.public
                ?.receivedLocationName ||
              null
          })
        );

      return res.json({
        success: true,

        trackingId,

        public: cargo.public,

        supervisor: {
          bossName:
            cargo.supervisor
              ?.bossName || ""
        },

        stages:
          publicStages,

        history:
          cargo.history || {}
      });

    } catch (error) {
      console.error(
        "GET CARGO ERROR:",
        error
      );

      return res.status(500).json({
        error:
          "Imeshindikana kupata cargo."
      });
    }
  }
);

/* =========================
   AGENT ACTION
========================= */

app.post(
  "/api/agent-action",
  requireAuth,
  async (req, res) => {
    try {
      if (
        req.user.role !==
        "agent"
      ) {
        return res.status(403).json({
          error:
            "Huruhusiwi."
        });
      }

      const {
        trackingId,
        action,
        lat,
        lon
      } = req.body;

      const cleanTrackingId =
        String(trackingId)
          .trim()
          .toUpperCase();

      if (
        req.user.trackingId !==
        cleanTrackingId
      ) {
        return res.status(403).json({
          error:
            "Cargo sio yako."
        });
      }

      if (
        typeof lat !== "number" ||
        typeof lon !== "number"
      ) {
        return res.status(400).json({
          error:
            "Location inahitajika."
        });
      }

      const stageNumber =
        Number(req.user.stage);

      const stageRef =
        db.ref(
          `cargo/${cleanTrackingId}/stages/stage_${stageNumber}`
        );

      const snap =
        await stageRef.once(
          "value"
        );

      if (!snap.exists()) {
        return res.status(404).json({
          error:
            "Stage haipo."
        });
      }

      const stage =
        snap.val();

      const locationName =
        await getPlaceName(
          lat,
          lon
        );

      const now =
        Date.now();

      /* =========================
         AGENT 1
      ========================= */

      if (stageNumber === 1) {
        if (
          action !== "RELEASE"
        ) {
          return res.status(403).json({
            error:
              "Agent wa kwanza anaweza kufanya NIMETOA tu."
          });
        }

        if (
          stage.public?.status ===
          "RELEASED"
        ) {
          return res.status(400).json({
            error:
              "Mizigo tayari imetolewa."
          });
        }

        await stageRef.update({
          public: {
            ...stage.public,
            status:
              "RELEASED",

            releasedAt:
              now,

            releasedLocationName:
              locationName,

            releasedLocation: {
              lat,
              lon
            }
          }
        });

        await db
          .ref(
            `cargo/${cleanTrackingId}/history`
          )
          .push({
            type:
              "NIMETOA",

            stage:
              stageNumber,

            locationName,

            location: {
              lat,
              lon
            },

            timestamp:
              now
          });

        // Open next stage
        const nextStage =
          stageNumber + 1;

        const nextRef =
          db.ref(
            `cargo/${cleanTrackingId}/stages/stage_${nextStage}`
          );

        const nextSnap =
          await nextRef.once(
            "value"
          );

        if (
          nextSnap.exists()
        ) {
          await nextRef
            .child(
              "public/status"
            )
            .set(
              "READY_TO_RECEIVE"
            );
        } else {
          await db
            .ref(
              `cargo/${cleanTrackingId}/public/status`
            )
            .set(
              "WAITING_FOR_BOSS"
            );
        }

        return res.json({
          success: true,
          message:
            "NIMETOA imehifadhiwa."
        });
      }

      /* =========================
         AGENT 2+
         RECEIVE
      ========================= */

      if (
        action === "RECEIVE"
      ) {
        if (
          stage.public?.status !==
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

            status:
              "RECEIVED",

            receivedAt:
              now,

            receivedLocationName:
              locationName,

            receivedLocation: {
              lat,
              lon
            }
          }
        });

        await db
          .ref(
            `cargo/${cleanTrackingId}/history`
          )
          .push({
            type:
              "NIMEPOKEA",

            stage:
              stageNumber,

            locationName,

            location: {
              lat,
              lon
            },

            timestamp:
              now
          });

        return res.json({
          success: true,
          message:
            "NIMEPOKEA imehifadhiwa."
        });
      }

      /* =========================
         AGENT 2+
         RELEASE
      ========================= */

      if (
        action === "RELEASE"
      ) {
        if (
          stage.public?.status !==
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

            status:
              "RELEASED",

            releasedAt:
              now,

            releasedLocationName:
              locationName,

            releasedLocation: {
              lat,
              lon
            }
          }
        });

        await db
          .ref(
            `cargo/${cleanTrackingId}/history`
          )
          .push({
            type:
              "NIMETOA",

            stage:
              stageNumber,

            locationName,

            location: {
              lat,
              lon
            },

            timestamp:
              now
          });

        const nextStage =
          stageNumber + 1;

        const nextRef =
          db.ref(
            `cargo/${cleanTrackingId}/stages/stage_${nextStage}`
          );

        const nextSnap =
          await nextRef.once(
            "value"
          );

        if (
          nextSnap.exists()
        ) {
          await nextRef
            .child(
              "public/status"
            )
            .set(
              "READY_TO_RECEIVE"
            );
        } else {
          await db
            .ref(
              `cargo/${cleanTrackingId}/public/status`
            )
            .set(
              "WAITING_FOR_BOSS"
            );
        }

        return res.json({
          success: true,
          message:
            "NIMETOA imehifadhiwa."
        });
      }

      return res.status(400).json({
        error:
          "Action haijulikani."
      });

    } catch (error) {
      console.error(
        "AGENT ACTION ERROR:",
        error
      );

      return res.status(500).json({
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
        req.user.role !==
          "boss" ||
        req.user.supervisor !==
          true
      ) {
        return res.status(403).json({
          error:
            "Boss pekee ndiye anaruhusiwa."
        });
      }

      const {
        trackingId,
        lat,
        lon
      } = req.body;

      const cleanTrackingId =
        String(trackingId)
          .trim()
          .toUpperCase();

      if (
        req.user.trackingId !==
        cleanTrackingId
      ) {
        return res.status(403).json({
          error:
            "Cargo sio yako."
        });
      }

      if (
        typeof lat !== "number" ||
        typeof lon !== "number"
      ) {
        return res.status(400).json({
          error:
            "Location inahitajika."
        });
      }

      const cargoRef =
        db.ref(
          `cargo/${cleanTrackingId}`
        );

      const snap =
        await cargoRef.once(
          "value"
        );

      if (!snap.exists()) {
        return res.status(404).json({
          error:
            "Cargo haipo."
        });
      }

      const cargo =
        snap.val();

      if (
        cargo.public?.status !==
        "WAITING_FOR_BOSS"
      ) {
        return res.status(400).json({
          error:
            "Mizigo bado haijafika kwa Boss."
        });
      }

      const locationName =
        await getPlaceName(
          lat,
          lon
        );

      const now =
        Date.now();

      await cargoRef.update({
        "public/status":
          "COMPLETED",

        "public/updatedAt":
          now,

        "supervisor/bossReceivedAt":
          now,

        "supervisor/bossReceivedLocationName":
          locationName,

        "supervisor/bossReceivedLocation":
          {
            lat,
            lon
          }
      });

      await db
        .ref(
          `cargo/${cleanTrackingId}/history`
        )
        .push({
          type:
            "BOSS_NIMEPOKEA",

          locationName,

          location: {
            lat,
            lon
          },

          timestamp:
            now
        });

      return res.json({
        success: true,
        message:
          "Boss amepokea mzigo."
      });

    } catch (error) {
      console.error(
        "BOSS RECEIVE ERROR:",
        error
      );

      return res.status(500).json({
        error:
          "Boss receive imeshindikana."
      });
    }
  }
);

/* =========================
   HOME
========================= */

app.get(
  "/",
  (req, res) => {
    res.sendFile(
      path.join(
        __dirname,
        "index.html"
      )
    );
  }
);

/* =========================
   SERVER
========================= */

const PORT =
  process.env.PORT || 3000;

app.listen(
  PORT,
  "0.0.0.0",
  () => {
    console.log(
      `MAKYAMA Dagaa server running on ${PORT}`
    );
  }
);
