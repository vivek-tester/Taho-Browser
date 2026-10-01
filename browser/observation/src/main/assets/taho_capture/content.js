(() => {
  if (window.top !== window) return;

  const MAX_HTML_CHARS = 350000;
  const MAX_TEXT_CHARS = 120000;
  const MAX_LIST_ITEMS = 200;
  let devtoolsPort = null;
  let changeObserver = null;
  let changes = [];
  let recorderActive = false;
  let recorderEvents = [];

  const limitText = (value, max = MAX_TEXT_CHARS) =>
    String(value == null ? "" : value).slice(0, max);

  const safe = (fn, fallback = null) => {
    try {
      return fn();
    } catch (_) {
      return fallback;
    }
  };

  const safeAsync = async (fn, fallback = null) => {
    try {
      return await fn();
    } catch (_) {
      return fallback;
    }
  };

  const simpleValue = (value, depth = 0, seen = new WeakSet()) => {
    if (value == null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      return value;
    }
    if (typeof value === "bigint") return value.toString() + "n";
    if (typeof value === "undefined") return "undefined";
    if (typeof value === "function") return "[Function " + (value.name || "anonymous") + "]";
    if (value instanceof Element) {
      return "<" + value.tagName.toLowerCase() + (value.id ? "#" + value.id : "") + ">";
    }
    if (depth >= 3) return Object.prototype.toString.call(value);
    if (typeof value === "object") {
      if (seen.has(value)) return "[Circular]";
      seen.add(value);
      if (Array.isArray(value)) {
        return value.slice(0, 50).map((item) => simpleValue(item, depth + 1, seen));
      }
      const out = {};
      Object.keys(value).slice(0, 60).forEach((key) => {
        out[key] = safe(() => simpleValue(value[key], depth + 1, seen), "[unreadable]");
      });
      return out;
    }
    return limitText(value);
  };

  const selectorFor = (node) => {
    if (!(node instanceof Element)) return node && node.nodeName ? node.nodeName : "node";
    if (node.id) return "#" + CSS.escape(node.id);
    const parts = [];
    let current = node;
    while (current && current.nodeType === Node.ELEMENT_NODE && parts.length < 5) {
      let part = current.tagName.toLowerCase();
      if (current.classList && current.classList.length) {
        part += "." + Array.from(current.classList).slice(0, 2).map((v) => CSS.escape(v)).join(".");
      }
      const parent = current.parentElement;
      if (parent) {
        const same = Array.from(parent.children).filter((child) => child.tagName === current.tagName);
        if (same.length > 1) part += ":nth-of-type(" + (same.indexOf(current) + 1) + ")";
      }
      parts.unshift(part);
      current = parent;
    }
    return parts.join(" > ");
  };

  const collectElements = () => ({
    url: location.href,
    title: document.title,
    readyState: document.readyState,
    nodeCount: document.getElementsByTagName("*").length,
    elementCount: document.querySelectorAll("*").length,
    formCount: document.forms.length,
    imageCount: document.images.length,
    linkCount: document.links.length,
    html: limitText(document.documentElement ? document.documentElement.outerHTML : "", MAX_HTML_CHARS)
  });

  const collectSources = () => ({
    url: location.href,
    scripts: Array.from(document.scripts).slice(0, MAX_LIST_ITEMS).map((script, index) => ({
      index,
      src: script.src || null,
      type: script.type || "text/javascript",
      async: !!script.async,
      defer: !!script.defer,
      inlinePreview: script.src ? null : limitText(script.textContent || "", 3000)
    })),
    stylesheets: Array.from(document.styleSheets).slice(0, MAX_LIST_ITEMS).map((sheet, index) => ({
      index,
      href: sheet.href || null,
      disabled: !!sheet.disabled,
      media: safe(() => sheet.media.mediaText, "")
    }))
  });

  const collectPerformance = () => {
    const nav = performance.getEntriesByType("navigation")[0];
    const paints = performance.getEntriesByType("paint").map((entry) => ({
      name: entry.name,
      startTime: Math.round(entry.startTime * 100) / 100
    }));
    const resources = performance.getEntriesByType("resource").slice(-MAX_LIST_ITEMS).map((entry) => ({
      name: limitText(entry.name, 500),
      initiatorType: entry.initiatorType || null,
      startTime: Math.round(entry.startTime * 100) / 100,
      duration: Math.round(entry.duration * 100) / 100,
      transferSize: Number(entry.transferSize || 0),
      encodedBodySize: Number(entry.encodedBodySize || 0),
      decodedBodySize: Number(entry.decodedBodySize || 0)
    }));
    return {
      timeOrigin: performance.timeOrigin,
      navigation: nav ? {
        type: nav.type,
        duration: nav.duration,
        domInteractive: nav.domInteractive,
        domContentLoadedEventEnd: nav.domContentLoadedEventEnd,
        loadEventEnd: nav.loadEventEnd,
        transferSize: nav.transferSize,
        encodedBodySize: nav.encodedBodySize,
        decodedBodySize: nav.decodedBodySize
      } : null,
      paints,
      resources
    };
  };

  const collectMemory = () => {
    const memory = performance.memory || null;
    return {
      apiAvailable: !!memory,
      jsHeapSizeLimit: memory ? Number(memory.jsHeapSizeLimit || 0) : null,
      totalJSHeapSize: memory ? Number(memory.totalJSHeapSize || 0) : null,
      usedJSHeapSize: memory ? Number(memory.usedJSHeapSize || 0) : null,
      domNodes: document.getElementsByTagName("*").length,
      images: document.images.length,
      scripts: document.scripts.length,
      stylesheets: document.styleSheets.length,
      note: memory ? null : "Gecko does not expose performance.memory on this page; DOM counters remain live."
    };
  };

  const collectApplication = async () => {
    const storageObject = (storage) => {
      const out = {};
      for (let i = 0; i < Math.min(storage.length, 100); i += 1) {
        const key = storage.key(i);
        out[key] = limitText(storage.getItem(key), 4000);
      }
      return out;
    };

    const cacheNames = await safeAsync(async () => Array.from(await caches.keys()).slice(0, 100), []);
    const registrations = await safeAsync(
      async () => (await navigator.serviceWorker.getRegistrations()).slice(0, 50).map((reg) => ({
        scope: reg.scope,
        active: reg.active ? reg.active.state : null,
        waiting: reg.waiting ? reg.waiting.state : null,
        installing: reg.installing ? reg.installing.state : null
      })),
      []
    );
    const databases = await safeAsync(
      async () => typeof indexedDB.databases === "function"
        ? (await indexedDB.databases()).slice(0, 100).map((db) => ({ name: db.name || null, version: db.version || null }))
        : [],
      []
    );

    return {
      localStorage: safe(() => storageObject(localStorage), {}),
      sessionStorage: safe(() => storageObject(sessionStorage), {}),
      cookieNames: safe(
        () => document.cookie.split(";").map((item) => item.split("=")[0].trim()).filter(Boolean).slice(0, 100),
        []
      ),
      serviceWorker: {
        controlled: !!navigator.serviceWorker.controller,
        controllerScript: navigator.serviceWorker.controller ? navigator.serviceWorker.controller.scriptURL : null,
        registrations
      },
      cacheStorage: cacheNames,
      indexedDB: databases,
      manifest: document.querySelector('link[rel="manifest"]')?.href || null
    };
  };

  const collectSecurity = () => {
    const mixed = location.protocol === "https:"
      ? performance.getEntriesByType("resource")
          .map((entry) => entry.name)
          .filter((name) => /^http:///i.test(name))
          .slice(0, 100)
      : [];
    return {
      url: location.href,
      protocol: location.protocol,
      secureContext: window.isSecureContext,
      crossOriginIsolated: window.crossOriginIsolated,
      referrerPolicy: document.referrerPolicy || null,
      referrer: document.referrer || null,
      mixedContentResources: mixed,
      cspMeta: Array.from(document.querySelectorAll('meta[http-equiv="Content-Security-Policy"]'))
        .map((node) => limitText(node.content, 4000))
    };
  };

  const collectAudit = () => {
    const images = Array.from(document.images);
    const inputs = Array.from(document.querySelectorAll("input,select,textarea"));
    const headings = Array.from(document.querySelectorAll("h1,h2,h3,h4,h5,h6"));
    const missingAlt = images.filter((img) => !img.hasAttribute("alt")).length;
    const unlabeled = inputs.filter((input) => {
      if (input.type === "hidden") return false;
      if (input.labels && input.labels.length) return false;
      return !input.getAttribute("aria-label") && !input.getAttribute("aria-labelledby");
    }).length;
    const nav = performance.getEntriesByType("navigation")[0];
    const checks = [
      { name: "Document title", pass: document.title.trim().length > 0 },
      { name: "HTML language", pass: !!document.documentElement.lang },
      { name: "Viewport meta", pass: !!document.querySelector('meta[name="viewport"]') },
      { name: "Secure context", pass: window.isSecureContext },
      { name: "Images have alt text", pass: missingAlt === 0, detail: missingAlt + " missing" },
      { name: "Form controls have labels", pass: unlabeled === 0, detail: unlabeled + " unlabeled" },
      { name: "Single primary heading", pass: document.querySelectorAll("h1").length === 1 }
    ];
    return {
      engine: "Taho on-device audit",
      chromiumLighthouseProtocol: false,
      checks,
      summary: {
        passed: checks.filter((item) => item.pass).length,
        total: checks.length,
        headings: headings.length,
        images: images.length,
        inputs: inputs.length,
        navigationDurationMs: nav ? Math.round(nav.duration) : null
      }
    };
  };

  const collectIssues = () => {
    const duplicateIds = [];
    const seen = new Set();
    document.querySelectorAll("[id]").forEach((node) => {
      if (seen.has(node.id)) duplicateIds.push(node.id);
      seen.add(node.id);
    });
    const brokenImages = Array.from(document.images)
      .filter((img) => img.complete && img.naturalWidth === 0)
      .map((img) => img.currentSrc || img.src)
      .slice(0, 100);
    const insecureForms = Array.from(document.forms)
      .filter((form) => location.protocol === "https:" && /^http:///i.test(form.action))
      .map((form) => form.action)
      .slice(0, 100);
    const missingAlt = Array.from(document.images)
      .filter((img) => !img.hasAttribute("alt"))
      .map(selectorFor)
      .slice(0, 100);
    return {
      duplicateIds: Array.from(new Set(duplicateIds)).slice(0, 100),
      brokenImages,
      insecureForms,
      imagesMissingAlt: missingAlt,
      consoleErrorsAvailable: false,
      note: "This on-device issue scan reports DOM/security problems visible to the page inspection context."
    };
  };

  const collectRendering = () => ({
    viewport: {
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      outerWidth: window.outerWidth,
      outerHeight: window.outerHeight,
      devicePixelRatio: window.devicePixelRatio,
      visualViewport: window.visualViewport ? {
        width: window.visualViewport.width,
        height: window.visualViewport.height,
        scale: window.visualViewport.scale,
        offsetLeft: window.visualViewport.offsetLeft,
        offsetTop: window.visualViewport.offsetTop
      } : null
    },
    scroll: { x: window.scrollX, y: window.scrollY },
    media: {
      darkMode: matchMedia("(prefers-color-scheme: dark)").matches,
      reducedMotion: matchMedia("(prefers-reduced-motion: reduce)").matches,
      forcedColors: matchMedia("(forced-colors: active)").matches,
      hover: matchMedia("(hover: hover)").matches
    },
    animationCount: safe(() => document.getAnimations().length, 0)
  });

  const collectSensors = () => ({
    orientation: screen.orientation ? {
      type: screen.orientation.type,
      angle: screen.orientation.angle
    } : null,
    screen: {
      width: screen.width,
      height: screen.height,
      availWidth: screen.availWidth,
      availHeight: screen.availHeight,
      colorDepth: screen.colorDepth,
      pixelDepth: screen.pixelDepth
    },
    touch: {
      maxTouchPoints: navigator.maxTouchPoints || 0,
      touchEventApi: "ontouchstart" in window
    },
    device: {
      hardwareConcurrency: navigator.hardwareConcurrency || null,
      deviceMemory: navigator.deviceMemory || null,
      platform: navigator.platform || null
    },
    apis: {
      geolocation: !!navigator.geolocation,
      deviceOrientation: "DeviceOrientationEvent" in window,
      deviceMotion: "DeviceMotionEvent" in window
    },
    emulationNote: "Live sensor capability is shown here. Synthetic sensor injection is not exposed by the current GeckoView embedding API."
  });

  const collectCoverage = () => {
    let totalRules = 0;
    let usedRules = 0;
    let inaccessibleSheets = 0;
    const samples = [];

    Array.from(document.styleSheets).forEach((sheet) => {
      let rules;
      try {
        rules = Array.from(sheet.cssRules || []);
      } catch (_) {
        inaccessibleSheets += 1;
        return;
      }
      rules.slice(0, 1000).forEach((rule) => {
        if (!rule.selectorText) return;
        totalRules += 1;
        const used = safe(() => !!document.querySelector(rule.selectorText), false);
        if (used) usedRules += 1;
        if (samples.length < 100) {
          samples.push({ selector: limitText(rule.selectorText, 500), used });
        }
      });
    });

    return {
      css: { totalRules, usedRules, unusedRules: Math.max(0, totalRules - usedRules), inaccessibleSheets, samples },
      javascript: {
        loadedScripts: document.scripts.length,
        executionCoverageAvailable: false,
        note: "GeckoView does not expose JavaScript execution counters to this embedded page inspector."
      }
    };
  };

  const ensureChanges = () => {
    if (changeObserver) return;
    changeObserver = new MutationObserver((records) => {
      records.forEach((record) => {
        changes.push({
          type: record.type,
          target: selectorFor(record.target),
          attributeName: record.attributeName || null,
          added: record.addedNodes ? record.addedNodes.length : 0,
          removed: record.removedNodes ? record.removedNodes.length : 0,
          time: Date.now()
        });
      });
      if (changes.length > MAX_LIST_ITEMS) changes = changes.slice(-MAX_LIST_ITEMS);
    });
    changeObserver.observe(document.documentElement || document, {
      subtree: true,
      attributes: true,
      childList: true,
      characterData: true
    });
  };

  const recordEvent = (event) => {
    if (!recorderActive) return;
    const target = event.target;
    const entry = {
      type: event.type,
      target: selectorFor(target),
      time: Date.now()
    };
    if (event.type === "input" || event.type === "change") {
      const element = target;
      if (element && typeof element.value === "string") {
        entry.value = element.type === "password" ? "[password]" : limitText(element.value, 1000);
      }
    }
    recorderEvents.push(entry);
    if (recorderEvents.length > MAX_LIST_ITEMS) recorderEvents = recorderEvents.slice(-MAX_LIST_ITEMS);
  };

  ["click", "input", "change", "submit"].forEach((type) => {
    document.addEventListener(type, recordEvent, true);
  });

  const collectRecorder = (argument) => {
    if (argument === "clear") recorderEvents = [];
    if (argument === "stop") recorderActive = false;
    if (argument === "start" || !argument) recorderActive = true;
    return {
      active: recorderActive,
      events: recorderEvents.slice(-MAX_LIST_ITEMS)
    };
  };

  const collectAnimations = () => safe(
    () => document.getAnimations().slice(0, MAX_LIST_ITEMS).map((animation, index) => ({
      index,
      playState: animation.playState,
      playbackRate: animation.playbackRate,
      currentTime: animation.currentTime,
      startTime: animation.startTime,
      id: animation.id || null,
      target: animation.effect && animation.effect.target ? selectorFor(animation.effect.target) : null,
      timing: animation.effect && typeof animation.effect.getTiming === "function"
        ? simpleValue(animation.effect.getTiming())
        : null
    })),
    []
  );

  const executeConsole = (code) => {
    const source = String(code || "");
    if (!source.trim()) return { type: "undefined", result: "undefined" };
    try {
      const value = (0, eval)(source);
      return {
        type: value === null ? "null" : typeof value,
        result: simpleValue(value),
        context: "WebExtension page inspection world"
      };
    } catch (error) {
      return {
        type: "error",
        error: error && error.stack ? limitText(error.stack, 16000) : limitText(error, 16000),
        context: "WebExtension page inspection world"
      };
    }
  };

  const handleCommand = async (command, argument) => {
    switch (command) {
      case "ELEMENTS": return collectElements();
      case "CONSOLE_INFO": return { url: location.href, title: document.title, context: "WebExtension page inspection world" };
      case "CONSOLE_EVAL": return executeConsole(argument);
      case "SOURCES": return collectSources();
      case "PERFORMANCE": return collectPerformance();
      case "MEMORY": return collectMemory();
      case "APPLICATION": return await collectApplication();
      case "SECURITY": return collectSecurity();
      case "LIGHTHOUSE": return collectAudit();
      case "RECORDER": return collectRecorder(argument);
      case "ISSUES": return collectIssues();
      case "RENDERING": return collectRendering();
      case "SENSORS": return collectSensors();
      case "COVERAGE": return collectCoverage();
      case "CHANGES":
        ensureChanges();
        if (argument === "clear") changes = [];
        return { observing: true, changes: changes.slice(-MAX_LIST_ITEMS) };
      case "ANIMATIONS": return { animations: collectAnimations() };
      default: throw new Error("Unsupported developer-tools command");
    }
  };

  const connectDevTools = () => {
    try {
      const port = browser.runtime.connectNative("taho.devtools");
      devtoolsPort = port;

      port.onMessage.addListener((raw) => {
        let request;
        try {
          request = typeof raw === "string" ? JSON.parse(raw) : raw;
        } catch (_) {
          return;
        }
        if (!request || request.type !== "DEVTOOLS_COMMAND" || !request.id) return;

        Promise.resolve(handleCommand(request.command, request.argument))
          .then((payload) => {
            if (devtoolsPort !== port) return;
            port.postMessage(JSON.stringify({
              type: "DEVTOOLS_RESPONSE",
              id: request.id,
              payload
            }));
          })
          .catch((error) => {
            if (devtoolsPort !== port) return;
            port.postMessage(JSON.stringify({
              type: "DEVTOOLS_RESPONSE",
              id: request.id,
              error: error && error.message ? limitText(error.message, 4000) : "Inspector command failed"
            }));
          });
      });

      port.onDisconnect.addListener(() => {
        if (devtoolsPort === port) devtoolsPort = null;
      });

      port.postMessage(JSON.stringify({
        type: "DEVTOOLS_READY",
        url: location.href,
        readyState: document.readyState
      }));
    } catch (_) {
      devtoolsPort = null;
    }
  };

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

  connectDevTools();
})();
