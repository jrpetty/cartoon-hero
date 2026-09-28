// Saving a file to the player's computer, and opening one from it.
//
// The game is a single page; files come and go through the browser: a Blob
// and a download link out, a hidden file input (or a drag-and-drop) in.

/** Offer `text` to the player as a file called `name`. */
export function downloadText(name: string, text: string, type = "application/json"): boolean {
  try {
    const url = URL.createObjectURL(new Blob([text], { type }));
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    a.style.display = "none";
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
    return true;
  } catch {
    return false;
  }
}

/** Ask the player to choose a file; resolves with its name and text (or null if they cancel). */
export function pickTextFile(accept: string): Promise<{ name: string; text: string } | null> {
  return new Promise((resolve) => {
    try {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = accept;
      input.style.display = "none";
      input.onchange = () => {
        const f = input.files?.[0];
        input.remove();
        if (!f) { resolve(null); return; }
        f.text().then((text) => resolve({ name: f.name, text }), () => resolve(null));
      };
      document.body.appendChild(input);
      input.click();
    } catch {
      resolve(null);
    }
  });
}
