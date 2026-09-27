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

const registered = [];
const filter = { urls: ["<all_urls>"] };

function register(name, event) {
  if (!event || typeof event.addListener !== "function") {
    return;
  }
  event.addListener((details) => emitWebRequest(name, details), filter);
  registered.push(name);
}

if (!browser.webRequest) {
  sendNative({
    kind: "web_request_capability",
    available: false,
    listeners: ""
  });
} else {
  register("onBeforeRequest", browser.webRequest.onBeforeRequest);
  register("onBeforeSendHeaders", browser.webRequest.onBeforeSendHeaders);
  register("onSendHeaders", browser.webRequest.onSendHeaders);
  register("onHeadersReceived", browser.webRequest.onHeadersReceived);
  register("onBeforeRedirect", browser.webRequest.onBeforeRedirect);
  register("onResponseStarted", browser.webRequest.onResponseStarted);
  register("onCompleted", browser.webRequest.onCompleted);
  register("onErrorOccurred", browser.webRequest.onErrorOccurred);

  sendNative({
    kind: "web_request_capability",
    available: registered.length > 0,
    listeners: registered.join(",")
  });
}
