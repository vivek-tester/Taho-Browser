const NATIVE_APP = "taho.observation";

let bulkPort = null;
let reconnectTimer = null;
let bulkSequence = 0;

function scheduleReconnect() {
  if (reconnectTimer !== null) {
    return;
  }

  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectBulkPort();
  }, 250);
}

function connectBulkPort() {
  try {
    const port = browser.runtime.connectNative(NATIVE_APP);
    bulkPort = port;

    port.onDisconnect.addListener(() => {
      if (bulkPort === port) {
        bulkPort = null;
      }
      scheduleReconnect();
    });

    port.onMessage.addListener((message) => {
      if (message && message.kind === "ping") {
        sendBulk({ kind: "bulk_heartbeat" });
      }
    });

    sendBulk({ kind: "bulk_heartbeat" });
  } catch (_) {
    bulkPort = null;
    scheduleReconnect();
  }
}

function sendBulk(message) {
  const payload = Object.assign({}, message, { seq: ++bulkSequence });

  if (bulkPort) {
    try {
      bulkPort.postMessage(payload);
      return Promise.resolve();
    } catch (_) {
      bulkPort = null;
      scheduleReconnect();
    }
  }

  return browser.runtime
    .sendNativeMessage(NATIVE_APP, payload)
    .catch(() => undefined);
}

connectBulkPort();

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || message.kind !== "content_announce") {
    return undefined;
  }

  const jsTabId =
    sender && sender.tab && Number.isInteger(sender.tab.id)
      ? sender.tab.id
      : null;

  sendBulk({
    kind: "background_announce",
    token: message.token || null,
    jsTabId,
    topLevel: Boolean(message.topLevel)
  });

  return Promise.resolve({ tabId: jsTabId });
});

const FIXTURE_PATHS = new Set([
  "/tab-a",
  "/tab-b",
  "/tab-p",
  "/api-json",
  "/graphql",
  "/multipart",
  "/form",
  "/redirect-a",
  "/redirect-b",
  "/redirect-p",
  "/beacon",
  "/prefetch.txt",
  "/preload.css",
  "/sw.js",
  "/sse",
  "/ws",
  "/bg",
  "/failed"
]);

function fixtureTag(url) {
  try {
    const parsed = new URL(url);
    if (parsed.hostname !== "127.0.0.1" && parsed.hostname !== "localhost") {
      return null;
    }

    if (FIXTURE_PATHS.has(parsed.pathname)) {
      return parsed.pathname;
    }

    if (/^\/extra-\d+$/.test(parsed.pathname)) {
      return parsed.pathname;
    }
  } catch (_) {
    return null;
  }

  return null;
}

function emitWebRequest(phase, details) {
  sendBulk({
    kind: "web_request",
    phase,
    requestId: details.requestId || null,
    tabId: Number.isInteger(details.tabId) ? details.tabId : null,
    frameId: Number.isInteger(details.frameId) ? details.frameId : null,
    documentId: details.documentId || null,
    resourceType: details.type || null,
    method: details.method || null,
    detailKeys: Object.keys(details || {}).sort().join(",").slice(0, 1024),
    requestBodyPresent: Boolean(details && details.requestBody),
    fixtureTag: fixtureTag(details && details.url)
  });
}

const registered = [];
const failed = [];
const filter = { urls: ["<all_urls>"] };

function register(name, event, extraInfoSpec) {
  if (!event || typeof event.addListener !== "function") {
    failed.push(name);
    return;
  }

  try {
    if (extraInfoSpec) {
      event.addListener((details) => emitWebRequest(name, details), filter, extraInfoSpec);
    } else {
      event.addListener((details) => emitWebRequest(name, details), filter);
    }
    registered.push(name);
  } catch (_) {
    failed.push(name);
  }
}

if (!browser.webRequest) {
  sendBulk({
    kind: "web_request_capability",
    available: false,
    listeners: "",
    failed: "browser.webRequest"
  });
} else {
  register("onBeforeRequest", browser.webRequest.onBeforeRequest, ["requestBody"]);
  register("onBeforeSendHeaders", browser.webRequest.onBeforeSendHeaders);
  register("onSendHeaders", browser.webRequest.onSendHeaders);
  register("onHeadersReceived", browser.webRequest.onHeadersReceived);
  register("onBeforeRedirect", browser.webRequest.onBeforeRedirect);
  register("onResponseStarted", browser.webRequest.onResponseStarted);
  register("onCompleted", browser.webRequest.onCompleted);
  register("onErrorOccurred", browser.webRequest.onErrorOccurred);

  sendBulk({
    kind: "web_request_capability",
    available: registered.length > 0,
    listeners: registered.join(","),
    failed: failed.join(",")
  });
}

setInterval(() => {
  sendBulk({ kind: "bulk_heartbeat" });
}, 3000);
