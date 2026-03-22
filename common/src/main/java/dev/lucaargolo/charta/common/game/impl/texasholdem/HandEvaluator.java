package dev.lucaargolo.charta.common.game.impl.texasholdem;

import dev.lucaargolo.charta.common.game.Ranks;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Rank;

import java.util.*;

/**
 * Evaluates the best 5-card poker hand from a set of 2–7 cards.
 *
 * <h3>Scoring</h3>
 * <pre>
 *   score = category × BASE + pack5(orderedRanks)
 * </pre>
 *
 * <table>
 *   <tr><th>Category</th><th>Value</th></tr>
 *   <tr><td>Straight Flush / Royal Flush</td><td>8</td></tr>
 *   <tr><td>Four of a Kind</td><td>7</td></tr>
 *   <tr><td>Full House</td><td>6</td></tr>
 *   <tr><td>Flush</td><td>5</td></tr>
 *   <tr><td>Straight</td><td>4</td></tr>
 *   <tr><td>Three of a Kind</td><td>3</td></tr>
 *   <tr><td>Two Pair</td><td>2</td></tr>
 *   <tr><td>One Pair</td><td>1</td></tr>
 *   <tr><td>High Card</td><td>0</td></tr>
 * </table>
 *
 * <p>pack5 encodes five rank values big-endian in base-100,
 * so the maximum tiebreaker value ≈ 1.41×10<sup>9</sup> &lt; BASE = 10<sup>10</sup>.</p>
 */
public final class HandEvaluator {

    /** Multiplier separating hand categories from tiebreaker ranks. */
    public static final long BASE = 10_000_000_000L;

    /** Maximum possible score (Ace-high straight flush). */
    public static final long MAX_SCORE = 9L * BASE;

    private HandEvaluator() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Evaluates the best 5-card hand among all C(n,5) combinations from {@code cards}.
     *
     * @param cards 5–7 cards to evaluate
     * @return the best hand score; {@code Long.MIN_VALUE} if fewer than 5 cards provided
     */
    public static long evaluate(List<Card> cards) {
        int n = cards.size();
        if (n < 5) return Long.MIN_VALUE;

        long best = Long.MIN_VALUE;
        for (int a = 0;     a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n;     e++) {
                            long score = eval5(cards.get(a), cards.get(b),
                                    cards.get(c), cards.get(d), cards.get(e));
                            if (score > best) best = score;
                        }
        return best;
    }

    /**
     * Returns a human-readable name for the best hand in {@code cards}.
     *
     * @param cards 5–7 cards
     * @return hand name string (e.g. {@code "Full House"}, {@code "Royal Flush"})
     */
    public static String getHandName(List<Card> cards) {
        long best = evaluate(cards);
        if (best <= 0) return "High Card";

        int category = (int) (best / BASE);
        return switch (category) {
            case 8 -> isRoyalFlush(best) ? "Royal Flush" : "Straight Flush";
            case 7 -> "Four of a Kind";
            case 6 -> "Full House";
            case 5 -> "Flush";
            case 4 -> "Straight";
            case 3 -> "Three of a Kind";
            case 2 -> "Two Pair";
            case 1 -> "One Pair";
            default -> "High Card";
        };
    }

    // -------------------------------------------------------------------------
    // Core 5-card evaluator
    // -------------------------------------------------------------------------

    private static long eval5(Card c1, Card c2, Card c3, Card c4, Card c5) {
        int[] ranks = { rankValue(c1), rankValue(c2), rankValue(c3), rankValue(c4), rankValue(c5) };
        Arrays.sort(ranks); // ascending

        boolean flush    = isFlush(c1, c2, c3, c4, c5);
        boolean straight = isStraight(ranks);

        // Group cards by rank: primary sort = count desc, secondary = rank desc
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int r : ranks) frequency.merge(r, 1, Integer::sum);

        List<Map.Entry<Integer, Integer>> groups = new ArrayList<>(frequency.entrySet());
        groups.sort((a, b) -> a.getValue().equals(b.getValue())
                ? b.getKey()   - a.getKey()   // same count → higher rank first
                : b.getValue() - a.getValue()); // higher count first

        // Build rank ordering for pack5: repeat each rank by its group count
        int[] ordered = new int[5];
        int pos = 0;
        for (Map.Entry<Integer, Integer> entry : groups) {
            for (int i = 0; i < entry.getValue(); i++) {
                ordered[pos++] = entry.getKey();
            }
        }

        int topCount   = groups.get(0).getValue();
        boolean hasPair     = groups.stream().anyMatch(e -> e.getValue() == 2);
        boolean hasTwoPairs = groups.stream().filter(e -> e.getValue() == 2).count() >= 2;
        boolean hasTrips    = topCount == 3;
        boolean hasQuads    = topCount == 4;
        boolean isFullHouse = hasTrips && hasPair;

        if (straight && flush)  return 8 * BASE + straightPack(ranks);
        if (hasQuads)           return 7 * BASE + pack5(ordered);
        if (isFullHouse)        return 6 * BASE + pack5(ordered);
        if (flush)              return 5 * BASE + pack5(ranks[4], ranks[3], ranks[2], ranks[1], ranks[0]);
        if (straight)           return 4 * BASE + straightPack(ranks);
        if (hasTrips)           return 3 * BASE + pack5(ordered);
        if (hasTwoPairs)        return 2 * BASE + pack5(ordered);
        if (hasPair)            return 1 * BASE + pack5(ordered);
        return                          pack5(ranks[4], ranks[3], ranks[2], ranks[1], ranks[0]);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /** Packs 5 rank values big-endian in base-100 for tiebreaking. */
    private static long pack5(int[] ord) {
        return pack5(ord[0], ord[1], ord[2], ord[3], ord[4]);
    }

    private static long pack5(int a, int b, int c, int d, int e) {
        return ((((long) a * 100 + b) * 100 + c) * 100 + d) * 100 + e;
    }

    /**
     * Packs straight ranks, handling the wheel (A-2-3-4-5 → 5-high).
     * In a wheel, sorted = [2,3,4,5,14] → we treat the top card as 5.
     */
    private static long straightPack(int[] sorted) {
        if (isWheelStraight(sorted)) {
            return pack5(5, 4, 3, 2, 1);
        }
        return pack5(sorted[4], sorted[3], sorted[2], sorted[1], sorted[0]);
    }

    private static boolean isFlush(Card c1, Card c2, Card c3, Card c4, Card c5) {
        return c1.suit() == c2.suit()
                && c2.suit() == c3.suit()
                && c3.suit() == c4.suit()
                && c4.suit() == c5.suit();
    }

    private static boolean isStraight(int[] sorted) {
        if (isWheelStraight(sorted)) return true;
        return sorted[4] - sorted[0] == 4
                && sorted[1] == sorted[0] + 1
                && sorted[2] == sorted[1] + 1
                && sorted[3] == sorted[2] + 1;
    }

    /** A-2-3-4-5 ("wheel") straight: ace counts as low. */
    private static boolean isWheelStraight(int[] sorted) {
        return sorted[4] == 14
                && sorted[0] == 2 && sorted[1] == 3
                && sorted[2] == 4 && sorted[3] == 5;
    }

    private static boolean isRoyalFlush(long score) {
        // Top card of the straight-flush tiebreaker is at rank position 0
        // pack5 of [14,13,12,11,10] → leading value = 14
        return (score % BASE) / (long) Math.pow(100, 4) == 14;
    }

    /**
     * Returns the numeric rank value (2–14, Ace = 14).
     */
    static int rankValue(Card card) {
        Rank rank = card.rank();
        if (rank == Ranks.ACE) return 14;
        return rank.ordinal(); // TWO = 2 … KING = 13
    }
}