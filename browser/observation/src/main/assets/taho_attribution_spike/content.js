(() => {
  const token =
    (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function")
      ? globalThis.crypto.randomUUID()
      : String(Date.now()) + "-" + Math.random().toString(16).slice(2);

  const topLevel = window.top === window;

  browser.runtime.sendMessage({
    kind: "content_announce",
    token,
    topLevel
  }).then((reply) => {
    return browser.runtime.sendNativeMessage("taho.observation", {
      kind: "session_announce",
      token,
      topLevel,
      jsTabId: reply && Number.isInteger(reply.tabId) ? reply.tabId : null
    });
  }).catch(() => undefined);
})();
