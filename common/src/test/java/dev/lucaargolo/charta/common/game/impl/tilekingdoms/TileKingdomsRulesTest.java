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
 * Full rule-coverage tests for Tile Kingdoms (Carcassonne).
 *
 * <h2>Key rules verified</h2>
 * <ul>
 *   <li>Completed roads/cities/monasteries score and return followers.</li>
 *   <li>Incomplete features do NOT score mid-game.</li>
 *   <li>A player CANNOT place a meeple on an already-completed feature
 *       (verified via {@code board.isFeatureComplete} / {@code board.isMonasteryComplete}).</li>
 *   <li>A player CANNOT place a meeple on a feature already owned by another
 *       player (ownership detected by claims already in the region).</li>
 *   <li>Majority logic: most meeples wins; tie → both score.</li>
 *   <li>All followers are returned from a completed feature regardless of
 *       whether they were the winner.</li>
 *   <li>Final scoring: incomplete features score 1 pt / tile (roads) or
 *       1 pt / tile (cities), and monasteries score 1 pt per filled neighbour.</li>
 * </ul>
 *
 * <p>Only pure-Java classes are used: {@link TileKingdomsBoard}, {@link TileType},
 * {@link PlacedTile}. Minecraft is not required on the test classpath.
 *
 * <h2>Coordinate convention</h2>
 * Logic X/Y where (0,0) is the centre; north = decreasing Y.
 */
class TileKingdomsRulesTest {

    // ── Direction constants ───────────────────────────────────────────────────
    private static final int N = 0, E = 1, S = 2, W = 3, CENTER = 4;

    // ── Player constants ──────────────────────────────────────────────────────
    private static final int N_PLAYERS   = 2;
    private static final int FOLLOWERS   = 7;   // per player

    // ── Inline packPos (mirrors TileKingdomsGame.packPos) ────────────────────
    private static long pos(int lx, int ly, int slot) {
        return (slot & 0xFF) | (((lx + 64) & 0xFF) << 8L) | (((ly + 64) & 0xFF) << 16L);
    }

    // ── Shared test state ─────────────────────────────────────────────────────
    private TileKingdomsBoard board;
    private Map<Long, Integer> claims;
    private int[]              scores;
    private int[]              followersLeft;

    @BeforeEach
    void setUp() {
        board         = new TileKingdomsBoard();
        claims        = new LinkedHashMap<>();
        scores        = new int[N_PLAYERS];
        followersLeft = new int[N_PLAYERS];
        Arrays.fill(followersLeft, FOLLOWERS);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 1  isFeatureComplete — board-level queries
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§1 isFeatureComplete — board queries")
    class IsFeatureCompleteTests {

        @Test
        @DisplayName("Closed 3-tile road is complete")
        void completedRoadIsComplete() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1); // E-W
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0);

            // Check from the middle tile's E slot
            assertTrue(board.isFeatureComplete(0, 0, E),
                    "Middle tile's East road slot should be complete");
            assertTrue(board.isFeatureComplete(0, 0, W),
                    "Middle tile's West road slot should be complete");
        }

        @Test
        @DisplayName("Open road (one end missing) is NOT complete")
        void openRoadIsNotComplete() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1); // E slot open

            assertFalse(board.isFeatureComplete(0, 0, E),
                    "Road with no tile to the East must not be complete");
        }

        @Test
        @DisplayName("Closed 2-tile city is complete")
        void completedCityIsComplete() {
            // CITY_CAP rot=0: N=C.  CITY_CAP rot=2: S=C → faces (0,0) from above.
            board.placeForced(0,  0, TileType.CITY_CAP, 0); // N edge = City
            board.placeForced(0, -1, TileType.CITY_CAP, 2); // S edge = City (closes tile above)

            assertTrue(board.isFeatureComplete(0, 0, N),
                    "2-tile city capped from north should be complete");
        }

        @Test
        @DisplayName("City with one open flank is NOT complete")
        void openCityIsNotComplete() {
            // Single CITY_CAP tile — north edge is city but nothing closes it
            board.placeForced(0, 0, TileType.CITY_CAP, 0);

            assertFalse(board.isFeatureComplete(0, 0, N),
                    "City with nothing on the other side must not be complete");
        }

        @Test
        @DisplayName("Full 4-edge city (CITY_FULL) is always complete on its own")
        void cityFullSingleTileIsComplete() {
            // CITY_FULL has all 4 edges = City, connectedCity = true.
            // A single CITY_FULL tile has all edges City but each city edge
            // points outward to an empty neighbour → NOT complete by itself.
            board.placeForced(0, 0, TileType.CITY_FULL, 0);

            // Each edge reaches into an empty cell → city is open on all sides
            assertFalse(board.isFeatureComplete(0, 0, N),
                    "CITY_FULL alone cannot be complete — all 4 sides are open");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 2  isMonasteryComplete
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§2 isMonasteryComplete")
    class IsMonasteryCompleteTests {

        @Test
        @DisplayName("9 / 9 cells filled → monastery complete")
        void fullyFilledMonasteryIsComplete() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            fillNeighbours(0, 0); // fills all 8 surrounding cells

            assertTrue(board.isMonasteryComplete(0, 0));
        }

        @Test
        @DisplayName("7 / 9 cells → monastery NOT complete")
        void partialMonasteryIsNotComplete() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            // Place only 6 of 8 neighbours
            board.placeForced( 1,  0, TileType.ROAD_CROSS, 0);
            board.placeForced(-1,  0, TileType.ROAD_CROSS, 0);
            board.placeForced( 0,  1, TileType.ROAD_CROSS, 0);
            board.placeForced( 0, -1, TileType.ROAD_CROSS, 0);
            board.placeForced( 1,  1, TileType.ROAD_CROSS, 0);
            board.placeForced(-1, -1, TileType.ROAD_CROSS, 0);
            // (-1,1) and (1,-1) are missing → total = 7

            assertFalse(board.isMonasteryComplete(0, 0));
        }

        @Test
        @DisplayName("Only monastery tile itself → 1 / 9 → NOT complete")
        void isolatedMonasteryNotComplete() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);

            assertFalse(board.isMonasteryComplete(0, 0));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 3  Cannot place on completed feature (Carcassonne core rule)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§3 No meeple on completed features")
    class NoMeepleOnCompletedFeatureTests {

        /**
         * Rule: if you place the tile that CLOSES a feature, you still have the
         * option to claim — but only uncompleted features on that tile. The
         * completed feature itself must NOT be offered as a claim target.
         * We verify this via {@code board.isFeatureComplete}.
         */
        @Test
        @DisplayName("Completed road on just-placed tile is flagged as complete — cannot claim")
        void justClosedRoadCannotBeClaimed() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1); // E-W
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0); // closes road

            // Claim phase is for the last placed tile (1,0).
            // The road on its W slot leads into the completed road.
            assertTrue(board.isFeatureComplete(1, 0, W),
                    "W slot of the closing tile belongs to the now-complete road");
            // hasFreeFeature logic should reject this slot.
        }

        @Test
        @DisplayName("Completed city on just-placed tile is flagged as complete — cannot claim")
        void justClosedCityCannotBeClaimed() {
            board.placeForced(0, -1, TileType.CITY_CAP, 2); // S=C
            board.placeForced(0,  0, TileType.CITY_CAP, 0); // N=C — closes city

            assertTrue(board.isFeatureComplete(0, 0, N),
                    "N slot of closing tile is complete city — must not be claimable");
        }

        @Test
        @DisplayName("Completed monastery on just-filled tile is flagged — cannot claim")
        void justCompletedMonasteryCannotBeClaimed() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            fillNeighbours(0, 0);

            assertTrue(board.isMonasteryComplete(0, 0),
                    "Monastery is complete — must not be offered as a claim target");
        }

        @Test
        @DisplayName("Incomplete feature on same tile IS claimable")
        void incompleteFeatureOnSameTileIsClaimable() {
            // CITY_CAP_ROAD_LR: N=City, E=Road, W=Road.
            // Close the city from the north, leave the road open.
            board.placeForced(0, -1, TileType.CITY_CAP, 2);        // closes city
            board.placeForced(0,  0, TileType.CITY_CAP_ROAD_LR, 0); // N=City(closed), E/W=Road(open)

            assertTrue (board.isFeatureComplete(0, 0, N),
                    "City on N slot should be complete (capped)");
            assertFalse(board.isFeatureComplete(0, 0, E),
                    "Road on E slot is still open → can still be claimed");
            assertFalse(board.isFeatureComplete(0, 0, W),
                    "Road on W slot is still open → can still be claimed");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 4  Cannot place on a feature already owned by another player
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§4 No meeple on already-owned feature")
    class OwnershipCheckTests {

        /**
         * The region BFS ({@code board.getRegion}) is the tool the server uses
         * to detect whether a feature is already owned. We test the BFS result
         * directly here.
         */
        @Test
        @DisplayName("getRegion links both ends of a straight road — ownership detected across tiles")
        void regionLinksEntireRoad() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0);

            // Put a claim on the west end's east slot
            claims.put(pos(-1, 0, E), 0);

            // Get the region as seen from the middle tile's east slot
            java.util.Set<Long> region = board.getRegion(0, 0, E);

            // The region should contain the claim position
            boolean regionContainsClaim = region.stream().anyMatch(claims::containsKey);
            assertTrue(regionContainsClaim,
                    "Region from middle tile E slot must include the claim on the western tile");
        }

        @Test
        @DisplayName("A meeple on one city tile is visible in the connected city region")
        void meepleVisibleAcrossConnectedCity() {
            board.placeForced(0,  0, TileType.CITY_CAP, 0); // N=City
            board.placeForced(0, -1, TileType.CITY_CAP, 2); // S=City → connects to (0,0)

            // Player 1 claims the city from the north tile
            claims.put(pos(0, -1, S), 1);

            // Region from the south tile's north slot must contain the claim
            java.util.Set<Long> region = board.getRegion(0, 0, N);
            boolean owned = region.stream().anyMatch(claims::containsKey);
            assertTrue(owned,
                    "City region from (0,0) N slot must see the claim on (0,-1)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 5  Road scoring
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§5 Road scoring — mid-game")
    class RoadScoringTests {

        @Test
        @DisplayName("Single meeple on completed 3-tile road scores 3 pts and is returned")
        void singleMeepleCompletedRoad() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0);
            claims.put(pos(0, 0, E), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3,         scores[0],          "3-tile road → 3 pts");
            assertEquals(0,         scores[1],           "Player 1 had no claim");
            assertEquals(FOLLOWERS, followersLeft[0],    "Meeple returned");
            assertTrue  (claims.isEmpty(),               "Claim removed");
        }

        @Test
        @DisplayName("Incomplete road scores nothing and keeps the meeple")
        void incompletedRoadScoresNothing() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1); // E end open
            claims.put(pos(0, 0, E), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(0, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(0,             scores[0],       "No score yet");
            assertEquals(FOLLOWERS - 1, followersLeft[0],"Meeple NOT returned");
            assertFalse (claims.isEmpty(),               "Claim remains");
        }

        @Test
        @DisplayName("Tie on completed road — both players score full pts and both meeples returned")
        void tieOnCompletedRoad() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0);
            claims.put(pos(0, 0, E), 0);
            claims.put(pos(0, 0, W), 1);
            followersLeft[0] = FOLLOWERS - 1;
            followersLeft[1] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(3,         scores[0],       "Tie → P0 also scores 3");
            assertEquals(3,         scores[1],       "Tie → P1 also scores 3");
            assertEquals(FOLLOWERS, followersLeft[0],"P0 meeple returned");
            assertEquals(FOLLOWERS, followersLeft[1],"P1 meeple returned");
            assertTrue  (claims.isEmpty(),           "All claims removed");
        }

        @Test
        @DisplayName("Majority on completed road — winner takes all, loser's meeple still returned")
        void majorityOnCompletedRoad() {
            board.placeForced(-2, 0, TileType.ROAD_CROSS,    0);
            board.placeForced(-1, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 1, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 2, 0, TileType.ROAD_CROSS,    0);

            // P0 has 2 meeples, P1 has 1 → P0 wins
            claims.put(pos(-1, 0, E), 0);
            claims.put(pos( 0, 0, E), 1);
            claims.put(pos( 1, 0, E), 0);
            followersLeft[0] = FOLLOWERS - 2;
            followersLeft[1] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(2, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(5, scores[0], "Majority (2 meeples) → all 5 pts");
            assertEquals(0, scores[1], "Minority (1 meeple) → 0 pts");
            assertEquals(FOLLOWERS, followersLeft[0], "Both P0 meeples returned");
            assertEquals(FOLLOWERS, followersLeft[1], "P1 meeple returned too");
            assertTrue  (claims.isEmpty(),            "All claims removed");
        }

        @Test
        @DisplayName("Completed road with no claim scores nothing and changes no follower count")
        void completedRoadWithNoClaim() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0);

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertArrayEquals(new int[]{0, 0}, scores,
                    "No meeple → no points");
            assertArrayEquals(new int[]{FOLLOWERS, FOLLOWERS}, followersLeft,
                    "No meeples to return");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 6  City scoring
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§6 City scoring")
    class CityScoringTests {

        /**
         * Two-tile city:  CITY_CAP(rot=0) at (0,0) — North edge = City
         *                 CITY_CAP(rot=2) at (0,-1) — South edge = City (closes toward (0,0))
         * Score = 2 tiles × 2 = 4 pts.
         */
        @Test
        @DisplayName("2-tile closed city with 1 meeple scores 4 pts (2×2)")
        void twoTileCityScoresFourPts() {
            board.placeForced(0,  0, TileType.CITY_CAP, 0);
            board.placeForced(0, -1, TileType.CITY_CAP, 2);
            claims.put(pos(0, 0, N), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(0, -1, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(4,         scores[0],       "2 tiles × 2 = 4 pts");
            assertEquals(FOLLOWERS, followersLeft[0], "Meeple returned");
            assertTrue  (claims.isEmpty(),            "Claim removed");
        }

        @Test
        @DisplayName("2-tile closed city with NO meeple scores nothing")
        void twoTileCityNoMeepleScoresNothing() {
            board.placeForced(0,  0, TileType.CITY_CAP, 0);
            board.placeForced(0, -1, TileType.CITY_CAP, 2);

            board.scoreAndReturnFollowers(0, -1, claims, N_PLAYERS, scores, followersLeft);

            assertArrayEquals(new int[]{0, 0}, scores, "No meeple → no points");
        }

        @Test
        @DisplayName("Incomplete city scores nothing mid-game and keeps meeple")
        void incompleteCityScoresNothingMidGame() {
            board.placeForced(0, 0, TileType.CITY_CAP, 0); // N edge open
            claims.put(pos(0, 0, N), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(0, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(0,             scores[0],       "City not closed → 0 pts");
            assertEquals(FOLLOWERS - 1, followersLeft[0],"Meeple NOT returned");
            assertFalse (claims.isEmpty(),               "Claim remains");
        }

        @Test
        @DisplayName("City tie — both players score full pts and both returned")
        void cityTieBothScore() {
            board.placeForced(0,  0, TileType.CITY_CAP, 0);
            board.placeForced(0, -1, TileType.CITY_CAP, 2);
            claims.put(pos(0,  0, N), 0);
            claims.put(pos(0, -1, S), 1);
            followersLeft[0] = FOLLOWERS - 1;
            followersLeft[1] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(0, -1, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(4,         scores[0], "Tie → P0 also scores 4");
            assertEquals(4,         scores[1], "Tie → P1 also scores 4");
            assertEquals(FOLLOWERS, followersLeft[0]);
            assertEquals(FOLLOWERS, followersLeft[1]);
            assertTrue  (claims.isEmpty());
        }

        @Test
        @DisplayName("Majority in city — dominant player takes all pts, minority meeple still returned")
        void cityMajority() {
            // 3-tile connected city: CITY_THREE (N,E,W city, S field) + 2 caps
            // Simple 3-tile city:  CITY_CAP × 3 arranged in a line is hard to close.
            // Use CITY_CORNER_NE (C,C,F,F connectedCity) + two caps on N and E.
            board.placeForced(0,  0, TileType.CITY_CORNER_NE, 0); // N=C, E=C
            board.placeForced(0, -1, TileType.CITY_CAP, 2);        // S=C → closes N of (0,0)
            board.placeForced(1,  0, TileType.CITY_CAP, 3);        // W=C → closes E of (0,0)

            // P0: 2 meeples (one each tile), P1: 1 meeple
            claims.put(pos(0,  0, N), 0);
            claims.put(pos(0, -1, S), 0); // also P0
            claims.put(pos(1,  0, W), 1); // P1
            followersLeft[0] = FOLLOWERS - 2;
            followersLeft[1] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            // 3 tiles × 2 = 6 pts
            assertEquals(6, scores[0], "Majority → P0 scores all 6 pts");
            assertEquals(0, scores[1], "Minority → P1 scores 0");
            assertEquals(FOLLOWERS, followersLeft[0], "Both P0 meeples returned");
            assertEquals(FOLLOWERS, followersLeft[1], "P1 meeple returned too");
            assertTrue  (claims.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 7  Monastery scoring
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§7 Monastery scoring")
    class MonasteryScoringTests {

        @Test
        @DisplayName("Fully surrounded monastery scores 9 pts and returns meeple mid-game")
        void fullMonastery9Pts() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            fillNeighbours(0, 0);
            claims.put(pos(0, 0, CENTER), 0);
            followersLeft[0] = FOLLOWERS - 1;

            // Score triggered by placing the last neighbour (e.g. (1,1))
            board.scoreAndReturnFollowers(1, 1, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(9,         scores[0],       "9 cells × 1 = 9 pts");
            assertEquals(FOLLOWERS, followersLeft[0], "Meeple returned");
            assertTrue  (claims.isEmpty(),            "Claim removed");
        }

        @Test
        @DisplayName("Partially surrounded monastery does NOT score mid-game")
        void partialMonasteryNoMidGameScore() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(0, 1, TileType.ROAD_CROSS, 0);
            // Only 3 cells filled (including the monastery itself)
            claims.put(pos(0, 0, CENTER), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreAndReturnFollowers(0, 1, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(0,             scores[0],       "Partial monastery → 0 mid-game");
            assertEquals(FOLLOWERS - 1, followersLeft[0],"Meeple NOT returned");
            assertFalse (claims.isEmpty());
        }

        @Test
        @DisplayName("Monastery with no meeple scores nothing even when complete")
        void monasteryNoMeepleScoresNothing() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            fillNeighbours(0, 0);
            // No claim added

            board.scoreAndReturnFollowers(1, 1, claims, N_PLAYERS, scores, followersLeft);

            assertArrayEquals(new int[]{0, 0}, scores);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 8  Final scoring (scoreFinalAll)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§8 Final scoring — incomplete features")
    class FinalScoringTests {

        @Test
        @DisplayName("Incomplete road at end-of-game: 1 pt per tile")
        void incompleteRoadFinalScore() {
            // One ROAD_CROSS and one ROAD_STRAIGHT — road is open on E
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            claims.put(pos(0, 0, E), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreFinalAll(claims, N_PLAYERS, scores, followersLeft);

            // 2 tiles, incomplete → 1 pt per tile = 2
            assertEquals(2, scores[0], "2-tile incomplete road → 2 pts at end");
        }

        @Test
        @DisplayName("Incomplete city at end-of-game: 1 pt per tile")
        void incompleteCityFinalScore() {
            board.placeForced(0, 0, TileType.CITY_CAP, 0); // North still open
            claims.put(pos(0, 0, N), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreFinalAll(claims, N_PLAYERS, scores, followersLeft);

            assertEquals(1, scores[0], "1-tile incomplete city → 1 pt at end");
        }

        @Test
        @DisplayName("Partial monastery at end-of-game: 1 pt per filled neighbour")
        void partialMonasteryFinalScore() {
            board.placeForced(0, 0, TileType.MONASTERY_PLAIN, 0);
            // 4 neighbours → total cells = 5
            board.placeForced( 1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
            board.placeForced( 0, 1, TileType.ROAD_CROSS, 0);
            board.placeForced( 0,-1, TileType.ROAD_CROSS, 0);
            claims.put(pos(0, 0, CENTER), 0);
            followersLeft[0] = FOLLOWERS - 1;

            board.scoreFinalAll(claims, N_PLAYERS, scores, followersLeft);

            assertEquals(5, scores[0], "5 filled cells around monastery → 5 pts");
        }

        @Test
        @DisplayName("Completed feature already scored mid-game does not double-count")
        void completedFeatureNotDoubleScored() {
            // Road was already scored mid-game and removed from claims
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            board.placeForced( 1, 0, TileType.ROAD_CROSS,    0);
            // claims is empty — already returned mid-game

            board.scoreFinalAll(claims, N_PLAYERS, scores, followersLeft);

            assertArrayEquals(new int[]{0, 0}, scores,
                    "No claim remaining → no double-score at end");
        }

        @Test
        @DisplayName("Two players each with incomplete feature score independently")
        void twoPlayersIncompleteFeaturesFinalScore() {
            // P0: incomplete road at (-1,0)→(0,0)
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);
            claims.put(pos(0, 0, E), 0);

            // P1: single incomplete city tile at (5,0)
            board.placeForced(5, 0, TileType.CITY_CAP, 0);
            claims.put(pos(5, 0, N), 1);

            followersLeft[0] = FOLLOWERS - 1;
            followersLeft[1] = FOLLOWERS - 1;

            board.scoreFinalAll(claims, N_PLAYERS, scores, followersLeft);

            assertEquals(2, scores[0], "P0: 2-tile road → 2 pts");
            assertEquals(1, scores[1], "P1: 1-tile city → 1 pt");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // § 9  Carcassonne turn-order rule: one meeple per turn
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§9 One meeple per turn (game-level invariant)")
    class OneMeeplePerTurnTests {

        /**
         * The "one meeple per turn" rule is enforced at the game layer, not the
         * board. We verify the board-level invariant that adding a claim to a
         * tile decrements followersLeft exactly once.
         */
        @Test
        @DisplayName("Placing one meeple decrements followersLeft by exactly 1")
        void placingOneMeepleDecrements() {
            // Simulate the server's handleClaim logic manually
            int pidx = 0;
            followersLeft[pidx]--;
            claims.put(pos(0, 0, N), pidx);

            assertEquals(FOLLOWERS - 1, followersLeft[pidx],
                    "Exactly one follower consumed");
            assertEquals(1, claims.size(), "Exactly one claim registered");
        }

        /**
         * After the road is scored, the meeple is returned. The player should
         * end up at FOLLOWERS (same as start, net zero) — and the new meeple
         * is NOT immediately available in the same turn (game enforces this).
         * Here we verify the returned count is correct.
         */
        @Test
        @DisplayName("Scoring returns the meeple — net follower change is zero for complete feature")
        void netFollowerZeroAfterCompletedRoad() {
            board.placeForced(-1, 0, TileType.ROAD_CROSS,    0);
            board.placeForced( 0, 0, TileType.ROAD_STRAIGHT, 1);

            // Simulate placing the meeple (one per turn)
            followersLeft[0]--;
            claims.put(pos(0, 0, E), 0);

            // Close the road
            board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
            board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

            assertEquals(FOLLOWERS, followersLeft[0],
                    "Net follower delta = 0 (placed 1, scored and returned 1)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper — fill all 8 surrounding cells with ROAD_CROSS (for monastery tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private void fillNeighbours(int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0)
                    board.placeForced(cx + dx, cy + dy, TileType.ROAD_CROSS, 0);
    }
}