(() => {
  if (window.top !== window) return;

  browser.runtime.sendMessage({ type: "taho:identity:hello" })
    .then((reply) => {
      if (!reply || !Number.isInteger(reply.extTabId) || reply.extTabId < 0) {
        return;
      }

      const message = JSON.stringify({
        type: "TAB_REGISTER",
        extTabId: reply.extTabId,
        url: location.href,
        readyState: document.readyState
      });

      return browser.runtime.sendNativeMessage("taho.identity", message);
    })
    .catch(() => undefined);
})();
