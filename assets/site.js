// Butler's site: release facts read from GitHub as the page opens, so a release needs no
// edit here; the dither field from the app's thinking line; and the small live demos.
(() => {
  "use strict";

  const REPO = "CherrysButler/Butler";
  const API = `https://api.github.com/repos/${REPO}/releases?per_page=30`;
  const RELEASES = `https://github.com/${REPO}/releases`;
  // The release key's certificate, read from the 0.2.3 APK. Every release since 0.1 carries it.
  const KEY = "664e07a5af6b9069f6a785f4c74d4ca2c067b526a994faf97dffb61cb5c1562d";
  const CACHE = "butler-releases-v1";
  // Reduced motion, or ?still for captures: every demo shows its settled state.
  const still = matchMedia("(prefers-reduced-motion: reduce)").matches || new URLSearchParams(location.search).has("still");

  // ---------------------------------------------------------------- releases

  async function releases(fresh) {
    if (!fresh) {
      try {
        const kept = JSON.parse(sessionStorage.getItem(CACHE) || "null");
        if (kept && Date.now() - kept.at < 10 * 60_000) return kept.list;
      } catch (_) { /* storage may be off */ }
    }
    const r = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
    if (!r.ok) throw new Error(`GitHub answered ${r.status}`);
    const list = (await r.json()).filter((x) => !x.draft && !x.prerelease);
    try { sessionStorage.setItem(CACHE, JSON.stringify({ at: Date.now(), list })); } catch (_) {}
    return list;
  }

  function facts(r) {
    const body = r.body || "";
    const apk = (r.assets || []).find((a) => /\.apk$/i.test(a.name));
    const sha = (body.match(/SHA-256:\s*`([0-9a-f]{64})`/i) || [])[1] || null;
    const vt = (body.match(/\((https:\/\/www\.virustotal\.com\/gui\/file\/[0-9a-f]{64}[^)\s]*)\)/i) || [])[1] || null;
    let scan = null;
    const clean = body.match(/no security vendor flags it \((\d+) checked\)/i);
    const flagged = body.match(/(\d+) of (\d+) vendors flagged it/i);
    if (clean) scan = { flagged: 0, total: +clean[1] };
    else if (flagged) scan = { flagged: +flagged[1], total: +flagged[2] };
    return {
      tag: r.tag_name,
      version: r.tag_name.replace(/^v/, ""),
      date: new Date(r.published_at),
      url: r.html_url,
      apk: apk ? apk.browser_download_url : `${RELEASES}/latest`,
      size: apk ? apk.size : null,
      sha, vt, scan, body,
    };
  }

  const fmtDate = (d) => d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
  const fmtSize = (b) => (b == null ? "" : `${(b / 1048576).toFixed(1)} MB`);
  const short = (h) => `${h.slice(0, 8)}…${h.slice(-6)}`;
  const esc = (s) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const icon = (name) => `<span class="icon" aria-hidden="true">${name}</span>`;
  const sleep = (ms) => new Promise((go) => setTimeout(go, still ? 0 : ms));

  // ---------------------------------------------------------------- the panel

  function setCheck(row, state, value, aside) {
    if (!row) return;
    row.dataset.state = state;
    const v = row.querySelector(".value");
    const a = row.querySelector(".aside");
    if (value != null) v.innerHTML = value;
    if (a) a.innerHTML = aside || "";
    const lamp = row.querySelector(".lamp .icon");
    if (lamp) lamp.textContent = state === "pass" ? "check" : state === "warn" ? "priority_high" : state === "info" ? "info" : "";
  }

  async function fillPanel(panel, fresh) {
    const rows = {};
    panel.querySelectorAll("[data-check]").forEach((r) => {
      rows[r.dataset.check] = r;
      setCheck(r, "pending", "Checking…", "");
    });
    const keys = panel.querySelectorAll("[data-download]");
    let list;
    try {
      list = await releases(fresh);
    } catch (e) {
      panel.querySelector(".checks").innerHTML =
        `<li class="offline">Couldn’t reach GitHub just now. The release page has the same files and checks: <a href="${RELEASES}/latest">github.com/${REPO}/releases</a>.</li>`;
      keys.forEach((k) => { k.href = `${RELEASES}/latest`; });
      return;
    }
    const f = facts(list[0]);
    keys.forEach((k) => {
      k.href = f.apk;
      const label = k.querySelector("[data-download-label]");
      if (label) label.textContent = `Download Butler ${f.version}`;
    });
    panel.querySelectorAll("[data-download-size]").forEach((s) => { s.textContent = fmtSize(f.size); });
    document.querySelectorAll("[data-latest-version]").forEach((s) => { s.textContent = f.version; });

    await sleep(260);
    setCheck(rows.version, "pass", `Butler ${esc(f.version)}`, `<span class="num">${fmtDate(f.date)}</span>`);
    await sleep(380);
    if (f.scan && f.scan.flagged === 0) {
      setCheck(rows.scan, "pass", `Clean on VirusTotal`, `<a href="${f.vt}" rel="noopener">${f.scan.total} vendors</a>`);
    } else if (f.scan) {
      setCheck(rows.scan, "warn", `${f.scan.flagged} of ${f.scan.total} vendors flagged it`, `<a href="${f.vt}" rel="noopener">Read the report</a>`);
    } else if (f.vt) {
      setCheck(rows.scan, "info", "Scan it on VirusTotal", `<a href="${f.vt}" rel="noopener">Open</a>`);
    } else {
      setCheck(rows.scan, "info", "Not scanned by the workflow", `<a href="${f.url}">Release</a>`);
    }
    await sleep(380);
    if (f.sha) {
      setCheck(rows.sha, "pass", `<span class="num" title="${f.sha}">${short(f.sha)}</span>`,
        `<button class="copy" type="button" data-copy="${f.sha}" aria-label="Copy the SHA-256">${icon("content_copy")}</button>`);
    } else {
      setCheck(rows.sha, "info", "In SHA256SUMS.txt", `<a href="${f.url}">Release</a>`);
    }
    await sleep(380);
    // Published facts, not checks a browser can run: shown as information, verified on the Download page's commands.
    setCheck(rows.key, "info", `<span class="num" title="${KEY}">${short(KEY)}</span>`, "Same since 0.1");
    await sleep(380);
    setCheck(rows.source, "info", `<a href="https://github.com/${REPO}/tree/${encodeURIComponent(f.tag)}">${esc(f.tag)} on GitHub</a>`, "Apache-2.0");
  }

  // ---------------------------------------------------------------- the release log

  function notesHtml(md) {
    // Only what Butler's release notes use: ## heads, - bullets, paragraphs, **bold**, `code`, [links](...).
    const cut = md.split(/\n(?=##\s)/).filter((part) => !/^##\s+(Install|Checked)\b/i.test(part.trim()));
    const inline = (s) => esc(s)
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/\*([^*\s][^*]*)\*/g, "<em>$1</em>")
      .replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" rel="noopener">$1</a>');
    let html = "";
    let list = false;
    for (const raw of cut.join("\n").split("\n")) {
      const line = raw.trim();
      if (!line) { if (list) { html += "</ul>"; list = false; } continue; }
      const head = line.match(/^#{2,3}\s+(.*)/);
      if (head) { if (list) { html += "</ul>"; list = false; } html += `<h3>${inline(head[1])}</h3>`; continue; }
      const item = line.match(/^[-*]\s+(.*)/);
      if (item) { if (!list) { html += "<ul>"; list = true; } html += `<li>${inline(item[1])}</li>`; continue; }
      if (list) { html += "</ul>"; list = false; }
      html += `<p>${inline(line)}</p>`;
    }
    if (list) html += "</ul>";
    return html;
  }

  async function fillLog(log) {
    let list;
    try { list = await releases(false); } catch (e) {
      log.innerHTML = `<p class="intro">Couldn’t reach GitHub just now. Every release is on <a href="${RELEASES}">the releases page</a>.</p>`;
      return;
    }
    log.innerHTML = list.map((r, i) => {
      const f = facts(r);
      const scan = f.scan && f.scan.flagged === 0
        ? `<span class="ok">${icon("check_circle")}<a href="${f.vt}" rel="noopener">Clean on VirusTotal, ${f.scan.total} vendors</a></span>`
        : f.vt ? `<a href="${f.vt}" rel="noopener">VirusTotal report</a>` : `<span>Not scanned by the workflow</span>`;
      return `<details class="release"${i === 0 ? " open" : ""}>
        <summary>
          <span class="tag">${esc(f.version)}</span>
          <span class="when num">${fmtDate(f.date)}</span>
          ${i === 0 ? '<span class="latest">Latest</span>' : "<span></span>"}
          <span class="icon chev" aria-hidden="true">expand_more</span>
        </summary>
        <div class="body">
          <div class="notes">${notesHtml(f.body) || "<p>No notes for this one.</p>"}</div>
          <div class="release-meta">
            <a href="${f.apk}">Butler-${esc(f.version)}.apk</a>
            <span class="num">${fmtSize(f.size)}</span>
            ${scan}
            ${f.sha ? `<span class="num" title="${f.sha}">SHA-256 ${short(f.sha)}</span>` : ""}
            <a href="${f.url}">Release page</a>
          </div>
        </div>
      </details>`;
    }).join("");
    log.querySelectorAll(".notes").forEach(sansNumbers);
  }

  // ---------------------------------------------------------------- the dither field

  // The app's thinking line: a 4x4 ordered dither that thickens and thins as two waves drift
  // across it. Here it fills the hero, dense behind the panel and gone by the top-left.
  const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5].map((v) => (v + 0.5) / 16);

  function field(canvas, opts) {
    const ctx = canvas.getContext("2d");
    const dot = opts.dot || 3;
    const pitch = opts.pitch || 4;
    const color = opts.color || "#50555C";
    let w = 0, h = 0, dpr = 1, raf = 0, visible = true, t0 = performance.now();

    function size() {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      const box = canvas.getBoundingClientRect();
      w = Math.max(1, Math.round(box.width));
      h = Math.max(1, Math.round(box.height));
      canvas.width = Math.round(w * dpr / pitch) ;
      canvas.height = Math.round(h * dpr / pitch);
      canvas.style.imageRendering = "pixelated";
    }

    function frame(now) {
      const t = ((now - t0) / (opts.period || 9000)) % 1;
      const cw = canvas.width, ch = canvas.height;
      const img = ctx.createImageData(cw, ch);
      const [r, g, b] = hex(color);
      for (let y = 0; y < ch; y++) {
        const ny = y / ch;
        for (let x = 0; x < cw; x++) {
          const nx = x / cw;
          const wave = 0.5 + 0.35 * Math.sin((nx - t) * 6.283 + ny * 2.2) + 0.15 * Math.sin((nx * 2.3 + t * 1.7) * 6.283 - ny * 3.1);
          const level = opts.shape(nx, ny) * wave;
          if (level > BAYER[(y & 3) * 4 + (x & 3)]) {
            const i = (y * cw + x) * 4;
            img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; img.data[i + 3] = opts.alpha || 255;
          }
        }
      }
      ctx.putImageData(img, 0, 0);
      if (!still && visible) raf = requestAnimationFrame(throttle);
    }
    let last = 0;
    function throttle(now) { if (now - last > 66) { last = now; frame(now); } else raf = requestAnimationFrame(throttle); }

    size();
    frame(performance.now());
    new ResizeObserver(() => { size(); frame(performance.now()); }).observe(canvas);
    new IntersectionObserver((e) => {
      visible = e[0].isIntersecting;
      cancelAnimationFrame(raf);
      if (visible && !still) raf = requestAnimationFrame(throttle);
    }).observe(canvas);
    void dot;
  }

  function hex(c) {
    const n = parseInt(c.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  // ---------------------------------------------------------------- small demos

  // A line sent on a bad connection: queued, sending, a retry with a countdown, delivered.
  function states(box) {
    const mine = box.querySelector(".mine");
    const line = box.querySelector(".turn-state");
    const reply = box.querySelector(".bot.reply");
    const steps = [
      [900, () => { mine.classList.add("unsent"); reply.hidden = true; line.innerHTML = "Queued"; }],
      [1100, () => { line.innerHTML = "Sending"; }],
      [900, () => { line.innerHTML = 'No connection · retrying in 3s <span class="act">Retry now</span>'; }],
      [1000, () => { line.innerHTML = 'No connection · retrying in 2s <span class="act">Retry now</span>'; }],
      [1000, () => { line.innerHTML = 'No connection · retrying in 1s <span class="act">Retry now</span>'; }],
      [1000, () => { line.innerHTML = "Sending"; }],
      [1300, () => { mine.classList.remove("unsent"); line.innerHTML = ""; reply.hidden = false; }],
      [4200, () => {}],
    ];
    if (still) { mine.classList.remove("unsent"); line.innerHTML = ""; reply.hidden = false; return; }
    let i = 0;
    (function next() {
      const [wait, act] = steps[i];
      act();
      i = (i + 1) % steps.length;
      setTimeout(next, wait);
    })();
  }

  // Agent mode's steps, one lit at a time, then all done.
  function agent(list) {
    const items = [...list.querySelectorAll("li")];
    if (still) { items.forEach((li) => li.classList.add("done")); return; }
    let i = 0;
    (function next() {
      items.forEach((li, j) => { li.classList.toggle("done", j < i); li.classList.toggle("run", j === i); });
      i = i >= items.length ? 0 : i + 1;
      setTimeout(next, i === 0 ? 2600 : 1300);
    })();
  }

  // The bow tie ties itself once: wings out from the knot.
  function tie(mark) {
    if (still || !mark.animate) return;
    const ease = "cubic-bezier(0.16, 1, 0.3, 1)";
    mark.querySelectorAll(".wing-l, .wing-r").forEach((wing) => {
      const left = wing.classList.contains("wing-l");
      wing.animate(
        [{ transform: `translateX(${left ? 14 : -14}%) scaleX(0.2)`, opacity: 0 }, { transform: "none", opacity: 1 }],
        { duration: 720, delay: 120, easing: ease, fill: "backwards" },
      );
    });
    mark.querySelector(".knot")?.animate([{ transform: "scale(0.6)", opacity: 0 }, { transform: "none", opacity: 1 }], { duration: 420, easing: ease, fill: "backwards" });
  }

  // Butter: the reply folds to its beats and back; the key stops the loop once touched.
  function butter(fold) {
    const key = fold.closest(".stage").querySelector("[data-butter-key]");
    let auto = !still, on = false, timer = 0;
    const set = (v) => { on = v; fold.classList.toggle("folded", on); fold.classList.toggle("tinted", !on); key.setAttribute("aria-pressed", String(on)); };
    key.addEventListener("click", () => { auto = false; clearTimeout(timer); set(!on); });
    if (still) { fold.classList.add("tinted"); return; }
    (function loop() { if (!auto) return; set(!on); timer = setTimeout(loop, on ? 3600 : 4200); })();
    fold.classList.add("tinted");
  }

  // Highlights: the tints settle in, hold, and clear.
  function highlights(stage) {
    if (still) { stage.classList.add("lit"); return; }
    (function loop(lit) { stage.classList.toggle("lit", lit); setTimeout(() => loop(!lit), lit ? 4200 : 1600); })(true);
  }

  // Rich typing: a line typed key by key, its marks styled as they open and close.
  function typing(box) {
    const out = box.querySelector(".typed");
    const keys = box.closest(".demo").querySelectorAll(".marks span");
    const line = '"Stay," she whispers, *closing the door behind her.* **Please.**';
    const render = (n) => {
      let html = "", sp = false, ac = false, bd = false;
      const s = line.slice(0, n);
      for (let i = 0; i < s.length; i++) {
        const c = s[i];
        if (c === "*" && s[i + 1] === "*") { html += '<span class="m">**</span>'; bd = !bd; i++; continue; }
        if (c === "*") { html += '<span class="m">*</span>'; ac = !ac; continue; }
        if (c === '"') { if (!sp) { html += '<span class="m">"</span>'; sp = true; } else { sp = false; html += '<span class="m">"</span>'; } continue; }
        const cls = [sp && "sp", ac && "ac", bd && "bd"].filter(Boolean).join(" ");
        html += cls ? `<span class="${cls}">${esc(c)}</span>` : esc(c);
      }
      out.innerHTML = html + '<span class="caret"></span>';
      const lit = bd ? "bold" : ac ? "action" : sp ? "speech" : null;
      keys.forEach((k) => k.classList.toggle("lit", k.dataset.k === lit));
    };
    if (still) { render(line.length); return; }
    let n = 0;
    (function tick() {
      render(n);
      n++;
      if (n > line.length) { setTimeout(() => { n = 0; tick(); }, 2600); return; }
      setTimeout(tick, 45 + Math.random() * 70);
    })();
  }

  // Rerolls: two picks light, the key changes, then they clear.
  function picks(box) {
    const chips = [...box.querySelectorAll("span")];
    const label = box.parentElement.querySelector("[data-picks-label]");
    const show = (on) => { chips.forEach((c, i) => c.classList.toggle("on", on.includes(i))); label.textContent = on.length ? "Rewrite with this" : "Try again"; };
    if (still) { show([4, 5]); return; }
    const seq = [[], [4], [4, 5], [4, 5], [2], [2, 0], [2, 0]];
    let i = 0;
    (function loop() { show(seq[i]); i = (i + 1) % seq.length; setTimeout(loop, 1300); })();
  }

  // Thinking words: the plate's word changes reply to reply.
  function words(el) {
    if (still) return;
    const list = ["Brewing", "Pondering", "Noodling", "Simmering", "Plotting", "Daydreaming"];
    let i = 0;
    setInterval(() => { i = (i + 1) % list.length; el.textContent = `${list[i]}\u2026`; }, 2400);
  }

  // Version numbers and long figures inside prose take the UI face, so a zero looks the same everywhere.
  function sansNumbers(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: (n) => (n.parentElement.closest("code, pre, script, style, .num, .vn, .icon, svg") ? NodeFilter.FILTER_REJECT : /\d+\.\d|\d{3,}/.test(n.data) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP),
    });
    const hits = [];
    while (walker.nextNode()) hits.push(walker.currentNode);
    for (const node of hits) {
      const frag = document.createDocumentFragment();
      let last = 0;
      node.data.replace(/\d+(?:\.\d+)+|\d{3,}/g, (m, at) => {
        if (at > last) frag.append(node.data.slice(last, at));
        const span = document.createElement("span");
        span.className = "vn";
        span.textContent = m;
        frag.append(span);
        last = at + m.length;
        return m;
      });
      if (last < node.data.length) frag.append(node.data.slice(last));
      node.replaceWith(frag);
    }
  }

  // ---------------------------------------------------------------- wiring

  document.addEventListener("click", async (e) => {
    const copy = e.target.closest("[data-copy]");
    if (copy) {
      try {
        await navigator.clipboard.writeText(copy.dataset.copy);
        copy.classList.add("done");
        copy.querySelector(".icon").textContent = "check";
        copy.setAttribute("aria-label", "Copied");
        setTimeout(() => { copy.classList.remove("done"); copy.querySelector(".icon").textContent = "content_copy"; copy.setAttribute("aria-label", "Copy"); }, 1600);
      } catch (_) { /* clipboard refused: the value is in the title */ }
    }
    const again = e.target.closest("[data-recheck]");
    if (again) fillPanel(again.closest(".panel"), true);
  });

  document.querySelectorAll("main, .page-hero, .hero").forEach(sansNumbers);
  document.querySelectorAll(".panel[data-panel]").forEach((p) => fillPanel(p, false));
  document.querySelectorAll("[data-log]").forEach(fillLog);
  document.querySelectorAll("[data-states]").forEach(states);
  document.querySelectorAll("[data-agent]").forEach(agent);
  document.querySelectorAll("svg.mark").forEach(tie);
  document.querySelectorAll("[data-butter]").forEach(butter);
  document.querySelectorAll("[data-highlights]").forEach(highlights);
  document.querySelectorAll("[data-typing]").forEach(typing);
  document.querySelectorAll("[data-picks]").forEach(picks);
  document.querySelectorAll("[data-words]").forEach(words);

  document.querySelectorAll("canvas[data-field]").forEach((c) => field(c, {
    pitch: 4, period: 11000, alpha: 215,
    // Thick at the right where the panel sits, thinning to nothing at the top-left.
    shape: (x, y) => Math.max(0, Math.min(1, (x - 0.18) * 1.05)) * (0.55 + 0.45 * Math.min(1, y * 1.6)),
  }));
  document.querySelectorAll("canvas[data-think]").forEach((c) => field(c, {
    pitch: 3, period: 2600, color: "#50555C",
    shape: (x) => 0.7 * (0.4 + 0.6 * Math.min(1, x / 0.15)),
  }));
})();
