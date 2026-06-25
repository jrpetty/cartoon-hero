/* PitchSite — curated stock photo suggestions.
 * When a client has no photos to hand on the call, offer a small set of
 * tasteful, royalty-free Unsplash images matched to their industry so the
 * site never looks empty. All are landscape, free to use under the Unsplash
 * licence. Selecting one just sets an image URL on the project. */

const U = (id, w = 1200) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&q=80`;

export const STOCK = {
  plumbing: {
    label: "Plumbing & heating",
    match: ["plumb", "heat", "boiler", "gas", "drain"],
    photos: ["1607472586893-edb57bdc0e39", "1581244277943-fe4a9c777189", "1585704032915-c3400ca199e7", "1558618666-fcd25c85cd64", "1521207418485-99c705420785", "1504328345606-18bbc8c9d7d1"],
  },
  trades: {
    label: "Trades & construction",
    match: ["electric", "build", "construct", "carpenter", "roof", "paint", "landscap", "garden", "handyman", "joiner", "tiler", "plaster"],
    photos: ["1504307651254-35680f356dfd", "1503387762-592deb58ef4e", "1521791136064-7986c2920216", "1572981779307-38b8cabb2407", "1416879595882-3373a0480b5b", "1530124566582-a618bc2615dc"],
  },
  restaurant: {
    label: "Food & hospitality",
    match: ["restaurant", "cafe", "café", "bistro", "diner", "pizz", "italian", "kitchen", "food", "coffee", "bakery", "bar"],
    photos: ["1517248135467-4c7edcad34c4", "1414235077428-338989a2e8c0", "1555396273-367ea4eb4db5", "1481931098730-318b6f776db0", "1559339352-11d035aa65de", "1504674900247-0877df9cc836"],
  },
  salon: {
    label: "Salon & beauty",
    match: ["salon", "hair", "barber", "beauty", "nails", "spa", "lash", "aesthetic"],
    photos: ["1560066984-138dadb4c035", "1521590832167-7bcbfaa6381f", "1522337660859-02fbefca4702", "1487412947147-5cebf100ffc2", "1503951914875-452162b0f3f1", "1633681926022-84c23e8cb2d6"],
  },
  fitness: {
    label: "Fitness & wellbeing",
    match: ["gym", "fitness", "train", "yoga", "pilates", "crossfit", "coach"],
    photos: ["1534438327276-14e5300c3a48", "1517836357463-d25dfeac3438", "1571019613454-1cb2f99b2d8b", "1518611012118-696072aa579a", "1599058917212-d750089bc07e", "1546483875-ad9014c88eba"],
  },
  professional: {
    label: "Professional services",
    match: ["account", "law", "solicitor", "consult", "agency", "financ", "advisor", "estate", "insur", "mortgage", "recruit", "marketing", "office"],
    photos: ["1497366754035-f200968a6e72", "1486406146926-c627a92ad1ab", "1521737604893-d14cc237f11d", "1551836022-d5d88e9218df", "1454165804606-c3d57bc86b40", "1556761175-5973dc0f32e7"],
  },
  health: {
    label: "Health & care",
    match: ["dental", "dentist", "clinic", "physio", "chiro", "therap", "medical", "health", "doctor", "optician", "vet"],
    photos: ["1576091160550-2173dba999ef", "1519494026892-80bbd2d6fd0d", "1612349317150-e413f6a5b16d", "1581056771107-24ca5f033842", "1505751172876-fa1923c5c528", "1538108149393-fbbd81895907"],
  },
  retail: {
    label: "Shops & retail",
    match: ["shop", "store", "boutique", "retail", "florist", "gift", "jewel"],
    photos: ["1441986300917-64674bd600d8", "1472851294608-062f824d29cc", "1567401893414-76b7b1e5a7a5", "1483985988355-763728e1935b", "1556905055-8f358a7a47b2", "1607082348824-0a96f2a4b9da"],
  },
  generic: {
    label: "General / business",
    match: [],
    photos: ["1497366216548-37526070297c", "1600880292203-757bb62b4baf", "1521737711867-e3b97375f902", "1542744173-8e7e53415bb0", "1556761175-b413da4baf72", "1531973576160-7125cd663d86"],
  },
};

export const STOCK_KEYS = Object.keys(STOCK);

/* Best category for a free-text hint (industry or business name). */
export function suggestCategory(hint = "") {
  const t = hint.toLowerCase();
  let best = "generic", score = 0;
  for (const [key, def] of Object.entries(STOCK)) {
    const s = def.match.reduce((n, w) => (t.includes(w) ? n + 1 : n), 0);
    if (s > score) { score = s; best = key; }
  }
  return best;
}

export function photosFor(categoryKey) {
  return (STOCK[categoryKey] || STOCK.generic).photos.map((id) => U(id));
}
