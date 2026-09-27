const NATIVE_APP = "taho.observation";

function sendNative(message) {
  return browser.runtime.sendNativeMessage(NATIVE_APP, message).catch(() => undefined);
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || message.kind !== "content_announce") {
    return undefined;
  }

  const jsTabId =
    sender && sender.tab && Number.isInteger(sender.tab.id)
      ? sender.tab.id
      : null;

  sendNative({
    kind: "background_announce",
    token: message.token || null,
    jsTabId,
    topLevel: Boolean(message.topLevel)
  });

  return Promise.resolve({ tabId: jsTabId });
});

function emitWebRequest(phase, details) {
  sendNative({
    kind: "web_request",
    phase,
    requestId: details.requestId || null,
    tabId: Number.isInteger(details.tabId) ? details.tabId : null,
    frameId: Number.isInteger(details.frameId) ? details.frameId : null,
    documentId: details.documentId || null,
    resourceType: details.type || null,
    method: details.method || null
  });
}

const filter = { urls: ["<all_urls>"] };

browser.webRequest.onBeforeRequest.addListener(
  (details) => emitWebRequest("onBeforeRequest", details), filter
);
browser.webRequest.onBeforeSendHeaders.addListener(
  (details) => emitWebRequest("onBeforeSendHeaders", details), filter
);
browser.webRequest.onSendHeaders.addListener(
  (details) => emitWebRequest("onSendHeaders", details), filter
);
browser.webRequest.onHeadersReceived.addListener(
  (details) => emitWebRequest("onHeadersReceived", details), filter
);
browser.webRequest.onBeforeRedirect.addListener(
  (details) => emitWebRequest("onBeforeRedirect", details), filter
);
browser.webRequest.onResponseStarted.addListener(
  (details) => emitWebRequest("onResponseStarted", details), filter
);
browser.webRequest.onCompleted.addListener(
  (details) => emitWebRequest("onCompleted", details), filter
);
browser.webRequest.onErrorOccurred.addListener(
  (details) => emitWebRequest("onErrorOccurred", details), filter
);
