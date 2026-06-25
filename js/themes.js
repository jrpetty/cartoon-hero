/* PitchSite — theme catalogue.
 * Each theme is a self-contained design system: palette, typography,
 * shape language and motion. Themes are applied to the generated site
 * (not the builder UI) via CSS custom properties, so the live preview and
 * the exported file are always identical. */

export const FONTS = {
  modern: {
    name: "Inter + Inter",
    heading: "'Inter', system-ui, sans-serif",
    body: "'Inter', system-ui, sans-serif",
    googleHref:
      "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap",
  },
  editorial: {
    name: "Fraunces + Inter",
    heading: "'Fraunces', Georgia, serif",
    body: "'Inter', system-ui, sans-serif",
    googleHref:
      "https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400..900&family=Inter:wght@400;500;600;700&display=swap",
  },
  geometric: {
    name: "Space Grotesk + Inter",
    heading: "'Space Grotesk', system-ui, sans-serif",
    body: "'Inter', system-ui, sans-serif",
    googleHref:
      "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=Inter:wght@400;500;600&display=swap",
  },
  warm: {
    name: "Poppins + Nunito Sans",
    heading: "'Poppins', system-ui, sans-serif",
    body: "'Nunito Sans', system-ui, sans-serif",
    googleHref:
      "https://fonts.googleapis.com/css2?family=Poppins:wght@500;600;700;800&family=Nunito+Sans:wght@400;600;700&display=swap",
  },
  classic: {
    name: "Playfair Display + Source Sans",
    heading: "'Playfair Display', Georgia, serif",
    body: "'Source Sans 3', system-ui, sans-serif",
    googleHref:
      "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@500;600;700;800&family=Source+Sans+3:wght@400;600;700&display=swap",
  },
};

/* Hand-tuned palettes. `accent` is overridable per-project from the editor. */
export const THEMES = {
  aurora: {
    label: "Aurora",
    blurb: "Clean modern SaaS / startup look",
    font: "modern",
    accent: "#6d5efc",
    accent2: "#22d3ee",
    bg: "#ffffff",
    surface: "#f6f7fb",
    ink: "#0f1222",
    muted: "#5b6072",
    radius: "18px",
    heroStyle: "gradient",
    navStyle: "pill",
  },
  midnight: {
    label: "Midnight",
    blurb: "Dark, premium, high-contrast",
    font: "geometric",
    accent: "#7c5cff",
    accent2: "#36e0b0",
    bg: "#0b0d17",
    surface: "#141829",
    ink: "#f4f6ff",
    muted: "#9aa3c0",
    radius: "16px",
    heroStyle: "glow",
    navStyle: "solid",
    dark: true,
  },
  linen: {
    label: "Linen",
    blurb: "Warm, editorial, hospitality",
    font: "editorial",
    accent: "#b5562e",
    accent2: "#e0a458",
    bg: "#fbf7f0",
    surface: "#f3ebdd",
    ink: "#2a2018",
    muted: "#6f5f4d",
    radius: "10px",
    heroStyle: "image",
    navStyle: "minimal",
  },
  emerald: {
    label: "Emerald",
    blurb: "Trustworthy, professional services",
    font: "classic",
    accent: "#0f7a5a",
    accent2: "#c9a227",
    bg: "#ffffff",
    surface: "#f1f6f3",
    ink: "#10231c",
    muted: "#4f6b60",
    radius: "8px",
    heroStyle: "split",
    navStyle: "solid",
  },
  coral: {
    label: "Coral",
    blurb: "Friendly, bold, local & trades",
    font: "warm",
    accent: "#ff5a5f",
    accent2: "#ffb400",
    bg: "#ffffff",
    surface: "#fff4f1",
    ink: "#231414",
    muted: "#7a5b57",
    radius: "22px",
    heroStyle: "gradient",
    navStyle: "pill",
  },
  slate: {
    label: "Slate",
    blurb: "Minimal, corporate, understated",
    font: "modern",
    accent: "#2563eb",
    accent2: "#0ea5e9",
    bg: "#ffffff",
    surface: "#f4f6f9",
    ink: "#121826",
    muted: "#566074",
    radius: "6px",
    heroStyle: "split",
    navStyle: "minimal",
  },
};

export const THEME_KEYS = Object.keys(THEMES);

export function resolveTheme(key, accentOverride) {
  const base = THEMES[key] || THEMES.aurora;
  const theme = { ...base };
  if (accentOverride) theme.accent = accentOverride;
  theme.fontset = FONTS[theme.font] || FONTS.modern;
  return theme;
}
