async function doPush() {
  const modal = document.getElementById("pushModal");
  const outEl = document.getElementById("pushOutput");
  modal.setAttribute("aria-hidden", "false");
  outEl.textContent = "Running push_inventory.sh...\n";

  try {
    const resp = await fetch("./push", { method: "POST" });
    const data = await resp.json();
    outEl.textContent =
      (data.ok ? "✅ Push OK\n\n" : "❌ Push FAILED\n\n") + (data.output || "");
  } catch (e) {
    outEl.textContent = "Exception: " + e;
  }
}

function closeModal() {
  const modal = document.getElementById("pushModal");
  modal.setAttribute("aria-hidden", "true");
}

window.addEventListener("DOMContentLoaded", () => {
  const pushBtn = document.getElementById("pushBtn");
  const closeBtn = document.getElementById("pushClose");
  const modal = document.getElementById("pushModal");

  if (pushBtn) pushBtn.addEventListener("click", doPush);
  if (closeBtn) closeBtn.addEventListener("click", closeModal);
  if (modal)
    modal.addEventListener("click", (e) => {
      if (e.target === modal) closeModal();
    });
});
