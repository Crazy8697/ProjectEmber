(function () {
  "use strict";

  window.__ADDJS_LOADED__ = true;
  console.log("add.js loaded");
  try { document.documentElement.setAttribute("data-addjs","1"); } catch (_) {}

  function qs(id) { return document.getElementById(id); }
  function qsa(sel) { return Array.from(document.querySelectorAll(sel)); }

  function setHidden(el, hidden) {
    if (!el) return;
    if (hidden) el.classList.add("hidden");
    else el.classList.remove("hidden");
  }

  function setThinking(state) {
    const el = qs("thinking");
    if (!el) return;
    if (state) { el.classList.remove("hidden"); el.style.display = "flex"; }
    else { el.classList.add("hidden"); }
  }

  function setAddQueuedThinking(state) {
    const el = qs("addQueuedThinking");
    if (!el) return;
    if (state) { el.classList.remove("hidden"); el.style.display = "flex"; }
    else { el.classList.add("hidden"); }
  }

  function setMsg(text, isError) {
    const el = qs("enrichMsg");
    if (!el) return;
    el.textContent = text || "";
    el.style.color = isError ? "#fca5a5" : "";
  }

  function setBtnDisabled(id, disabled) {
    const el = qs(id);
    if (!el) return;
    el.disabled = !!disabled;
    el.style.opacity = disabled ? "0.6" : "";
    el.style.cursor = disabled ? "not-allowed" : "";
  }

  function getHeaders() {
    const el = qs("appData");
    if (!el) return [];
    const raw = el.dataset.headers || "[]";
    try { return JSON.parse(raw); } catch { return []; }
  }

  function parseMPNs(raw) {
    return raw
      .split(/[\n,]+/)
      .map(s => s.trim())
      .filter(Boolean);
  }

  // --- Preview + Queue state ---
  let lastEnrichData = null;

  function escapeHtml(s) {
    return String(s ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function normalizeRow(row) {
    const out = Object.assign({}, row || {});
    if (out.qty_on_hand == null) out.qty_on_hand = "";
    if (out.location_bin == null) out.location_bin = "";
    if (out.notes == null) out.notes = "";
    return out;
  }

  function orderedHeadersFrom(rowObj) {
    const keys = Object.keys(rowObj || {});
    const priority = [
      "mpn",
      "manufacturer",
      "description",
      "category",
      "package",
      "pin_count",
      "polarity_or_channel",
      "qty_on_hand",
      "location_bin",
      "notes",
      "needs_review",
      "confidence",
      "datasheet_url",
      "avg_cost",
      "current_rating_a",
      "power_rating_w",
      "voltage_rating_v",
      "rating_context"
    ];

    const out = [];
    priority.forEach(k => { if (keys.includes(k)) out.push(k); });
    keys.forEach(k => { if (!out.includes(k)) out.push(k); });
    return out;
  }

  function asWarnings(r) {
    const w = r?.warnings;
    if (!w) return [];
    if (Array.isArray(w)) return w.map(String);
    return [String(w)];
  }

  function getMeta(r) {
    const m = r?.meta;
    return (m && typeof m === "object") ? m : {};
  }

  function renderBadges(r) {
    const w = asWarnings(r);
    if (!w.length) return "";
    const badges = w.slice(0, 6).map(x => `<span class="pill">${escapeHtml(x)}</span>`).join(" ");
    return `<div class="small muted" style="margin-top:6px; display:flex; gap:6px; flex-wrap:wrap;">${badges}</div>`;
  }

  function renderPreview(data) {
    lastEnrichData = data;

    const box = qs("enrichResults");
    if (!box) return;

    setHidden(box, false);

    const results = data?.results || [];
    if (!results.length) {
      box.innerHTML = "<div class='muted'>No results.</div>";
      setMsg("No results returned.", false);
      return;
    }

    const headers = orderedHeadersFrom(results[0].row || {});
    const hasNeedsReview = headers.includes("needs_review");
    const hasConfidence = headers.includes("confidence");

    let html = "";
    html += "<div class='row' style='margin-top:6px; gap:10px;'>";
    html += "  <button type='button' class='btn ghost' id='selectAllBtn'>Select all</button>";
    html += "  <button type='button' class='btn ghost' id='selectNoneBtn'>Select none</button>";
    html += "  <span class='muted small'>Tip: you can edit qty/bin/notes inline before queueing.</span>";
    html += "</div>";

    html += "<table class='enrichTable'>";
    html += "<thead><tr><th style='width:48px;'></th>";
    headers.forEach(h => { html += `<th>${escapeHtml(h)}</th>`; });
    html += "</tr></thead><tbody>";

    results.forEach((r, idx) => {
      const row = normalizeRow(r.row);
      const meta = getMeta(r);
      const needsReview = String(row.needs_review || "").toLowerCase() === "true";
      const conf = parseFloat(row.confidence);
      const lowConf = Number.isFinite(conf) && conf < 0.55;

      const rowStyle = (needsReview || lowConf)
        ? " style='background: rgba(252,165,165,0.08);'"
        : "";

      html += `<tr${rowStyle}>`;
      html += `<td><input type="checkbox" class="previewCheck" data-idx="${idx}" /></td>`;

      headers.forEach(h => {
        if (h === "qty_on_hand") {
          html += `<td><input class="inlineEdit" data-idx="${idx}" data-key="${h}" style="width:90px;" value="${escapeHtml(row[h])}" /></td>`;
          return;
        }
        if (h === "location_bin") {
          html += `<td><input class="inlineEdit" data-idx="${idx}" data-key="${h}" style="width:140px;" value="${escapeHtml(row[h])}" /></td>`;
          return;
        }
        if (h === "notes") {
          html += `<td><input class="inlineEdit" data-idx="${idx}" data-key="${h}" style="min-width:180px; width:100%;" value="${escapeHtml(row[h])}" /></td>`;
          return;
        }

        if (h === "datasheet_url") {
          const ds = String(row[h] || "").trim();
          const dl = String(meta.download_url || "").trim();
          if (ds) {
            html += `<td><a href="${escapeHtml(ds)}" target="_blank" rel="noreferrer">datasheet</a>${renderBadges(r)}</td>`;
          } else if (dl) {
            html += `<td><a href="${escapeHtml(dl)}" target="_blank" rel="noreferrer">Download manually</a>${renderBadges(r)}</td>`;
          } else {
            html += `<td>${escapeHtml(ds)}${renderBadges(r)}</td>`;
          }
          return;
        }

        html += `<td>${escapeHtml(row[h])}</td>`;
      });

      html += "</tr>";
    });

    html += "</tbody></table>";
    html += `<div style="margin-top:10px; display:flex; gap:10px; align-items:center; flex-wrap:wrap;">
      <button type="button" class="btn" id="queueSelectedBtn">Queue selected</button>
      <button type="button" class="btn ghost" id="clearPreviewBtn">Clear preview</button>
    </div>`;

    if (hasNeedsReview || hasConfidence) {
      html += `<div class="muted small" style="margin-top:8px;">
        Highlighted rows indicate <code>needs_review</code> or low <code>confidence</code>.
      </div>`;
    }

    box.innerHTML = html;

    qs("queueSelectedBtn")?.addEventListener("click", function () { queueSelected(); });
    qs("clearPreviewBtn")?.addEventListener("click", function () { clearPreview(); });
    qs("selectAllBtn")?.addEventListener("click", function () { setAllChecks(true); });
    qs("selectNoneBtn")?.addEventListener("click", function () { setAllChecks(false); });

    qsa(".inlineEdit").forEach(inp => {
      inp.addEventListener("input", function (ev) {
        const el = ev.currentTarget;
        const idx = parseInt(el.dataset.idx, 10);
        const key = el.dataset.key;
        const val = el.value;

        try {
          const target = lastEnrichData.results[idx].row;
          target[key] = val;
        } catch (_) { /* ignore */ }
      });
    });

    setMsg(`Preview ready: ${results.length} row(s). Select, queue, then add.`, false);
  }

  function setAllChecks(state) {
    qsa(".previewCheck").forEach(c => { c.checked = !!state; });
  }

  function clearPreview() {
    const box = qs("enrichResults");
    if (box) {
      box.innerHTML = "";
      setHidden(box, true);
    }
    lastEnrichData = null;
    setMsg("", false);
  }

  function queueSelected() {
    const data = lastEnrichData;
    if (!data || !data.results) {
      alert("No preview data. Enrich first.");
      return;
    }

    const checked = qsa(".previewCheck").filter(c => c.checked);
    if (!checked.length) {
      alert("Select at least one row.");
      return;
    }

    const queueBox = qs("queueBox");
    const queueList = qs("queueList");
    if (!queueBox || !queueList) return;

    setHidden(queueBox, false);

    checked.forEach(c => {
      const idx = parseInt(c.dataset.idx, 10);
      const row = normalizeRow(data.results[idx].row);

      const mpn = String(row.mpn || "").trim();
      const desc = String(row.description || "").trim();
      const fingerprint = `${mpn}||${desc}`;
      const existing = qsa("#queueList .queueItem").some(el => el.dataset.fp === fingerprint);
      if (existing) return;

      const item = document.createElement("div");
      item.className = "queueItem";
      item.dataset.row = JSON.stringify(row);
      item.dataset.fp = fingerprint;

      const left = document.createElement("div");
      left.innerHTML = `<strong>${escapeHtml(row.mpn || "(no mpn)")}</strong>
        <div class="small muted">${escapeHtml(row.description || "")}</div>
        <div class="small muted">qty_on_hand: ${escapeHtml(row.qty_on_hand || "")} • bin: ${escapeHtml(row.location_bin || "")}</div>`;

      const removeBtn = document.createElement("button");
      removeBtn.textContent = "Remove";
      removeBtn.className = "btn ghost small";
      removeBtn.type = "button";
      removeBtn.onclick = function () {
        queueList.removeChild(item);
        updateQueueControls();
      };

      item.appendChild(left);
      item.appendChild(removeBtn);
      queueList.appendChild(item);
    });

    updateQueueControls();
  }

  function updateQueueControls() {
    const queueBox = qs("queueBox");
    const queueList = qs("queueList");
    const addQueuedBtn = qs("addQueuedBtn");
    const count = queueList?.children?.length || 0;

    if (queueBox) {
      if (count) queueBox.classList.remove("hidden");
      else queueBox.classList.add("hidden");
    }

    if (addQueuedBtn) {
      addQueuedBtn.disabled = (count === 0);
      addQueuedBtn.style.opacity = (count === 0) ? "0.6" : "";
      addQueuedBtn.style.cursor = (count === 0) ? "not-allowed" : "";
    }
  }

  async function enrichList() {
    setMsg("Enrich clicked…", false);

    const mpnRaw = qs("mpnList")?.value || "";
    const hints = qs("hints")?.value || "";
    const mpns = parseMPNs(mpnRaw);

    if (!mpns.length) {
      alert("Enter at least one MPN.");
      return;
    }

    setBtnDisabled("enrichBtn", true);

    try {
      setThinking(true);
      setMsg(`POST /scavenger/api/enrich/batch (${mpns.length} item(s))…`, false);

      const res = await fetch("/scavenger/api/enrich/batch", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ items: mpns, hints })
      });

      setMsg(`HTTP ${res.status} from /scavenger/api/enrich/batch`, !res.ok);

      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();
      console.log("Enrichment response:", data);
      renderPreview(data);
    } catch (err) {
      console.error("Enrichment failed:", err);
      setMsg("Enrichment failed. Check console for details.", true);
      alert("Enrichment failed. See console.");
    } finally {
      setThinking(false);
      setBtnDisabled("enrichBtn", false);
    }
  }

  async function postRowToAdd(row) {
    const headers = getHeaders();
    const params = new URLSearchParams();
    const keys = headers.length ? headers : Object.keys(row || {});
    keys.forEach(k => params.set(k, row?.[k] ?? ""));

    const res = await fetch("/scavenger/add", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: params.toString()
    });
    if (!res.ok) throw new Error("HTTP " + res.status);
  }

  async function addQueued() {
    const queueBox = qs("queueBox");
    const queueList = qs("queueList");
    const items = Array.from(queueList?.children || []);
    if (!items.length) {
      alert("Queue is empty.");
      return;
    }

    setBtnDisabled("addQueuedBtn", true);
    setBtnDisabled("clearQueueBtn", true);

    try {
      setAddQueuedThinking(true);
      setMsg(`Adding ${items.length} queued item(s)…`, false);

      for (const item of items) {
        let row = {};
        try { row = JSON.parse(item.dataset.row || "{}"); } catch {}
        await postRowToAdd(row);
        queueList.removeChild(item);
        updateQueueControls();
      }

      if (queueBox) queueBox.classList.add("hidden");
      setMsg("Queued items added.", false);
      alert("Queued items added.");
    } catch (err) {
      console.error("Add queued failed:", err);
      setMsg("Add queued failed. Check console for details.", true);
      alert("Add queued failed. See console.");
    } finally {
      setAddQueuedThinking(false);
      setBtnDisabled("addQueuedBtn", false);
      setBtnDisabled("clearQueueBtn", false);
      updateQueueControls();
    }
  }

  function clearQueue() {
    const queueList = qs("queueList");
    if (queueList) queueList.innerHTML = "";
    updateQueueControls();
    setMsg("Queue cleared.", false);
  }

  function _initAddPage() {
    // Click probe: capture-phase listener to prove clicks are happening even if something blocks bubbling.
    document.addEventListener("click", function (ev) {
      const t = ev.target;
      const id = t && t.id;
      if (id === "enrichBtn" || (t && t.closest && t.closest("#enrichBtn"))) {
        setMsg("Click probe: enrichBtn clicked (captured).", false);
      }
    }, true);

    const enrichBtn = qs("enrichBtn");
    if (enrichBtn) enrichBtn.addEventListener("click", enrichList);

    const addQueuedBtn = qs("addQueuedBtn");
    if (addQueuedBtn) addQueuedBtn.addEventListener("click", addQueued);

    const clearQueueBtn = qs("clearQueueBtn");
    if (clearQueueBtn) clearQueueBtn.addEventListener("click", clearQueue);

    updateQueueControls();
    setMsg("add.js init OK (handlers attached).", false);
  }

  try {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", _initAddPage);
    } else {
      _initAddPage();
    }
  } catch (e) {
    console.error("add.js init failed:", e);
  }
})();