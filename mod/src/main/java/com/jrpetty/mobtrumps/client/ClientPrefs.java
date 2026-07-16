package com.jrpetty.mobtrumps.client;

/** Session-remembered client UI preferences (not persisted to disk). */
public final class ClientPrefs {

    /** How big battle cards render: AUTO fits them to the window; the rest force a size. */
    public enum CardSize {
        AUTO("Auto"), SMALL("S"), MEDIUM("M"), LARGE("L");

        public final String label;

        CardSize(String label) {
            this.label = label;
        }

        public CardSize next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static volatile CardSize cardSize = CardSize.AUTO;

    private ClientPrefs() {
    }

    public static CardSize cardSize() {
        return cardSize;
    }

    public static void cycleCardSize() {
        cardSize = cardSize.next();
    }

    /**
     * The card scale to use given the fit-to-window scale. AUTO returns the fit;
     * fixed sizes clamp to the fit so a card can never overflow the window.
     */
    public static float resolveScale(float fitScale) {
        return switch (cardSize) {
            case AUTO -> fitScale;
            case SMALL -> Math.min(fitScale, 0.50f);
            case MEDIUM -> Math.min(fitScale, 0.68f);
            case LARGE -> Math.min(fitScale, 0.92f);
        };
    }
}
