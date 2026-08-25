(function () {
  var PHONE_WIDTH = 412;

  function forceMobileMetrics() {
    try {
      Object.defineProperty(window, "innerWidth", {
        configurable: true,
        get: function () {
          return PHONE_WIDTH;
        },
      });
      Object.defineProperty(window, "outerWidth", {
        configurable: true,
        get: function () {
          return PHONE_WIDTH;
        },
      });
      Object.defineProperty(document.documentElement, "clientWidth", {
        configurable: true,
        get: function () {
          return PHONE_WIDTH;
        },
      });
    } catch (e) {}
    if (window.__ytmMatchMedia) return;
    window.__ytmMatchMedia = true;
    var orig = window.matchMedia.bind(window);
    window.matchMedia = function (query) {
      var result = orig(query);
      var min = /min-width:\s*(\d+)/i.exec(query);
      var max = /max-width:\s*(\d+)/i.exec(query);
      var matches = result.matches;
      if (min && parseInt(min[1], 10) >= 600) matches = false;
      if (max && parseInt(max[1], 10) <= 599) matches = true;
      return {
        matches: matches,
        media: query,
        onchange: null,
        addListener: function () {},
        removeListener: function () {},
        addEventListener: function () {},
        removeEventListener: function () {},
        dispatchEvent: function () {
          return false;
        },
      };
    };
  }

  function setViewport() {
    var head = document.head || document.documentElement;
    if (!head) return;
    var meta = document.querySelector("meta[name='viewport']");
    if (!meta) {
      meta = document.createElement("meta");
      meta.setAttribute("name", "viewport");
      head.appendChild(meta);
    }
    meta.setAttribute(
      "content",
      "width=" + PHONE_WIDTH + ", initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover",
    );
  }

  forceMobileMetrics();
  setViewport();
  if (window.__ytmShell) return;
  window.__ytmShell = true;
  window.__ytmWasPlaying = false;
  window.__ytmLastMeta = { title: "", artist: "", artwork: "" };
  window.__ytmNativeHeaderPx = 0;
  window.__ytmPlayerExpanded = false;
  window.__ytmWantExpanded = false;
  window.__ytmHeaderMode = "home";

  function prune(obj) {
    if (!obj || typeof obj !== "object") return obj;
    if (Array.isArray(obj.adPlacements)) obj.adPlacements = [];
    if (Array.isArray(obj.playerAds)) obj.playerAds = [];
    if (Array.isArray(obj.adSlots)) obj.adSlots = [];
    if (obj.adBreakHeartbeatParams) delete obj.adBreakHeartbeatParams;
    if (obj.playerResponse) prune(obj.playerResponse);
    if (obj.auxiliaryUi && obj.auxiliaryUi.messageRenderers) {
      obj.auxiliaryUi.messageRenderers = [];
    }
    return obj;
  }

  try {
    var origParse = JSON.parse;
    JSON.parse = function () {
      var value = origParse.apply(this, arguments);
      try {
        return prune(value);
      } catch (e) {
        return value;
      }
    };
  } catch (e) {}

  var DOCUMENT_CSS = [
    "html, body, ytmusic-app { background: #000000 !important; }",
    "ytmusic-mealbar-promo-renderer, ytmusic-you-there-renderer, ytmusic-app-promo-renderer,",
    "tp-yt-app-drawer, #guide-wrapper, ytmusic-guide-renderer,",
    "a[href*='play.google.com/store/apps'], a[aria-label='Open App'], a[aria-label='Abrir app'] {",
    "  display: none !important;",
    "}",
    "#player-bar-background { opacity: 0 !important; pointer-events: none !important; }",
    "ytmusic-app-layout {",
    "  --ytmusic-guide-width: 0px !important;",
    "  padding-top: 0 !important;",
    "  padding-bottom: 72px !important;",
    "}",
    "ytmusic-player-bar {",
    "  position: fixed !important; left: 0 !important; right: 0 !important;",
    "  top: auto !important; bottom: 0 !important;",
    "  height: auto !important; min-height: 64px !important; max-height: none !important;",
    "  overflow: visible !important; opacity: 0 !important; visibility: hidden !important;",
    "  pointer-events: none !important; z-index: 1 !important;",
    "}",
    "ytmusic-player-page {",
    "  max-width: 100% !important; left: 0 !important; right: 0 !important;",
    "}",
    ".previous-items-button, .next-items-button, #left-arrow, #right-arrow {",
    "  display: none !important;",
    "}",
  ].join("\n");

  var PLAYER_BAR_CSS = [
    ":host { overflow: visible !important; max-height: none !important; }",
    ".middle-controls, .content-info-wrapper, .thumbnail-image-wrapper, .left-controls {",
    "  display: flex !important; visibility: visible !important; opacity: 1 !important;",
    "  overflow: visible !important; height: auto !important; max-height: none !important;",
    "}",
    ".volume, .volume-slider, tp-yt-paper-slider.volume-slider { display: none !important; }",
  ].join("\n");

  function navCss() {
    var hideSearch = window.__ytmHeaderMode !== "search";
    var searchRule = hideSearch
      ? "ytmusic-search-box, #search-icon, .search-icon, tp-yt-iron-icon[icon='search'] { display: none !important; }"
      : "ytmusic-search-box, #search-icon, .search-icon { display: flex !important; visibility: visible !important; }";
    return [
      "#guide-button, .menu-button, #menu-button, yt-icon-button#guide-button { display: none !important; }",
      "#open-app, #open-app-button, a[href*='play.google.com'],",
      "a[aria-label='Open App'], a[aria-label='Abrir app'] { display: none !important; }",
      "#logo, ytmusic-logo, .logo, .ytmusic-logo { display: none !important; }",
      "#guide-wrapper { display: none !important; }",
      searchRule,
    ].join("\n");
  }

  var CAROUSEL_CSS = [
    ".previous-items-button, .next-items-button, #left-arrow, #right-arrow {",
    "  display: none !important;",
    "}",
  ].join("\n");

  function putStyle(root, cssText) {
    if (!root || !cssText) return;
    var existing =
      (root.getElementById && root.getElementById("ytm-shell-css")) ||
      (root.querySelector && root.querySelector("#ytm-shell-css"));
    if (existing) {
      existing.textContent = cssText;
      return;
    }
    var host = root.head || root;
    var css = document.createElement("style");
    css.id = "ytm-shell-css";
    css.textContent = cssText;
    host.appendChild(css);
  }

  function hideAll(root, selector) {
    if (!root || !root.querySelectorAll) return;
    var nodes = root.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].style.setProperty("display", "none", "important");
    }
  }

  function walkShadows(root, visit, depth) {
    if (!root || depth > 8) return;
    visit(root);
    var nodes = root.querySelectorAll ? root.querySelectorAll("*") : [];
    for (var i = 0; i < nodes.length && i < 80; i++) {
      if (nodes[i].shadowRoot) walkShadows(nodes[i].shadowRoot, visit, depth + 1);
    }
  }

  function queryInBar(selector) {
    var bar = document.querySelector("ytmusic-player-bar");
    if (!bar) return null;
    if (bar.shadowRoot) {
      var hit = bar.shadowRoot.querySelector(selector);
      if (hit) return hit;
    }
    var found = null;
    walkShadows(bar, function (root) {
      if (found || !root.querySelector) return;
      var el = root.querySelector(selector);
      if (el) found = el;
    }, 0);
    return found;
  }

  function hideOpenApp(root) {
    var els = root.querySelectorAll(
      "a, button, yt-button-shape, yt-button-renderer, tp-yt-paper-button",
    );
    for (var i = 0; i < els.length && i < 40; i++) {
      var t = ((els[i].getAttribute("aria-label") || "") + " " + (els[i].textContent || ""))
        .replace(/\s+/g, " ")
        .trim();
      if (/open app|abrir app|get the app|abrir la app/i.test(t)) {
        var wrap = els[i].closest("yt-button-shape, yt-button-renderer, a, button") || els[i];
        wrap.style.setProperty("display", "none", "important");
      }
    }
  }

  function hideWebChrome() {
    var nav = document.querySelector("ytmusic-nav-bar");
    if (!nav) return;
    var search = window.__ytmHeaderMode === "search";
    walkShadows(nav, function (root) {
      hideAll(root, "#guide-button, #menu-button, .menu-button, tp-yt-iron-icon#menu");
      hideAll(root, "#logo, ytmusic-logo, .logo");
      if (!search) {
        hideAll(root, "ytmusic-search-box, #search-button, #search-icon, .search-box");
        hideAll(root, "#avatar-btn, ytmusic-settings-button, ytmusic-notification-toggle-button");
      }
      hideOpenApp(root);
    }, 0);
  }

  function pierceShadows() {
    putStyle(document, DOCUMENT_CSS);
    var nav = document.querySelector("ytmusic-nav-bar");
    if (nav) {
      if (nav.shadowRoot) putStyle(nav.shadowRoot, navCss());
      hideWebChrome();
    }
    var bar = document.querySelector("ytmusic-player-bar");
    if (bar && bar.shadowRoot) putStyle(bar.shadowRoot, PLAYER_BAR_CSS);
    var shelves = document.querySelectorAll(
      "ytmusic-carousel-shelf-renderer, ytmusic-carousel",
    );
    for (var i = 0; i < shelves.length && i < 12; i++) {
      if (shelves[i].shadowRoot) putStyle(shelves[i].shadowRoot, CAROUSEL_CSS);
      walkShadows(shelves[i], function (root) {
        hideAll(root, ".previous-items-button, .next-items-button, #left-arrow, #right-arrow");
      }, 0);
    }
  }

  function injectCss() {
    setViewport();
    pierceShadows();
  }

  function isPlayerExpanded() {
    if (window.__ytmWantExpanded) return true;
    var layout = document.querySelector("ytmusic-app-layout");
    if (!layout) return window.__ytmPlayerExpanded;
    return (
      layout.hasAttribute("player-page-open") ||
      layout.getAttribute("player-page-open") === "" ||
      !!layout.playerPageOpen_
    );
  }

  function notifyPlayerExpanded(expanded) {
    if (window.__ytmPlayerExpanded === expanded) return;
    window.__ytmPlayerExpanded = expanded;
    try {
      if (window.AndroidYtm && window.AndroidYtm.onPlayerExpanded) {
        window.AndroidYtm.onPlayerExpanded(expanded);
      }
    } catch (e) {}
  }

  function collapseDesktopPlayer() {
    if (window.__ytmWantExpanded || isPlayerExpanded()) return;
    var layout = document.querySelector("ytmusic-app-layout");
    if (layout) {
      layout.removeAttribute("player-page-open");
      try {
        layout.playerPageOpen_ = false;
      } catch (e) {}
    }
    document.documentElement.removeAttribute("player-page-open");
    if (document.body) document.body.removeAttribute("player-page-open");
  }

  function showPlayerPage(page) {
    if (!page) return;
    page.removeAttribute("hidden");
    page.style.removeProperty("display");
    page.style.removeProperty("visibility");
    page.style.removeProperty("height");
    page.style.removeProperty("max-height");
    page.style.removeProperty("pointer-events");
  }

  function expandPlayer() {
    window.__ytmWantExpanded = true;
    var expandBtn =
      queryInBar(".expand-button") ||
      queryInBar("#expand-button") ||
      queryInBar('[aria-label="Expand"]') ||
      queryInBar('[aria-label*="Expand"]') ||
      queryInBar('[aria-label*="Expandir"]');
    if (expandBtn) {
      try {
        expandBtn.style.pointerEvents = "auto";
      } catch (e) {}
      expandBtn.click();
    }
    var layout = document.querySelector("ytmusic-app-layout");
    if (layout) {
      layout.setAttribute("player-page-open", "");
      try {
        layout.playerPageOpen_ = true;
      } catch (e) {}
    }
    document.documentElement.setAttribute("player-page-open", "");
    showPlayerPage(document.querySelector("ytmusic-player-page"));
    notifyPlayerExpanded(true);
    setTimeout(function () {
      showPlayerPage(document.querySelector("ytmusic-player-page"));
      notifyPlayerExpanded(true);
    }, 250);
    return true;
  }

  function collapsePlayer() {
    window.__ytmWantExpanded = false;
    var page = document.querySelector("ytmusic-player-page");
    if (page) {
      walkShadows(page, function (root) {
        var back =
          root.querySelector &&
          (root.querySelector(".back-button") ||
            root.querySelector("#back-button") ||
            root.querySelector('[aria-label="Close"]') ||
            root.querySelector('[aria-label*="Minimize"]') ||
            root.querySelector('[aria-label*="Cerrar"]'));
        if (back) back.click();
      }, 0);
    }
    var layout = document.querySelector("ytmusic-app-layout");
    if (layout) {
      layout.removeAttribute("player-page-open");
      try {
        layout.playerPageOpen_ = false;
      } catch (e) {}
    }
    document.documentElement.removeAttribute("player-page-open");
    if (document.body) document.body.removeAttribute("player-page-open");
    notifyPlayerExpanded(false);
  }

  function clickFirst(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var el = document.querySelector(selectors[i]);
      if (el) {
        el.click();
        return true;
      }
    }
    return false;
  }

  function clickNavIcon(selectors) {
    var nav = document.querySelector("ytmusic-nav-bar");
    if (!nav) return clickFirst(selectors);
    var found = false;
    walkShadows(nav, function (root) {
      if (found) return;
      for (var i = 0; i < selectors.length; i++) {
        var el = root.querySelector && root.querySelector(selectors[i]);
        if (el) {
          el.click();
          found = true;
          return;
        }
      }
    }, 0);
    if (!found) clickFirst(selectors);
  }

  function isAdPlaying() {
    return !!document.querySelector(
      ".ad-showing, .ytp-ad-player-overlay, ytmusic-player[playback-video-mode_='AD']",
    );
  }

  function normalizePlayback() {
    var video = document.querySelector("video");
    if (!video || isAdPlaying()) return;
    try {
      if (video.muted) video.muted = false;
      if (video.playbackRate !== 1) video.playbackRate = 1;
    } catch (e) {}
  }

  function skipAds() {
    clickFirst([
      ".ytp-ad-skip-button",
      ".ytp-ad-skip-button-modern",
      ".ytp-skip-ad-button",
      "button.ytp-ad-skip-button-container",
      ".ytp-ad-overlay-close-button",
    ]);
    var ad = isAdPlaying();
    var video = document.querySelector("video");
    if (ad && video) {
      try {
        if (video.duration && isFinite(video.duration)) video.currentTime = video.duration;
        video.muted = true;
        video.playbackRate = 16;
      } catch (e) {}
    } else {
      normalizePlayback();
    }
    var meal = document.querySelector(
      "ytmusic-mealbar-promo-renderer, ytmusic-you-there-renderer",
    );
    if (meal) {
      var confirm = meal.querySelector("#confirm-button, button");
      if (confirm) confirm.click();
      meal.remove();
    }
  }

  function textOf(el) {
    if (!el) return "";
    return (el.getAttribute("title") || el.title || el.textContent || "")
      .replace(/\s+/g, " ")
      .trim();
  }

  function artUrl(img) {
    if (!img) return "";
    var src =
      img.currentSrc ||
      img.src ||
      img.getAttribute("src") ||
      (img.srcset && img.srcset.split(" ").pop()) ||
      "";
    if (!src || src.indexOf("data:") === 0) return "";
    if (img.naturalWidth && img.naturalWidth < 8) return "";
    return String(src)
      .replace(/=w\d+-h\d+/g, "=w226-h226")
      .replace(/=s\d+/g, "=s226");
  }

  function looksLikePageTitle(title) {
    return /^(liked music|library|youtube music|yt music|new episodes)$/i.test(title);
  }

  function metadata() {
    var video = document.querySelector("video");
    var titleEl = queryInBar("yt-formatted-string.title") || queryInBar(".title.ytmusic-player-bar");
    var byEl = queryInBar("yt-formatted-string.byline") || queryInBar(".byline");
    var art =
      queryInBar(".thumbnail-image-wrapper img") ||
      queryInBar("yt-img-shadow img") ||
      queryInBar("img.image");
    if (art) {
      try {
        art.loading = "eager";
        art.referrerPolicy = "no-referrer-when-downgrade";
      } catch (e) {}
    }
    var playing = !!(video && !video.paused && !video.ended);
    if (playing) window.__ytmWasPlaying = true;
    if (video && video.ended) window.__ytmWasPlaying = false;
    var title = textOf(titleEl);
    var artist = textOf(byEl);
    var artwork = artUrl(art) || (video && video.poster) || "";
    var ms = navigator.mediaSession && navigator.mediaSession.metadata;
    if (ms) {
      if ((!title || looksLikePageTitle(title)) && ms.title) title = String(ms.title).trim();
      if (!artist && ms.artist) artist = String(ms.artist).trim();
      if ((!artwork || looksLikePageTitle(title)) && ms.artwork && ms.artwork.length) {
        var msArt = ms.artwork[ms.artwork.length - 1].src || "";
        if (msArt) artwork = msArt;
      }
    }
    if (looksLikePageTitle(title)) title = (ms && ms.title) || window.__ytmLastMeta.title || "";
    if (!title) {
      var docTitle = (document.title || "").replace(/\s*[-–]\s*YouTube Music\s*$/i, "").trim();
      if (docTitle && !/^youtube music$/i.test(docTitle) && !looksLikePageTitle(docTitle)) {
        title = docTitle;
      }
    }
    if (!artist && title.indexOf(" • ") !== -1) {
      var bits = title.split(" • ");
      title = bits[0].trim();
      artist = bits.slice(1).join(" • ").trim();
    }
    var last = window.__ytmLastMeta;
    if (!title && last.title) title = last.title;
    if (!artist && last.artist) artist = last.artist;
    if (!artwork && last.artwork) artwork = last.artwork;
    if (title && !looksLikePageTitle(title)) last.title = title;
    if (artist) last.artist = artist;
    if (artwork) last.artwork = artwork;
    if (window.__ytmWantExpanded) {
      showPlayerPage(document.querySelector("ytmusic-player-page"));
      notifyPlayerExpanded(true);
    } else {
      notifyPlayerExpanded(isPlayerExpanded());
    }
    return {
      title: title,
      artist: artist,
      artwork: artwork,
      playing: playing,
      duration: video && isFinite(video.duration) ? video.duration : 0,
      position: video ? video.currentTime : 0,
      hasContent: hasContent(),
    };
  }

  function hasContent() {
    return !!document.querySelector(
      "ytmusic-section-list-renderer, ytmusic-search-page, ytmusic-browse-response, " +
        "ytmusic-two-column-browse-results-renderer, ytmusic-tabbed-search-results-renderer, " +
        "ytmusic-responsive-header-renderer, ytmusic-carousel-shelf-renderer, " +
        "ytmusic-item-section-renderer, ytmusic-grid-renderer",
    );
  }

  window.__ytmHasContent = hasContent;

  window.__ytmGo = function (url) {
    try {
      if (!url) return false;
      var dest;
      try {
        dest = new URL(url, location.href);
      } catch (e) {
        window.location.assign(url);
        return true;
      }
      if (dest.origin === location.origin && dest.pathname === location.pathname) {
        return true;
      }
      var path = dest.pathname;
      var links = document.querySelectorAll("a[href]");
      for (var i = 0; i < links.length; i++) {
        var href = links[i].getAttribute("href") || "";
        if (href === url || href === path || href.indexOf(path) === 0) {
          links[i].click();
          return true;
        }
      }
      var app = document.querySelector("ytmusic-app");
      if (app && typeof app.navigate === "function") {
        app.navigate(url);
        return true;
      }
      window.location.assign(dest.href);
      return true;
    } catch (e) {
      return false;
    }
  };

  window.__ytmSetHeader = function (mode, px) {
    window.__ytmHeaderMode = mode || "home";
    if (typeof px === "number" && px >= 0) window.__ytmNativeHeaderPx = 0;
    injectCss();
  };

  window.__ytmEmbedded = false;
  window.__ytmSetEmbedded = function (enabled) {
    window.__ytmEmbedded = !!enabled;
    if (window.__ytmEmbedded) {
      window.__ytmWasPlaying = false;
      var video = document.querySelector("video");
      if (video) {
        try {
          video.pause();
          video.muted = true;
        } catch (e) {}
      }
      injectCss();
    }
  };

  function videoIdFromHref(href) {
    if (!href) return "";
    try {
      var u = new URL(href, location.href);
      var v = u.searchParams.get("v");
      if (v && v.length === 11) return v;
      if (u.hostname.indexOf("youtu.be") !== -1) {
        var seg = u.pathname.replace(/^\//, "").split("/")[0];
        if (seg && seg.length === 11) return seg;
      }
    } catch (e) {}
    return "";
  }

  function hookEmbeddedPlay() {
    if (!window.__ytmEmbedded || !window.AndroidYtm) return;
    document.querySelectorAll("a[href*='watch'], a[href*='youtu.be/']").forEach(function (link) {
      if (link.__ytmHooked) return;
      link.__ytmHooked = true;
      link.addEventListener(
        "click",
        function (ev) {
          var id = videoIdFromHref(link.getAttribute("href") || "");
          if (!id) return;
          ev.preventDefault();
          ev.stopPropagation();
          try {
            window.AndroidYtm.onTrackSelected(id);
          } catch (e) {}
        },
        true,
      );
    });
    document.querySelectorAll("ytmusic-play-button-renderer, .play-button").forEach(function (btn) {
      if (btn.__ytmHooked) return;
      btn.__ytmHooked = true;
      btn.addEventListener(
        "click",
        function (ev) {
          var root = btn.closest("ytmusic-responsive-list-item-renderer, ytmusic-two-row-item-renderer, ytmusic-navigation-button-renderer, a");
          var link = root && (root.querySelector("a[href*='watch']") || (root.tagName === "A" ? root : null));
          var id = link ? videoIdFromHref(link.getAttribute("href") || "") : "";
          if (!id) return;
          ev.preventDefault();
          ev.stopPropagation();
          try {
            window.AndroidYtm.onTrackSelected(id);
          } catch (e) {}
        },
        true,
      );
    });
  }

  window.__ytmControl = function (action) {
    var video = document.querySelector("video");
    if (action === "play") {
      window.__ytmWasPlaying = true;
      normalizePlayback();
      if (video) video.play().catch(function () {});
    } else if (action === "pause") {
      window.__ytmWasPlaying = false;
      if (video) video.pause();
    } else if (action === "toggle") {
      if (video && !video.paused) window.__ytmControl("pause");
      else window.__ytmControl("play");
    } else if (action === "playFirst") {
      clickFirst([
        "ytmusic-play-button-renderer",
        '[aria-label="Play"]',
        '[aria-label^="Play "]',
        ".play-button",
      ]);
    } else if (action === "next") {
      var nextBtn = queryInBar(".next-button") || queryInBar("#next-button");
      if (nextBtn) nextBtn.click();
      else clickFirst([".next-button", "#next-button"]);
    } else if (action === "previous") {
      var prevBtn = queryInBar(".previous-button") || queryInBar("#previous-button");
      if (prevBtn) prevBtn.click();
      else clickFirst([".previous-button", "#previous-button"]);
    } else if (action === "expand") {
      expandPlayer();
    } else if (action === "collapse") {
      collapsePlayer();
    } else if (action === "cast") {
      clickFirst([
        "ytmusic-cast-button",
        "ytmusic-cast-button-renderer",
        '[aria-label="Cast"]',
        '[aria-label*="Cast"]',
      ]);
    } else if (action === "openSearch") {
      window.__ytmHeaderMode = "search";
      injectCss();
      clickNavIcon([
        "ytmusic-search-box",
        "#search-input",
        "input#search",
        '[placeholder*="Search"]',
        '[aria-label*="Search"]',
      ]);
    } else if (action === "openAccount") {
      clickNavIcon([
        "#avatar-btn",
        "yt-img-shadow#avatar",
        "button#avatar-btn",
        "ytmusic-settings-button",
        '[aria-label*="Account"]',
        '[aria-label*="Cuenta"]',
      ]);
    } else if (action === "openHistory") {
      window.__ytmGo("https://music.youtube.com/history");
    } else if (action === "openNotifications") {
      clickNavIcon([
        "ytmusic-notification-toggle-button",
        '[aria-label*="Notification"]',
        '[aria-label*="Notific"]',
      ]);
    } else if (action === "keepalive") {
      skipAds();
      if (!window.__ytmWantExpanded && !window.__ytmEmbedded) collapseDesktopPlayer();
      if (!window.__ytmEmbedded) normalizePlayback();
      if (window.__ytmEmbedded) {
        var embeddedVideo = document.querySelector("video");
        if (embeddedVideo) {
          try {
            embeddedVideo.pause();
            embeddedVideo.muted = true;
          } catch (e) {}
        }
      } else if (video && window.__ytmWasPlaying && video.paused) {
        video.play().catch(function () {});
      }
    }
  };

  injectCss();

  setInterval(function () {
    injectCss();
    skipAds();
    if (!window.__ytmWantExpanded && !window.__ytmEmbedded) collapseDesktopPlayer();
    if (!window.__ytmEmbedded) normalizePlayback();
    if (window.__ytmEmbedded) hookEmbeddedPlay();
  }, 1600);

  setInterval(function () {
    try {
      if (window.__ytmEmbedded) {
        hookEmbeddedPlay();
        return;
      }
      if (window.AndroidYtm) window.AndroidYtm.onState(JSON.stringify(metadata()));
    } catch (e) {}
  }, 800);
})();
