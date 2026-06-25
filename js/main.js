import { mountApp } from "./ui.js";

mountApp().catch((e) => {
  console.error("PitchSite failed to start:", e);
  const t = document.getElementById("toast");
  if (t) { t.textContent = "Something went wrong starting the app"; t.classList.add("show"); }
});
