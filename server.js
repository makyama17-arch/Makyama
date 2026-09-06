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

/* =========================================================
   FIREBASE
========================================================= */

if (!process.env.FIREBASE_SERVICE_ACCOUNT) {
  console.error(
    "❌ FIREBASE_SERVICE_ACCOUNT haijawekwa kwenye Render."
  );

  process.exit(1);
}

let serviceAccount;

try {
  serviceAccount = JSON.parse(
    process.env.FIREBASE_SERVICE_ACCOUNT
  );
} catch (err) {
  console.error(
    "❌ FIREBASE_SERVICE_ACCOUNT si JSON sahihi."
  );

  process.exit(1);
}

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(
      serviceAccount
    ),

    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      "https://makyama-e5e89-default-rtdb.firebaseio.com"
  });
}

const db = admin.database();


/* =========================================================
   HELPERS
========================================================= */

function makeTrackingId() {
  return (
    "DAG-" +
    Math.floor(
      100000 +
      Math.random() * 900000
    )
  );
}

function makePin() {
  return String(
    Math.floor(
      100000 +
      Math.random() * 900000
    )
  );
}

function makeUid() {
  return crypto.randomUUID();
}

function nowISO() {
  return new Date().toISOString();
}

function cleanText(
  value,
  max = 200
) {
  if (
    typeof value !== "string"
  ) {
    return "";
  }

  return value
    .trim()
    .slice(0, max);
}


/* =========================================================
   LOCATION
========================================================= */

async function reverseGeocode(
  lat,
  lng
) {
  try {

    const url =
      "https://nominatim.openstreetmap.org/reverse" +
      `?lat=${encodeURIComponent(lat)}` +
      `&lon=${encodeURIComponent(lng)}` +
      "&format=json&zoom=18";

    const response =
      await fetch(
        url,
        {
          headers: {
            "User-Agent":
              "MAKYAMA-Dagaa-Transport/1.0"
          }
        }
      );

    if (!response.ok) {
      return "";
    }

    const data =
      await response.json();

    return data.display_name || "";

  } catch (err) {

    return "";

  }
}


function validLocation(
  location
) {

  if (!location) {
    return false;
  }

  const lat =
    Number(location.lat);

  const lng =
    Number(location.lng);

  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= -90 &&
    lat <= 90 &&
    lng >= -180 &&
    lng <= 180
  );
}


/* =========================================================
   AUTHENTICATION
========================================================= */

async function requireAuth(
  req,
  res,
  next
) {

  try {

    const header =
      req.headers.authorization ||
      "";

    if (
      !header.startsWith(
        "Bearer "
      )
    ) {

      return res.status(401).json({
        ok: false,
        error:
          "Authorization token haipo."
      });

    }

    const token =
      header.substring(7);

    /*
      checkRevoked = true
      inasaidia kuzuia token za
      accounts zilizofutwa/revoked.
    */

    const decoded =
      await admin.auth()
        .verifyIdToken(
          token,
          true
        );

    req.user =
      decoded;

    next();

  } catch (err) {

    console.error(
      "AUTH ERROR:",
      err.message
    );

    return res.status(401).json({

      ok: false,

      error:
        "Session imekwisha au token si sahihi."

    });

  }

}


/* =========================================================
   GET CARGO
========================================================= */

async function getCargo(
  trackingId
) {

  const snapshot =
    await db
      .ref(
        `cargo/${trackingId}`
      )
      .once("value");

  if (
    !snapshot.exists()
  ) {

    return null;

  }

  return snapshot.val();

}


/* =========================================================
   REMOVE PRIVATE DATA
========================================================= */

function removePrivateData(
  cargo
) {

  if (!cargo) {
    return null;
  }

  const safeCargo = {

    trackingId:
      cargo.trackingId,

    cargoName:
      cargo.cargoName,

    origin:
      cargo.origin,

    destination:
      cargo.destination,

    bossName:
      cargo.bossName,

    status:
      cargo.status,

    createdAt:
      cargo.createdAt

  };

  /*
    Boss taarifa salama.
    Hakuna boss.private.pinHash
    */

  if (cargo.boss) {

    safeCargo.boss = {

      uid:
        cargo.boss.uid,

      name:
        cargo.boss.name,

      history:
        cargo.boss.history ||
        {}

    };

  }

  /*
    Agents bila private.pinHash
    */

  safeCargo.stages = {};

  const stages =
    cargo.stages || {};

  for (
    const key of Object.keys(
      stages
    )
  ) {

    const stage =
      stages[key];

    safeCargo.stages[key] = {

      stage:
        stage.stage,

      name:
        stage.name,

      uid:
        stage.uid,

      status:
        stage.status,

      history:
        stage.history ||
        {}

    };

  }

  return safeCargo;

}


/* =========================================================
   PUBLIC CARGO
   ONLY SAFE INFORMATION
========================================================= */

function makePublicCargo(
  cargo
) {

  if (!cargo) {
    return null;
  }

  return {

    trackingId:
      cargo.trackingId,

    cargoName:
      cargo.cargoName,

    origin:
      cargo.origin,

    destination:
      cargo.destination,

    status:
      cargo.status,

    createdAt:
      cargo.createdAt

  };

}


/* =========================================================
   AGENT-SPECIFIC CARGO
========================================================= */

function makeAgentCargo(
  cargo,
  stageNumber
) {

  if (!cargo) {
    return null;
  }

  const stageKey =
    `stage_${stageNumber}`;

  const myStage =
    cargo.stages?.[
      stageKey
    ];

  if (!myStage) {
    return null;
  }

  /*
    Agent 1 anaweza kuona
    taarifa zote za operational
    lakini PIN hashes hazitoki.
  */

  if (
    Number(stageNumber) === 1
  ) {

    return removePrivateData(
      cargo
    );

  }

  /*
    Agents 2+
    wanaona taarifa za mzigo
    + stage yao tu.

    Hawaoni:
    - Agent mwingine
    - GPS ya Agent mwingine
    - History ya Agent mwingine
    - Boss history
    */

  return {

    trackingId:
      cargo.trackingId,

    cargoName:
      cargo.cargoName,

    origin:
      cargo.origin,

    destination:
      cargo.destination,

    status:
      cargo.status,

    createdAt:
      cargo.createdAt,

    stages: {

      [stageKey]: {

        stage:
          myStage.stage,

        name:
          myStage.name,

        uid:
          myStage.uid,

        status:
          myStage.status,

        history:
          myStage.history ||
          {}

      }

    }

  };

}


/* =========================================================
   ROOT
========================================================= */

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


/* =========================================================
   HEALTH
========================================================= */

app.get(
  "/api/health",
  (req, res) => {

    res.json({

      ok: true,

      service:
        "MAKYAMA Dagaa Transport",

      time:
        nowISO()

    });

  }
);


/* =========================================================
   CREATE CARGO
========================================================= */

app.post(
  "/api/create-cargo",
  async (req, res) => {

    try {

      const cargoName =
        cleanText(
          req.body.cargoName
        );

      const origin =
        cleanText(
          req.body.origin
        );

      const destination =
        cleanText(
          req.body.destination
        );

      const bossName =
        cleanText(
          req.body.bossName
        );

      let agents =
        Array.isArray(
          req.body.agents
        )
          ? req.body.agents
          : [];

      agents =
        agents
          .map(
            agent => ({

              name:
                cleanText(
                  agent?.name
                )

            })
          )
          .filter(
            agent =>
              agent.name
          );


      if (!cargoName) {

        return res.status(400).json({

          ok: false,

          error:
            "Jina la mzigo linahitajika."

        });

      }


      if (!origin) {

        return res.status(400).json({

          ok: false,

          error:
            "Sehemu ya kuanzia inahitajika."

        });

      }


      if (!destination) {

        return res.status(400).json({

          ok: false,

          error:
            "Sehemu ya mwisho inahitajika."

        });

      }


      if (!bossName) {

        return res.status(400).json({

          ok: false,

          error:
            "Jina la Boss linahitajika."

        });

      }


      if (
        agents.length < 1
      ) {

        return res.status(400).json({

          ok: false,

          error:
            "Weka angalau Agent mmoja."

        });

      }


      if (
        agents.length > 20
      ) {

        return res.status(400).json({

          ok: false,

          error:
            "Agents ni wengi sana."

        });

      }


      /* =========================
         TRACKING ID
      ========================= */

      let trackingId;

      for (
        let i = 0;
        i < 20;
        i++
      ) {

        const candidate =
          makeTrackingId();

        const snapshot =
          await db
            .ref(
              `cargo/${candidate}`
            )
            .once("value");

        if (
          !snapshot.exists()
        ) {

          trackingId =
            candidate;

          break;

        }

      }


      if (!trackingId) {

        return res.status(500).json({

          ok: false,

          error:
            "Imeshindikana kutengeneza Tracking ID."

        });

      }


      /* =========================
         BOSS
      ========================= */

      const bossPin =
        makePin();

      const bossPinHash =
        await bcrypt.hash(
          bossPin,
          12
        );


      /* =========================
         AGENTS
      ========================= */

      const stages = {};

      const returnedAgents = [];


      for (
        let i = 0;
        i < agents.length;
        i++
      ) {

        const stageNumber =
          i + 1;

        const pin =
          makePin();

        const pinHash =
          await bcrypt.hash(
            pin,
            12
          );

        const uid =
          makeUid();


        stages[
          `stage_${stageNumber}`
        ] = {

          stage:
            stageNumber,

          name:
            agents[i].name,

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

          stage:
            stageNumber,

          name:
            agents[i].name,

          pin

        });

      }


      /* =========================
         CARGO
      ========================= */

      const cargo = {

        trackingId,

        cargoName,

        origin,

        destination,

        bossName,

        status:
          "WAITING_FOR_RELEASE",

        createdAt:
          nowISO(),

        boss: {

          uid:
            makeUid(),

          name:
            bossName,

          private: {

            pinHash:
              bossPinHash

          },

          history: {}

        },

        stages

      };


      await db
        .ref(
          `cargo/${trackingId}`
        )
        .set(
          cargo
        );


      return res.json({

        ok: true,

        message:
          "Mzigo umefanikiwa kusajiliwa.",

        trackingId,

        boss: {

          name:
            bossName,

          pin:
            bossPin

        },

        agents:
          returnedAgents

      });


    } catch (err) {

      console.error(
        "CREATE CARGO ERROR:",
        err
      );

      return res.status(500).json({

        ok: false,

        error:
          "Server error wakati wa kusajili mzigo."

      });

    }

  }
);


/* =========================================================
   LOGIN
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
          20
        );


      if (
        !trackingId ||
        !pin
      ) {

        return res.status(400).json({

          ok: false,

          error:
            "Tracking ID na PIN vinahitajika."

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
            "Mzigo haujapatikana."

        });

      }


      /* =========================
         CHECK BOSS
      ========================= */

      if (
        cargo.boss?.private?.pinHash
      ) {

        const match =
          await bcrypt.compare(
            pin,
            cargo.boss.private.pinHash
          );


        if (match) {

          const uid =
            cargo.boss.uid;


          try {

            await admin.auth()
              .deleteUser(
                uid
              );

          } catch (_) {}


          try {

            await admin.auth()
              .createUser({

                uid

              });

          } catch (_) {}


          const token =
            await admin.auth()
              .createCustomToken(
                uid,
                {

                  role:
                    "boss",

                  trackingId,

                  supervisor:
                    true

                }
              );


          return res.json({

            ok: true,

            role:
              "boss",

            name:
              cargo.boss.name,

            trackingId,

            token

          });

        }

      }


      /* =========================
         CHECK AGENTS
      ========================= */

      const stages =
        cargo.stages ||
        {};


      for (
        const key of Object.keys(
          stages
        )
      ) {

        const stage =
          stages[key];


        if (
          !stage.private?.pinHash
        ) {

          continue;

        }


        const match =
          await bcrypt.compare(
            pin,
            stage.private.pinHash
          );


        if (match) {

          const uid =
            stage.uid;


          try {

            await admin.auth()
              .deleteUser(
                uid
              );

          } catch (_) {}


          try {

            await admin.auth()
              .createUser({

                uid

              });

          } catch (_) {}


          const token =
            await admin.auth()
              .createCustomToken(
                uid,
                {

                  role:
                    "agent",

                  trackingId,

                  stage:
                    stage.stage,

                  supervisor:
                    stage.stage === 1

                }
              );


          return res.json({

            ok: true,

            role:
              "agent",

            stage:
              stage.stage,

            name:
              stage.name,

            trackingId,

            token

          });

        }

      }


      return res.status(401).json({

        ok: false,

        error:
          "PIN si sahihi."

      });


    } catch (err) {

      console.error(
        "LOGIN ERROR:",
        err
      );

      return res.status(500).json({

        ok: false,

        error:
          "Server error wakati wa login."

      });

    }

  }
);


/* =========================================================
   CARGO ACCESS
========================================================= */

/*
  MUHIMU:

  GET /api/cargo/:trackingId

  Bila login:
      PUBLIC DATA ONLY

  Boss:
      FULL DATA

  Agent 1:
      FULL OPERATIONAL DATA

  Agent 2+:
      OWN STAGE ONLY
*/

app.get(
  "/api/cargo/:trackingId",
  async (req, res) => {

    try {

      const trackingId =
        cleanText(
          req.params.trackingId,
          50
        ).toUpperCase();


      if (!trackingId) {

        return res.status(400).json({

          ok: false,

          error:
            "Tracking ID haipo."

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
            "Mzigo haujapatikana."

        });

      }


      /* =====================================================
         PUBLIC USER
      ===================================================== */

      const header =
        req.headers.authorization ||
        "";


      if (
        !header.startsWith(
          "Bearer "
        )
      ) {

        return res.json({

          ok: true,

          cargo:
            makePublicCargo(
              cargo
            )

        });

      }


      /* =====================================================
         AUTHENTICATED USER
      ===================================================== */

      let decoded;

      try {

        const token =
          header.substring(7);

        decoded =
          await admin.auth()
            .verifyIdToken(
              token,
              true
            );

      } catch (authError) {

        return res.status(401).json({

          ok: false,

          error:
            "Session si sahihi."

        });

      }


      /*
        MUHIMU:
        Token lazima iwe ya mzigo
        unaotafutwa.
      */

      if (
        decoded.trackingId !==
        trackingId
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Huna ruhusa ya kuona mzigo huu."

        });

      }


      /* =====================================================
         BOSS
      ===================================================== */

      if (
        decoded.role ===
        "boss"
      ) {

        if (
          cargo.boss?.uid !==
          decoded.uid
        ) {

          return res.status(403).json({

            ok: false,

            error:
              "Huna ruhusa ya kuona taarifa za Boss."

          });

        }


        return res.json({

          ok: true,

          role:
            "boss",

          cargo:
            removePrivateData(
              cargo
            )

        });

      }


      /* =====================================================
         AGENT
      ===================================================== */

      if (
        decoded.role ===
        "agent"
      ) {

        const stageNumber =
          Number(
            decoded.stage
          );


        if (
          !Number.isInteger(
            stageNumber
          ) ||
          stageNumber < 1
        ) {

          return res.status(403).json({

            ok: false,

            error:
              "Agent stage si sahihi."

          });

        }


        const stageKey =
          `stage_${stageNumber}`;


        const stage =
          cargo.stages?.[
            stageKey
          ];


        if (!stage) {

          return res.status(403).json({

            ok: false,

            error:
              "Agent huyu hayupo kwenye mzigo huu."

          });

        }


        /*
          UID ya token lazima
          ifanane na Agent mwenyewe.
        */

        if (
          stage.uid !==
          decoded.uid
        ) {

          return res.status(403).json({

            ok: false,

            error:
              "Huna ruhusa ya taarifa za Agent huyu."

          });

        }


        return res.json({

          ok: true,

          role:
            "agent",

          stage:
            stageNumber,

          cargo:
            makeAgentCargo(
              cargo,
              stageNumber
            )

        });

      }


      return res.status(403).json({

        ok: false,

        error:
          "Role haijaruhusiwa."

      });


    } catch (err) {

      console.error(
        "CARGO ACCESS ERROR:",
        err
      );

      return res.status(500).json({

        ok: false,

        error:
          "Imeshindikana kupata taarifa za mzigo."

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
        req.user.role !==
        "agent"
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Hii ni kwa Agent pekee."

        });

      }


      const trackingId =
        cleanText(
          req.user.trackingId,
          50
        ).toUpperCase();


      const stageNumber =
        Number(
          req.user.stage
        );


      const action =
        cleanText(
          req.body.action,
          30
        ).toUpperCase();


      const location =
        req.body.location ||
        null;


      if (
        ![
          "RELEASE",
          "RECEIVE"
        ].includes(
          action
        )
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
        await cargoRef
          .once("value");


      if (
        !snapshot.exists()
      ) {

        return res.status(404).json({

          ok: false,

          error:
            "Mzigo haujapatikana."

        });

      }


      const cargo =
        snapshot.val();


      const stageKey =
        `stage_${stageNumber}`;


      const stage =
        cargo.stages?.[
          stageKey
        ];


      if (!stage) {

        return res.status(404).json({

          ok: false,

          error:
            "Agent stage haijapatikana."

        });

      }


      /*
        SECURITY:
        Token lazima iwe ya
        Agent huyu.
      */

      if (
        stage.uid !==
        req.user.uid
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Huna ruhusa ya kufanya action hii."

        });

      }


      /* =====================================================
         AGENT 1
      ===================================================== */

      if (
        stageNumber === 1
      ) {

        if (
          action !==
          "RELEASE"
        ) {

          return res.status(400).json({

            ok: false,

            error:
              "Agent 1 anatakiwa kuanza kwa RELEASE."

          });

        }


        if (
          stage.status !==
          "WAITING_FOR_RELEASE"
        ) {

          return res.status(400).json({

            ok: false,

            error:
              "Agent 1 tayari ameshafanya action hii."

          });

        }

      }


      /* =====================================================
         AGENT 2+
      ===================================================== */

      else {

        if (
          action ===
          "RECEIVE" &&
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
          action ===
          "RELEASE" &&
          stage.status !==
          "WAITING_FOR_RELEASE_AFTER_RECEIVE"
        ) {

          return res.status(400).json({

            ok: false,

            error:
              "Agent lazima apokee mzigo kwanza."

          });

        }

      }


      /* =====================================================
         GPS
      ===================================================== */

      let address = "";

      if (
        validLocation(
          location
        )
      ) {

        address =
          await reverseGeocode(

            Number(
              location.lat
            ),

            Number(
              location.lng
            )

          );

      }


      /* =====================================================
         EVENT
      ===================================================== */

      const eventId =
        makeUid();


      const event = {

        id:
          eventId,

        action,

        stage:
          stageNumber,

        agentName:
          stage.name,

        timestamp:
          nowISO(),

        location:
          validLocation(
            location
          )
            ? {

                lat:
                  Number(
                    location.lat
                  ),

                lng:
                  Number(
                    location.lng
                  ),

                address:
                  address || ""

              }

            : null

      };


      await cargoRef
        .child(
          `stages/${stageKey}/history/${eventId}`
        )
        .set(
          event
        );


      /* =====================================================
         AUTOMATIC STATUS
      ===================================================== */

      /*
        AGENT 1 RELEASE
      */

      if (
        stageNumber === 1 &&
        action === "RELEASE"
      ) {

        await cargoRef
          .child(
            `stages/${stageKey}/status`
          )
          .set(
            "RELEASED"
          );


        const nextStage =
          cargo.stages?.stage_2;


        if (nextStage) {

          await cargoRef
            .child(
              "stages/stage_2/status"
            )
            .set(
              "WAITING_FOR_RECEIVE"
            );


          await cargoRef
            .child(
              "status"
            )
            .set(
              "WAITING_FOR_RECEIVE"
            );

        } else {

          /*
            Hakuna Agent mwingine.
            Mzigo unaenda Boss.
          */

          await cargoRef
            .child(
              "status"
            )
            .set(
              "WAITING_FOR_BOSS"
            );

        }

      }


      /*
        AGENT 2+ RECEIVE
      */

      if (
        stageNumber > 1 &&
        action === "RECEIVE"
      ) {

        await cargoRef
          .child(
            `stages/${stageKey}/status`
          )
          .set(
            "WAITING_FOR_RELEASE_AFTER_RECEIVE"
          );


        await cargoRef
          .child(
            "status"
          )
          .set(
            `AGENT_${stageNumber}_RECEIVED`
          );

      }


      /*
        AGENT 2+ RELEASE
      */

      if (
        stageNumber > 1 &&
        action === "RELEASE"
      ) {

        await cargoRef
          .child(
            `stages/${stageKey}/status`
          )
          .set(
            "RELEASED"
          );


        const nextStage =
          cargo.stages?.[
            `stage_${stageNumber + 1}`
          ];


        if (nextStage) {

          await cargoRef
            .child(
              `stages/stage_${stageNumber + 1}/status`
            )
            .set(
              "WAITING_FOR_RECEIVE"
            );


          await cargoRef
            .child(
              "status"
            )
            .set(
              `WAITING_FOR_AGENT_${stageNumber + 1}`
            );

        } else {

          /*
            Agent wa mwisho
            amemaliza.
            Sasa Boss ndiye
            anayesubiriwa.
          */

          await cargoRef
            .child(
              "status"
            )
            .set(
              "WAITING_FOR_BOSS"
            );

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

      console.error(
        "AGENT ACTION ERROR:",
        err
      );

      return res.status(500).json({

        ok: false,

        error:
          "Imeshindikana kuhifadhi action."

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
        req.user.role !==
        "boss"
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Boss pekee ndiye anaweza kupokea mzigo wa mwisho."

        });

      }


      const trackingId =
        cleanText(
          req.user.trackingId,
          50
        ).toUpperCase();


      const location =
        req.body.location ||
        null;


      const cargoRef =
        db.ref(
          `cargo/${trackingId}`
        );


      const snapshot =
        await cargoRef
          .once("value");


      if (
        !snapshot.exists()
      ) {

        return res.status(404).json({

          ok: false,

          error:
            "Mzigo haujapatikana."

        });

      }


      const cargo =
        snapshot.val();


      /*
        Hakikisha token ni ya
        Boss wa mzigo huu.
      */

      if (
        cargo.boss?.uid !==
        req.user.uid
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Huna ruhusa ya kupokea mzigo huu."

        });

      }


      /*
        Boss RECEIVE ONLY
      */

      if (
        cargo.status !==
        "WAITING_FOR_BOSS"
      ) {

        return res.status(400).json({

          ok: false,

          error:
            "Mzigo bado haujafika hatua ya kupokelewa na Boss."

        });

      }


      /* =====================================================
         GPS
      ===================================================== */

      let address = "";

      if (
        validLocation(
          location
        )
      ) {

        address =
          await reverseGeocode(

            Number(
              location.lat
            ),

            Number(
              location.lng
            )

          );

      }


      /* =====================================================
         BOSS EVENT
      ===================================================== */

      const eventId =
        makeUid();


      const event = {

        id:
          eventId,

        action:
          "BOSS_RECEIVE",

        stage:
          "BOSS",

        agentName:
          cargo.bossName,

        timestamp:
          nowISO(),

        location:
          validLocation(
            location
          )
            ? {

                lat:
                  Number(
                    location.lat
                  ),

                lng:
                  Number(
                    location.lng
                  ),

                address:
                  address || ""

              }

            : null

      };


      await cargoRef
        .child(
          `boss/history/${eventId}`
        )
        .set(
          event
        );


      /*
        DELIVERY COMPLETE
      */

      await cargoRef
        .child(
          "status"
        )
        .set(
          "DELIVERED"
        );


      return res.json({

        ok: true,

        message:
          "Boss amepokea mzigo.",

        event

      });


    } catch (err) {

      console.error(
        "BOSS RECEIVE ERROR:",
        err
      );

      return res.status(500).json({

        ok: false,

        error:
          "Imeshindikana kuhifadhi mapokezi ya Boss."

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


      const targetStage =
        Number(
          req.body.stage
        );


      if (
        !Number.isInteger(
          targetStage
        ) ||
        targetStage < 1
      ) {

        return res.status(400).json({

          ok: false,

          error:
            "Agent stage si sahihi."

        });

      }


      /*
        Boss au Agent 1
        pekee.
      */

      const allowed =
        req.user.role ===
          "boss" ||

        (
          req.user.role ===
            "agent" &&

          Number(
            req.user.stage
          ) === 1
        );


      if (!allowed) {

        return res.status(403).json({

          ok: false,

          error:
            "Ni Boss au Agent 1 pekee anaweza kutoa/reset PIN."

        });

      }


      /*
        Agent 1 hawezi
        kutumia endpoint hii
        kujire-set mwenyewe.
      */

      if (
        req.user.role ===
          "agent" &&

        Number(
          req.user.stage
        ) === targetStage
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Agent 1 hawezi kujipa PIN mpya kupitia mfumo huu."

        });

      }


      const cargoRef =
        db.ref(
          `cargo/${trackingId}`
        );


      const snapshot =
        await cargoRef
          .once("value");


      if (
        !snapshot.exists()
      ) {

        return res.status(404).json({

          ok: false,

          error:
            "Mzigo haujapatikana."

        });

      }


      const cargo =
        snapshot.val();


      /*
        Hakikisha Boss ni Boss
        wa mzigo huu.
      */

      if (
        req.user.role ===
          "boss" &&

        cargo.boss?.uid !==
          req.user.uid
      ) {

        return res.status(403).json({

          ok: false,

          error:
            "Huna ruhusa ya reset PIN kwenye mzigo huu."

        });

      }


      /*
        Hakikisha Agent 1
        ni Agent 1 wa mzigo huu.
      */

      if (
        req.user.role ===
          "agent"
      ) {

        if (
          cargo.stages?.stage_1?.uid !==
            req.user.uid
        ) {

          return res.status(403).json({

            ok: false,

            error:
              "Huna ruhusa ya reset PIN."

          });

        }

      }


      const stageKey =
        `stage_${targetStage}`;


      const stage =
        cargo.stages?.[
          stageKey
        ];


      if (!stage) {

        return res.status(404).json({

          ok: false,

          error:
            "Agent huyo hajapatikana."

        });

      }


      /*
        Tengeneza PIN mpya.
      */

      const newPin =
        makePin();


      const newPinHash =
        await bcrypt.hash(
          newPin,
          12
        );


      await cargoRef
        .child(
          `stages/${stageKey}/private/pinHash`
        )
        .set(
          newPinHash
        );


      /*
        Delete old Firebase user
        */

      if (stage.uid) {

        try {

          await admin.auth()
            .deleteUser(
              stage.uid
            );

        } catch (_) {}

      }


      /*
        New UID
      */

      const newUid =
        makeUid();


      try {

        await admin.auth()
          .createUser({

            uid:
              newUid

          });

      } catch (_) {}


      await cargoRef
        .child(
          `stages/${stageKey}/uid`
        )
        .set(
          newUid
        );


      return res.json({

        ok: true,

        message:
          `PIN mpya ya ${stage.name} imetengenezwa.`,

        stage:
          targetStage,

        agentName:
          stage.name,

        pin:
          newPin

      });


    } catch (err) {

      console.error(
        "RESET AGENT PIN ERROR:",
        err
      );

      return res.status(500).json({

        ok: false,

        error:
          "Imeshindikana kutengeneza PIN mpya."

      });

    }

  }
);


/* =========================================================
   404 API
========================================================= */

app.use(
  "/api",
  (req, res) => {

    res.status(404).json({

      ok: false,

      error:
        "API endpoint haijapatikana."

    });

  }
);


/* =========================================================
   SERVER ERROR
========================================================= */

app.use(
  (err, req, res, next) => {

    console.error(
      "SERVER ERROR:",
      err
    );

    res.status(500).json({

      ok: false,

      error:
        "Server error."

    });

  }
);


/* =========================================================
   START SERVER
========================================================= */

app.listen(
  PORT,
  "0.0.0.0",
  () => {

    console.log(
      `🚚 MAKYAMA Dagaa server running on ${PORT}`
    );

  }
);
