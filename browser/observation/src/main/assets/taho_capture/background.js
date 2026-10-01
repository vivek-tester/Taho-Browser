const NATIVE_APP = "taho.capture.bulk";
const PROTOCOL_VERSION = 1;
const REQUEST_BODY_CHUNK_BYTES = 160 * 1024;
const MAX_REQUEST_BODY_BYTES = 8 * 1024 * 1024;

let port = null;
let reconnectTimer = null;
let reconnectDelayMs = 250;
let connectionId = null;
let sequence = 0;
let eventCounter = 0;
let captureEnabled = false;

function randomId(prefix) {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
    return prefix + "-" + globalThis.crypto.randomUUID();
  }
  eventCounter += 1;
  return prefix + "-" + Date.now() + "-" + eventCounter;
}

function connect() {
  if (reconnectTimer !== null) return;
  try {
    const next = browser.runtime.connectNative(NATIVE_APP);
    port = next;
    connectionId = randomId("conn");
    sequence = 0;

    next.onMessage.addListener((raw) => {
      let message = raw;
      try {
        if (typeof raw === "string") message = JSON.parse(raw);
      } catch (_) {
        return;
      }
      if (!message || message.type !== "CONTROL_CAPTURE") return;
      const nextEnabled = message.enabled === true;
      const wasEnabled = captureEnabled;
      captureEnabled = nextEnabled;
      if (captureEnabled && !wasEnabled) {
        connectionId = randomId("conn");
        sequence = 0;
        emit("HELLO", { protocol: PROTOCOL_VERSION });
      }
    });

    next.onDisconnect.addListener(() => {
      if (port === next) port = null;
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        reconnectDelayMs = Math.min(5000, reconnectDelayMs * 2);
        connect();
      }, reconnectDelayMs);
    });

    reconnectDelayMs = 250;
  } catch (_) {
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, 250);
  }
}

function emit(type, fields) {
  if (!captureEnabled || !port || !connectionId) return;
  const payload = Object.assign({}, fields || {}, {
    type,
    conn: connectionId,
    seq: ++sequence
  });
  try {
    port.postMessage(JSON.stringify(payload));
  } catch (_) {
    port = null;
  }
}

function base(details) {
  return {
    id: randomId("event"),
    reqId: String(details.requestId || ""),
    tabId: Number.isInteger(details.tabId) ? details.tabId : -1
  };
}

function headersOf(values) {
  if (!Array.isArray(values)) return [];
  return values.slice(0, 200).map((header) => ({
    name: String(header && header.name || "").slice(0, 256),
    value: String(header && header.value || "").slice(0, 16384)
  }));
}

function bytesToBase64(bytes) {
  const view = new Uint8Array(bytes);
  let binary = "";
  const block = 0x8000;
  for (let offset = 0; offset < view.length; offset += block) {
    const slice = view.subarray(offset, Math.min(view.length, offset + block));
    binary += String.fromCharCode.apply(null, slice);
  }
  return btoa(binary);
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || message.type !== "taho:identity:hello") return undefined;
  const extTabId =
    sender && sender.tab && Number.isInteger(sender.tab.id)
      ? sender.tab.id
      : -1;
  return Promise.resolve({ extTabId });
});

const filter = { urls: ["<all_urls>"] };

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    emit("TX_START", Object.assign(base(details), {
      frameId: Number.isInteger(details.frameId) ? details.frameId : -1,
      docId: details.documentId || null,
      url: String(details.url || ""),
      method: String(details.method || ""),
      resourceType: details.type || null,
      ts: Number.isFinite(details.timeStamp) ? details.timeStamp : null
    }));

    const requestBody = details.requestBody || null;
    const formData = requestBody && requestBody.formData;
    if (formData && typeof formData === "object") {
      const form = Object.keys(formData).slice(0, 200).map((name) => ({
        name: String(name).slice(0, 256),
        values: Array.isArray(formData[name])
          ? formData[name].slice(0, 32).map((value) => String(value).slice(0, 16384))
          : []
      }));
      emit("TX_REQ_BODY", Object.assign(base(details), {
        bodyKind: "FORM",
        form
      }));
    } else {
      const raw = requestBody && requestBody.raw;
      if (Array.isArray(raw) && raw.length > 0) {
        try {
          const views = raw
            .filter((part) => part && part.bytes)
            .map((part) => new Uint8Array(part.bytes));
          const observedTotalBytes = views.reduce(
            (sum, view) => sum + view.byteLength,
            0
          );
          if (observedTotalBytes > 0) {
            const capturedBytes = Math.min(
              observedTotalBytes,
              MAX_REQUEST_BODY_BYTES
            );
            const captured = new Uint8Array(capturedBytes);
            let writeOffset = 0;
            for (const view of views) {
              if (writeOffset >= capturedBytes) break;
              const take = Math.min(
                view.byteLength,
                capturedBytes - writeOffset
              );
              captured.set(view.subarray(0, take), writeOffset);
              writeOffset += take;
            }

            const chunkCount = Math.ceil(
              capturedBytes / REQUEST_BODY_CHUNK_BYTES
            );
            for (let chunkIndex = 0; chunkIndex < chunkCount; chunkIndex += 1) {
              const start = chunkIndex * REQUEST_BODY_CHUNK_BYTES;
              const end = Math.min(
                capturedBytes,
                start + REQUEST_BODY_CHUNK_BYTES
              );
              const chunk = captured.subarray(start, end);
              emit("TX_REQ_BODY", Object.assign(base(details), {
                bodyKind: "RAW",
                chunkIndex,
                chunkCount,
                isFinal: chunkIndex === chunkCount - 1,
                observedTotalBytes,
                truncated: observedTotalBytes > MAX_REQUEST_BODY_BYTES,
                base64: bytesToBase64(chunk)
              }));
            }
          }
        } catch (_) {
          // Capability/unsupported bodies remain UNAVAILABLE natively.
        }
      }
    }
  },
  filter,
  ["requestBody"]
);

try {
  browser.webRequest.onBeforeSendHeaders.addListener(
    (details) => {
      emit("TX_REQ_HEADERS", Object.assign(base(details), {
        headers: headersOf(details.requestHeaders)
      }));
    },
    filter,
    ["requestHeaders"]
  );
} catch (_) {
  // Native capability state will remain limited when headers never arrive.
}

try {
  browser.webRequest.onResponseStarted.addListener(
    (details) => {
      emit("TX_RESP_START", Object.assign(base(details), {
        statusCode: details.statusCode,
        statusText: details.statusLine || null,
        headers: headersOf(details.responseHeaders),
        ts: Number.isFinite(details.timeStamp) ? details.timeStamp : null
      }));
    },
    filter,
    ["responseHeaders"]
  );
} catch (_) {
  browser.webRequest.onResponseStarted.addListener(
    (details) => {
      emit("TX_RESP_START", Object.assign(base(details), {
        statusCode: details.statusCode,
        statusText: details.statusLine || null,
        headers: [],
        ts: Number.isFinite(details.timeStamp) ? details.timeStamp : null
      }));
    },
    filter
  );
}

browser.webRequest.onBeforeRedirect.addListener(
  (details) => {
    emit("TX_REDIRECT", Object.assign(base(details), {
      statusCode: details.statusCode,
      redirectUrl: String(details.redirectUrl || ""),
      ts: Number.isFinite(details.timeStamp) ? details.timeStamp : null
    }));
  },
  filter
);

browser.webRequest.onCompleted.addListener(
  (details) => {
    emit("TX_COMPLETE", Object.assign(base(details), {
      ts: Number.isFinite(details.timeStamp) ? details.timeStamp : null
    }));
  },
  filter
);

browser.webRequest.onErrorOccurred.addListener(
  (details) => {
    emit("TX_ERROR", Object.assign(base(details), {
      error: String(details.error || "").slice(0, 512)
    }));
  },
  filter
);

connect();
