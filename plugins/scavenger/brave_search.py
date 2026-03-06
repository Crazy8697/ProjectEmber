from __future__ import annotations

import json
import os
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Optional


BRAVE_WEB_ENDPOINT = "https://api.search.brave.com/res/v1/web/search"


class BraveSearchError(RuntimeError):
    pass


def _env(name: str, default: Optional[str] = None) -> str:
    val = os.getenv(name, default)
    if val is None or val == "":
        raise BraveSearchError(f"Missing required env var: {name}")
    return val


def web_search(query: str, count: int = 5, country: str = "us", search_lang: str = "en") -> List[Dict[str, Any]]:
    """
    Returns list of dicts: {url,title,snippet}
    Uses Brave Search API Web Search endpoint.

    Auth header: X-Subscription-Token: <BRAVE_API_KEY>
    """
    q = (query or "").strip()
    if not q:
        return []

    token = _env("BRAVE_API_KEY")

    params = {
        "q": q,
        "count": str(int(count)),
        "country": country,
        "search_lang": search_lang,
    }
    url = BRAVE_WEB_ENDPOINT + "?" + urllib.parse.urlencode(params)

    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "X-Subscription-Token": token,
            "User-Agent": "scavenger-inventory/1.0",
        },
        method="GET",
    )

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = resp.read().decode("utf-8", errors="replace")
    except Exception as e:
        raise BraveSearchError(f"Brave web_search request failed: {e}") from e

    try:
        data = json.loads(body)
    except Exception as e:
        raise BraveSearchError(f"Brave web_search returned non-JSON: {e}") from e

    # Brave response commonly: {"web": {"results": [ {url,title,description}, ... ]}, ...}
    results: List[Dict[str, Any]] = []
    web = data.get("web") or {}
    items = web.get("results") or []
    if not isinstance(items, list):
        return []

    for item in items[: max(0, int(count))]:
        if not isinstance(item, dict):
            continue
        url_v = (item.get("url") or item.get("link") or "").strip()
        title = (item.get("title") or "").strip()
        snippet = (item.get("description") or item.get("snippet") or "").strip()
        if not url_v:
            continue
        results.append({"url": url_v, "title": title, "snippet": snippet})

    return results


def pick_best_datasheet_url(results: List[Dict[str, Any]]) -> Optional[str]:
    """
    Heuristic (improved):

    Goal: pick the *most trustworthy* datasheet page/link, not just "any .pdf".

    - Prefer well-known datasheet aggregators and manufacturer-hosted pages.
    - De-prioritize random-host PDFs / directory listings.
    - Still allow PDFs if they look legit, but don't let ".pdf" alone win.
    """
    if not results:
        return None

    # Known-good(ish) sources (not perfect, but far better than random Apache dirs)
    GOOD_DOMAINS = (
        "alldatasheet.",
        "alltransistors.com",
        "datasheetarchive.com",
        "octopart.com",
        "digikey.",
        "mouser.",
        "lcsc.com",
        "arrow.com",
        "rs-online.",
        "farnell.",
        "element14.",
        "sanken.",
        "onsemi.",
        "ti.com",
        "st.com",
        "infineon.",
        "nxp.com",
        "vishay.",
        "microchip.com",
        "analog.com",
        "rohm.com",
        "toshiba.",
        "panasonic.",
    )

    # Common SEO-scrape sites that often have thin/incorrect metadata
    SOFT_BAD_DOMAINS = (
        "datasheetcafe.com",
        "datasheetspdf.com",
        "datasheet4u.com",
        "pdfdatasheet.",
    )

    def score(url: str, title: str = "", snippet: str = "") -> int:
        u = (url or "").strip()
        if not u:
            return -10_000
        u_l = u.lower()
        t_l = (title or "").lower()
        sn_l = (snippet or "").lower()

        s = 0

        # Prefer HTTPS
        if u_l.startswith("https://"):
            s += 8
        elif u_l.startswith("http://"):
            s -= 3

        # Domain reputation weights
        if any(d in u_l for d in GOOD_DOMAINS):
            s += 60
        if any(d in u_l for d in SOFT_BAD_DOMAINS):
            s -= 20

        # Content signal
        if "datasheet" in u_l or "datasheet" in t_l or "datasheet" in sn_l:
            s += 20
        if "pinout" in t_l or "pinout" in sn_l:
            s += 6
        if "application" in t_l or "application note" in sn_l:
            s += 3

        # Filetype / structure
        if u_l.endswith(".pdf"):
            # PDFs are fine, but don't let random PDFs beat a reputable HTML listing.
            s += 18
        if "/pdf/" in u_l or "index of" in t_l or "apache" in sn_l:
            # Strongly penalize directory listings / random hosting.
            s -= 35

        # Light penalties for noisy tracking parameters
        if "?" in u_l:
            s -= 2

        return s

    best_url: Optional[str] = None
    best_s = -10_000
    for r in results:
        if not isinstance(r, dict):
            continue
        u = r.get("url") or ""
        sc = score(u, r.get("title") or "", r.get("snippet") or "")
        if sc > best_s:
            best_s = sc
            best_url = u

    # Require some minimal signal; otherwise return None
    return best_url if best_s > 5 else None
