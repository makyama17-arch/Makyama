importScripts(
  "https://www.gstatic.com/firebasejs/10.12.5/firebase-app-compat.js"
);

importScripts(
  "https://www.gstatic.com/firebasejs/10.12.5/firebase-messaging-compat.js"
);

firebase.initializeApp({
  apiKey: "AIzaSyBe2kQr-75nBWLLE5GDNBvhGFT91FtBbBw",
  authDomain: "makyama-e5e89.firebaseapp.com",
  databaseURL: "https://makyama-e5e89-default-rtdb.firebaseio.com",
  projectId: "makyama-e5e89",
  storageBucket: "makyama-e5e89.firebasestorage.app",
  messagingSenderId: "229527204095",
  appId: "1:229527204095:web:2bdfe0589cf42794b3dcca"
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage(function(payload) {
  console.log(
    "[firebase-messaging-sw.js] Message received:",
    payload
  );

  const title =
    payload.notification &&
    payload.notification.title
      ? payload.notification.title
      : "MAKYAMA TRANSPORT";

  const body =
    payload.notification &&
    payload.notification.body
      ? payload.notification.body
      : "Una taarifa mpya kuhusu mzigo wako.";

  self.registration.showNotification(title, {
    body: body,
    icon: "/favicon.ico",
    badge: "/favicon.ico",
    requireInteraction: true,
    data: payload.data || {}
  });
});

self.addEventListener("notificationclick", function(event) {
  event.notification.close();

  event.waitUntil(
    clients.matchAll({
      type: "window",
      includeUncontrolled: true
    }).then(function(clientList) {

      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i];

        if ("focus" in client) {
          return client.focus();
        }
      }

      if (clients.openWindow) {
        return clients.openWindow("/");
      }

    })
  );
});
