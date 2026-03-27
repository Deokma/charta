package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for road scoring and completion in Tile Kingdoms.
 *
 * <h2>ASCII diagram legend</h2>
 * <pre>
 *   [X]  — ROAD_CROSS    (all 4 sides = road, endpoint)
 *   [S]  — ROAD_STRAIGHT (N-S or E-W depending on rotation)
 *   [C]  — ROAD_CURVE_SE (curve S-E)
 *   [T]  — ROAD_T        (T-junction, endpoint)
 *   [M]  — MONASTERY_ROAD_S (monastery with road to south)
 *   [K]  — CITY_CAP      (city on north, fields elsewhere)
 *   [KR] — CITY_CAP_ROAD_LR (city N, road E-W)
 *   ---  — horizontal road (E-W)
 *   |    — vertical road (N-S)
 *   *    — meeple of player 0
 *   #    — meeple of player 1
 * </pre>
 *
 * <h2>Coordinate system</h2>
 * <pre>
 *   X grows right, Y grows downward (S = +Y).
 *   Rotations are clockwise: rot=1 means the original North edge is now East.
 *
 *   ROAD_CURVE_SE edge map (base: N=F, E=R, S=R, W=F):
 *     rot=0: E=R, S=R  (SE corner)
 *     rot=1: S=R, W=R  (SW corner)
 *     rot=2: N=R, W=R  (NW corner)
 *     rot=3: N=R, E=R  (NE corner)
 *
 *   ROAD_STRAIGHT edge map (base: N=R, E=F, S=R, W=F):
 *     rot=0: N=R, S=R  (N-S)
 *     rot=1: E=R, W=R  (E-W)
 *
 *   ROAD_T edge map (base: N=F, E=R, S=R, W=R):
 *     rot=0: N=F, E=R, S=R, W=R
 *     rot=1: N=R, E=F, S=R, W=R
 *     rot=2: N=R, E=R, S=F, W=R
 *     rot=3: N=R, E=R, S=R, W=F
 * </pre>
 */
class RoadCompletionTest {

    private static final int N = 0, E = 1, S = 2, W = 3;
    private static final int FOLLOWERS_PER_PLAYER = 7;
    private static final int N_PLAYERS = 2;
    private static final int FULL = FOLLOWERS_PER_PLAYER;

    /** Mirror of TileKingdomsBoard.packPos — no direct reference to TileKingdomsGame. */
    private static long pos(int lx, int ly, int slot) {
        return (slot & 0xFF) | (((lx + 64) & 0xFF) << 8L) | (((ly + 64) & 0xFF) << 16L);
    }

    private TileKingdomsBoard board;
    private Map<Long, Integer> claims;
    private int[] scores;
    private int[] followersLeft;

    @BeforeEach
    void setUp() {
        board = new TileKingdomsBoard();
        claims = new LinkedHashMap<>();
        scores = new int[N_PLAYERS];
        followersLeft = new int[N_PLAYERS];
        Arrays.fill(followersLeft, FULL - 1); // 1 meeple already spent
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Basic cases
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic cases")
    class BasicCases {

        /**
         * <pre>
         *  (-1,0)       (0,0)        (1,0)
         *  [X]  ---  [S rot=1]  ---  [X]
         *              *E
         * Road: 3 tiles, meeple on E slot of the middle tile.
         * </pre>
         */
        @Test
        @DisplayName("Meeple returned and road scored — 3 tiles")
        void meepleReturnedAfterRoadCompletion() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1); // rot=1 => E-W
            claims.put(pos(0, 0, E), 0);

            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "3 tiles => 3 points");
            assertEquals(0, scores[1]);
            assertEquals(FULL, followersLeft[0], "Meeple returned");
            assertEquals(FULL - 1, followersLeft[1], "Player 1 unaffected");
            assertTrue(claims.isEmpty());
        }

        /**
         * <pre>
         *  (-1,0)       (0,0)        (1,0)
         *  [X]  ---  [S rot=1]  ---  ???
         *              *E
         * Right end is open — road not complete.
         * </pre>
         */
        @Test
        @DisplayName("Meeple stays — road not closed")
        void meepleNotReturnedForIncompleteRoad() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            claims.put(pos(0, 0, E), 0);

            board.scoreAndReturnFollowers(0, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(0, scores[0], "No points — road is open");
            assertEquals(FULL - 1, followersLeft[0], "Meeple not returned");
            assertFalse(claims.isEmpty());
        }

        /**
         * <pre>
         *  (-1,0)       (0,0)        (1,0)
         *  [X]  ---  [S rot=1]  ---  [X]
         * Road complete but no meeples.
         * </pre>
         */
        @Test
        @DisplayName("Completed road with no meeples — 0 points")
        void completedRoadWithNoClaimScoresNothing() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertArrayEquals(new int[]{0, 0}, scores);
            assertArrayEquals(new int[]{FULL - 1, FULL - 1}, followersLeft);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — Tie and dominance
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tie and dominance")
    class TieAndDominance {

        /**
         * <pre>
         *  (-1,0)       (0,0)        (1,0)
         *  [X]  ---  [S rot=1]  ---  [X]
         *             *E  #W
         * Both players on the same road — tie.
         * </pre>
         */
        @Test
        @DisplayName("Tie: both players score 3 points")
        void twoPlayersShareCompletedRoadScore() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, E), 0);
            claims.put(pos(0, 0, W), 1);

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "Tie — both score 3");
            assertEquals(3, scores[1]);
            assertEquals(FULL, followersLeft[0]);
            assertEquals(FULL, followersLeft[1]);
            assertTrue(claims.isEmpty());
        }

        /**
         * <pre>
         *  (-2,0)  (-1,0)   (0,0)   (1,0)   (2,0)
         *  [X] --- [S] --- [S] --- [S] --- [X]
         *           *E      *E      #E
         * Player 0: 2 meeples, Player 1: 1 meeple => Player 0 takes all 5 points.
         * </pre>
         */
        @Test
        @DisplayName("Dominance: majority meeples take all points")
        void dominantPlayerTakesAllPoints() {
            board.placeForced(-2, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(-1, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(2, 0, TileType.ROAD_CROSS, 0);

            claims.put(pos(-1, 0, E), 0);
            claims.put(pos(0, 0, E), 1);
            claims.put(pos(1, 0, E), 0);
            followersLeft[0] = FULL - 2;
            followersLeft[1] = FULL - 1;

            board.scoreAndReturnFollowers(2, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(5, scores[0], "Majority => all 5 points");
            assertEquals(0, scores[1], "Minority => 0");
            assertEquals(FULL, followersLeft[0], "Both meeples of player 0 returned");
            assertEquals(FULL, followersLeft[1], "Meeple of player 1 also returned");
            assertTrue(claims.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — Long and complex roads
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Long and complex roads")
    class LongAndComplexRoads {

        /**
         * Road of 7 tiles (maximum straight):
         * <pre>
         *  (-3,0) (-2,0) (-1,0) (0,0) (1,0) (2,0) (3,0)
         *  [X] --- [S] --- [S] --- [S] --- [S] --- [S] --- [X]
         *                          *E
         * </pre>
         */
        @Test
        @DisplayName("Long straight road of 7 tiles — 7 points")
        void longStraightRoadSevenTiles() {
            board.placeForced(-3, 0, TileType.ROAD_CROSS, 0);
            for (int x = -2; x <= 2; x++) {
                board.placeForced(x, 0, TileType.ROAD_STRAIGHT, 1); // rot=1 => E-W
            }
            claims.put(pos(0, 0, E), 0);
            board.placeForced(3, 0, TileType.ROAD_CROSS, 0);

            board.scoreAndReturnFollowers(3, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(7, scores[0], "7 tiles => 7 points");
            assertEquals(FULL, followersLeft[0]);
            assertTrue(claims.isEmpty());
        }

        /**
         * Two road segments from one crossroad:
         * <pre>
         *  Segment A: (-1,0)[X] — (0,0)[S r=1] — (1,0)[X]   (E-W, 3 tiles)
         *  Segment B: (-1,-1)[X] — (-1,0)[X] — (-1,1)[X]    (N-S, 2 tiles per arm)
         *
         * ROAD_CROSS is an endpoint — each arm is a separate road.
         * Segment A: 3 tiles. Segment B (N-arm of (-1,0)): 2 tiles.
         * </pre>
         */
        @Test
        @DisplayName("Two independent segments from one ROAD_CROSS — each scores separately")
        void lShapedRoadWithCurve() {
            // Segment A: horizontal through (0,0)
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);  // E-W
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, E), 0);

            // Segment B: vertical through (-1,0) — N-arm capped by (-1,-1) and (-1,1)
            board.placeForced(-1, -1, TileType.ROAD_CROSS, 0);
            board.placeForced(-1, 1, TileType.ROAD_CROSS, 0);
            claims.put(pos(-1, 0, N), 1); // N-arm of (-1,0)

            // Score segment A
            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);
            // Score segment B
            board.scoreAndReturnFollowers(-1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "Segment A: 3 tiles => 3 points");
            assertEquals(2, scores[1], "Segment B: 2 tiles => 2 points");
            assertEquals(FULL, followersLeft[0]);
            assertEquals(FULL, followersLeft[1]);
            assertTrue(claims.isEmpty());
        }

        /**
         * Road terminating into a city:
         * <pre>
         *  (-1,0)              (0,0)                  (1,0)
         *  [X]  ---  [CITY_CAP_ROAD_LR r=0]  ---  [CITY_THREE_ROAD r=1]
         *                      *E
         *
         * CITY_CAP_ROAD_LR r=0: N=City, E=R, W=R
         * CITY_THREE_ROAD r=1: W=City — road capped by city edge.
         * BFS from (0,0,E): crosses to (1,0,W) => W=City => road capped.
         * Tiles: {(-1,0), (0,0)} = 2 tiles, but scoring starts from (1,0) => 3 tiles counted.
         * </pre>
         */
        @Test
        @DisplayName("Road terminating into a city — complete and scored")
        void roadTerminatesInCity() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.CITY_CAP_ROAD_LR, 0); // N=City, E=R, W=R
            claims.put(pos(0, 0, E), 0);
            board.placeForced(1, 0, TileType.CITY_THREE_ROAD, 1);   // W=City

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "Road capped by city — 3 points");
            assertEquals(FULL, followersLeft[0]);
            assertTrue(claims.isEmpty());
        }

        /**
         * Road terminating at a monastery:
         * <pre>
         *  (-1,0)       (0,0)              (1,0)
         *  [X]  ---  [S rot=1]  ---  [MONASTERY_ROAD_S r=1]
         *              *E
         *
         * MONASTERY_ROAD_S r=1: all sides F — road capped naturally.
         * BFS from (0,0,E): crosses to (1,0,W) => W=F => road capped.
         * </pre>
         */
        @Test
        @DisplayName("Road terminating at a monastery — complete and scored")
        void roadTerminatesAtMonastery() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);    // E-W
            claims.put(pos(0, 0, E), 0);
            board.placeForced(1, 0, TileType.MONASTERY_ROAD_S, 1); // all sides F

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "Road capped by monastery — 3 points");
            assertEquals(FULL, followersLeft[0]);
            assertTrue(claims.isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — Multiple independent roads
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Multiple independent roads")
    class MultipleIndependentRoads {

        /**
         * Two parallel roads:
         * <pre>
         *  Road A (y=0):  [X] --- [S] --- [X]   *E at (0,0)
         *  Road B (y=2):  [X] --- [S] --- [X]   #E at (0,2)
         * </pre>
         */
        @Test
        @DisplayName("Two parallel roads — each player scores 3 points independently")
        void twoParallelRoadsIndependentScoring() {
            // Road A
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, E), 0);

            // Road B
            board.placeForced(-1, 2, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 2, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 2, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 2, E), 1);

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);
            board.scoreAndReturnFollowers(1, 2, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "Player 0: road A = 3 points");
            assertEquals(3, scores[1], "Player 1: road B = 3 points");
            assertEquals(FULL, followersLeft[0]);
            assertEquals(FULL, followersLeft[1]);
            assertTrue(claims.isEmpty());
        }

        /**
         * One road complete, one open:
         * <pre>
         *  Road A (y=0):  [X] --- [S] --- [X]   *E at (0,0)  ← complete
         *  Road B (y=2):  [X] --- [S] --- ???   #E at (0,2)  ← open
         * </pre>
         */
        @Test
        @DisplayName("One road complete, one open — only the first scores")
        void oneCompleteOneIncomplete() {
            // Road A — complete
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, E), 0);

            // Road B — open (no right endpoint)
            board.placeForced(-1, 2, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 2, TileType.ROAD_STRAIGHT, 1);
            claims.put(pos(0, 2, E), 1);

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);
            board.scoreAndReturnFollowers(0, 2, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3, scores[0], "Road A complete => 3 points");
            assertEquals(0, scores[1], "Road B open => 0 points");
            assertEquals(FULL, followersLeft[0], "Meeple A returned");
            assertEquals(FULL - 1, followersLeft[1], "Meeple B not returned");
            assertFalse(claims.containsKey(pos(0, 0, E)), "Claim A removed");
            assertTrue(claims.containsKey(pos(0, 2, E)), "Claim B remains");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        /**
         * <pre>
         *  (0,0)
         *  [X]
         *  *N
         * All 4 sides are road, but no neighbours => road not complete.
         * </pre>
         */
        @Test
        @DisplayName("Single ROAD_CROSS with no neighbours — road not complete")
        void singleCrossroadAloneIsIncomplete() {
            board.placeForced(0, 0, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, N), 0);

            board.scoreAndReturnFollowers(0, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(0, scores[0], "No neighbours => road open");
            assertEquals(FULL - 1, followersLeft[0], "Meeple not returned");
            assertFalse(claims.isEmpty());
        }

        /**
         * Meeple on a field slot (not a road):
         * <pre>
         *  (-1,0)       (0,0)        (1,0)
         *  [X]  ---  [S rot=1]  ---  [X]
         *              *N   ← N slot = FIELD
         * </pre>
         */
        @Test
        @DisplayName("Meeple on field slot — not counted for road scoring")
        void meepleOnFieldSlotNotCountedForRoad() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1); // rot=1: E=R, W=R, N=F, S=F
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);

            // N slot of ROAD_STRAIGHT rot=1 is a field, not a road
            claims.put(pos(0, 0, N), 0);

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(0, scores[0], "Meeple on field does not count for road");
            assertEquals(FULL - 1, followersLeft[0], "Meeple on field not returned when road closes");
        }

        /**
         * Two meeples of the same player on one road — both returned, points not doubled:
         * <pre>
         *  (-2,0) (-1,0)  (0,0)  (1,0)  (2,0)
         *  [X] --- [S] --- [S] --- [S] --- [X]
         *           *E      *E
         * </pre>
         */
        @Test
        @DisplayName("Two meeples of same player on one road — points not doubled, both returned")
        void twoMeeplesOfSamePlayerOnOneRoad() {
            board.placeForced(-2, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(-1, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(2, 0, TileType.ROAD_CROSS, 0);

            claims.put(pos(-1, 0, E), 0);
            claims.put(pos(0, 0, E), 0);
            followersLeft[0] = FULL - 2;

            board.scoreAndReturnFollowers(2, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(5, scores[0], "5 tiles => 5 points (not doubled)");
            assertEquals(FULL, followersLeft[0], "Both meeples returned");
            assertTrue(claims.isEmpty());
        }

        /**
         * Scoring triggered from an empty cell:
         * <pre>
         *  (-1,0)       (0,0)        (1,0)
         *  [X]  ---  [S rot=1]  ---  [X]
         *              *E
         * scoreAndReturnFollowers called from (0,1) — empty cell.
         * Nothing should happen.
         * </pre>
         */
        @Test
        @DisplayName("Scoring from empty cell — nothing happens")
        void scoringFromEmptyCellDoesNothing() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, E), 0);

            board.scoreAndReturnFollowers(0, 1, claims, N_PLAYERS, scores, followersLeft);

            assertArrayEquals(new int[]{0, 0}, scores, "Empty cell => 0 points");
            assertEquals(FULL - 1, followersLeft[0], "Meeple untouched");
            assertFalse(claims.isEmpty(), "Claim remains");
        }
    }

    @Test
    @DisplayName("Large closed road loop with turns and two players — correct scoring")
    void largeClosedRoadWithTurnsAndIntersection() {
    /*
        Coordinates (x,y), Y grows downward (S=+Y).
        A closed rectangular loop of 8 tiles:

        (0,0)[C r=0] --E-- (1,0)[S r=1] --E-- (2,0)[C r=1]
             |S                                      |S
        (0,1)[S r=0]                           (2,1)[S r=0]
             |S                                      |S
        (0,2)[C r=3] --E-- (1,2)[S r=1] --E-- (2,2)[C r=2]

        ROAD_CURVE_SE rotations:
          (0,0) r=0: E=R, S=R
          (2,0) r=1: S=R, W=R
          (2,2) r=2: N=R, W=R
          (0,2) r=3: N=R, E=R

        ROAD_STRAIGHT rotations:
          (1,0) r=1: E-W
          (0,1) r=0: N-S
          (2,1) r=0: N-S
          (1,2) r=1: E-W

        Total: 8 tiles, closed loop.
        Player 0: 2 meeples, Player 1: 1 meeple => Player 0 has majority.
    */
        board.placeForced(0, 0, TileType.ROAD_CURVE_SE, 0); // E=R, S=R
        board.placeForced(1, 0, TileType.ROAD_STRAIGHT, 1); // E-W
        board.placeForced(2, 0, TileType.ROAD_CURVE_SE, 1); // S=R, W=R

        board.placeForced(0, 1, TileType.ROAD_STRAIGHT, 0); // N-S
        board.placeForced(2, 1, TileType.ROAD_STRAIGHT, 0); // N-S

        board.placeForced(0, 2, TileType.ROAD_CURVE_SE, 3); // N=R, E=R
        board.placeForced(1, 2, TileType.ROAD_STRAIGHT, 1); // E-W
        board.placeForced(2, 2, TileType.ROAD_CURVE_SE, 2); // N=R, W=R

        // Player 0: 2 meeples, Player 1: 1 meeple
        claims.put(pos(1, 0, E), 0); // E slot of top straight
        claims.put(pos(0, 1, N), 0); // N slot of left vertical
        claims.put(pos(2, 1, N), 1); // N slot of right vertical
        followersLeft[0] = FULL - 2;
        followersLeft[1] = FULL - 1;

        board.scoreAndReturnFollowers(1, 2, claims, N_PLAYERS, scores, followersLeft);

        assertEquals(8, scores[0], "Closed loop of 8 tiles, majority => all 8 points");
        assertEquals(0, scores[1]);
        assertEquals(FULL, followersLeft[0], "Both meeples of player 0 returned");
        assertEquals(FULL, followersLeft[1], "Meeple of player 1 returned");
        assertTrue(claims.isEmpty(), "All claims removed");
    }

    @Test
    @DisplayName("Two independent closed loops of 6 tiles — each player scores 6 points")
    void twoIndependentLoopsFiveEach() {
    /*
        Two independent closed loops of 6 tiles each.
        Y grows downward (S=+Y).

        Loop A (player 0):
        (0,0)[C r=0] --E-- (1,0)[S r=1] --E-- (2,0)[C r=1]
             |S                                      |S
        (0,1)[C r=3] --E-- (1,1)[S r=1] --E-- (2,1)[C r=2]

          (0,0) r=0: E=R, S=R
          (1,0) r=1: E=R, W=R
          (2,0) r=1: S=R, W=R
          (2,1) r=2: N=R, W=R
          (1,1) r=1: E=R, W=R
          (0,1) r=3: N=R, E=R
        Connections: (0,0)E→(1,0)W, (1,0)E→(2,0)W, (2,0)S→(2,1)N,
                     (2,1)W→(1,1)E, (1,1)W→(0,1)E, (0,1)N→(0,0)S. Closed.

        Loop B (player 1): same structure shifted +4 on X.

        Meeples:
          Player 0: pos(1,0,E) — top straight of loop A
          Player 1: pos(5,0,E) — top straight of loop B
    */
        // Loop A
        board.placeForced(0, 0, TileType.ROAD_CURVE_SE, 0); // E=R, S=R
        board.placeForced(1, 0, TileType.ROAD_STRAIGHT, 1); // E=R, W=R
        board.placeForced(2, 0, TileType.ROAD_CURVE_SE, 1); // S=R, W=R
        board.placeForced(2, 1, TileType.ROAD_CURVE_SE, 2); // N=R, W=R
        board.placeForced(1, 1, TileType.ROAD_STRAIGHT, 1); // E=R, W=R
        board.placeForced(0, 1, TileType.ROAD_CURVE_SE, 3); // N=R, E=R

        claims.put(pos(1, 0, E), 0); // player 0 meeple on top straight

        // Loop B
        board.placeForced(4, 0, TileType.ROAD_CURVE_SE, 0); // E=R, S=R
        board.placeForced(5, 0, TileType.ROAD_STRAIGHT, 1); // E=R, W=R
        board.placeForced(6, 0, TileType.ROAD_CURVE_SE, 1); // S=R, W=R
        board.placeForced(6, 1, TileType.ROAD_CURVE_SE, 2); // N=R, W=R
        board.placeForced(5, 1, TileType.ROAD_STRAIGHT, 1); // E=R, W=R
        board.placeForced(4, 1, TileType.ROAD_CURVE_SE, 3); // N=R, E=R

        claims.put(pos(5, 0, E), 1); // player 1 meeple on top straight

        board.scoreAndReturnFollowers(0, 1, claims, N_PLAYERS, scores, followersLeft);
        board.scoreAndReturnFollowers(4, 1, claims, N_PLAYERS, scores, followersLeft);

        assertEquals(6, scores[0], "Loop A: 6 tiles => 6 points");
        assertEquals(6, scores[1], "Loop B: 6 tiles => 6 points");
        assertEquals(FULL, followersLeft[0], "Player 0 meeple returned");
        assertEquals(FULL, followersLeft[1], "Player 1 meeple returned");
        assertTrue(claims.isEmpty(), "All claims removed");
    }

    @Test
    @DisplayName("Two independent roads of 5 tiles with turns — each player scores 5 points")
    void complexRoadNetworkWithIntersectionsAndTurns() {
    /*
        Two independent roads of 5 tiles each, each capped by two ROAD_CROSS endpoints.
        Y grows downward (S=+Y).

        Road A (player 0):
          (0,0)[X] --S-- (0,1)[C r=3] --E-- (1,1)[S r=1] --E-- (2,1)[C r=2] --N-- (2,0)[X]

          (0,0)[X]  S-arm → (0,1) N slot
          (0,1) r=3: N=R, E=R  → connects N to (0,0), E to (1,1)
          (1,1) r=1: E=R, W=R  → connects W to (0,1), E to (2,1)
          (2,1) r=2: N=R, W=R  → connects W to (1,1), N to (2,0)
          (2,0)[X]  S-arm → (2,1) N slot
          Tiles: (0,0), (0,1), (1,1), (2,1), (2,0) = 5 tiles. Closed.

        Road B (player 1): same structure shifted +5 on X.
          (5,0)[X] --S-- (5,1)[C r=3] --E-- (6,1)[S r=1] --E-- (7,1)[C r=2] --N-- (7,0)[X]

        Meeples:
          Player 0: pos(1,1,E) — horizontal straight of road A
          Player 1: pos(6,1,E) — horizontal straight of road B
    */
        // Road A
        board.placeForced(0, 0, TileType.ROAD_CROSS, 0);    // endpoint
        board.placeForced(0, 1, TileType.ROAD_CURVE_SE, 3); // N=R, E=R
        board.placeForced(1, 1, TileType.ROAD_STRAIGHT, 1); // E=R, W=R
        board.placeForced(2, 1, TileType.ROAD_CURVE_SE, 2); // N=R, W=R
        board.placeForced(2, 0, TileType.ROAD_CROSS, 0);    // endpoint

        claims.put(pos(1, 1, E), 0); // player 0 meeple on horizontal straight

        // Road B
        board.placeForced(5, 0, TileType.ROAD_CROSS, 0);    // endpoint
        board.placeForced(5, 1, TileType.ROAD_CURVE_SE, 3); // N=R, E=R
        board.placeForced(6, 1, TileType.ROAD_STRAIGHT, 1); // E=R, W=R
        board.placeForced(7, 1, TileType.ROAD_CURVE_SE, 2); // N=R, W=R
        board.placeForced(7, 0, TileType.ROAD_CROSS, 0);    // endpoint

        claims.put(pos(6, 1, E), 1); // player 1 meeple on horizontal straight

        board.scoreAndReturnFollowers(2, 0, claims, N_PLAYERS, scores, followersLeft);
        board.scoreAndReturnFollowers(7, 0, claims, N_PLAYERS, scores, followersLeft);

        assertEquals(5, scores[0], "Road A: 5 tiles => 5 points");
        assertEquals(5, scores[1], "Road B: 5 tiles => 5 points");
        assertEquals(FULL, followersLeft[0], "Player 0 meeple returned");
        assertEquals(FULL, followersLeft[1], "Player 1 meeple returned");
        assertTrue(claims.isEmpty(), "All claims removed");
    }
}
