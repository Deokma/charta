package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for meeple (follower) return after road completion.
 *
 * <p>Only depends on pure-Java classes: {@link TileKingdomsBoard}, {@link TileType},
 * {@link PlacedTile}. {@code packPos} is inlined here so no direct symbol from
 * {@link TileKingdomsGame} is referenced in this file (TileKingdomsBoard still
 * references it internally, which is fine as long as Minecraft is on the test classpath).
 *
 * <h2>Board layout</h2>
 * <pre>
 *  (-1,0)            (0,0)              (1,0)
 *  ROAD_CROSS  —  ROAD_STRAIGHT  —  ROAD_CROSS
 *  [endpoint]     (rot=1, E-W)       [endpoint]
 * </pre>
 * {@code ROAD_STRAIGHT} rotated 1 step clockwise → physical edges E=R, W=R.
 * {@code ROAD_CROSS} is a road endpoint per BFS rules.
 * A completed 3-tile road scores 3 points (1 per tile).
 */
class RoadCompletionTest {

    // Slot indices
    private static final int N = 0, E = 1, S = 2, W = 3;
    private static final int FOLLOWERS_PER_PLAYER = 7;
    private static final int N_PLAYERS = 2;
    private static final int FULL = FOLLOWERS_PER_PLAYER;

    // Inline mirror of TileKingdomsGame.packPos — avoids direct class reference
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
        // Start with 1 meeple already spent (simulates mid-game state)
        followersLeft = new int[N_PLAYERS];
        Arrays.fill(followersLeft, FULL - 1);
    }

    /** Builds the 3-tile road without placing any claim. */
    private void buildThreeTileRoad() {
        board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
        board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1); // rot=1 => E-W road
        board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 1 — Базовый: мипл возвращён после завершения дороги
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Мипл возвращён и дорога засчитана, когда закрывающий тайл завершает дорогу из 3 тайлов")
    void meepleReturnedAfterRoadCompletion() {
        // Arrange: два тайла уже стоят
        board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
        board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);

        // Игрок 0 ставит мипл на восточный слот среднего тайла
        claims.put(pos(0, 0, E), 0);

        // Act: ставим правый конец, завершая дорогу
        board.placeForced(1, 0, TileType.ROAD_CROSS, 0);
        board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

        // Assert
        assertEquals(3, scores[0],
                "Дорога из 3 тайлов => игрок 0 получает 3 очка");
        assertEquals(0, scores[1],
                "Игрок 1 ничего не ставил => 0 очков");
        assertEquals(FULL, followersLeft[0],
                "Мипл игрока 0 должен быть возвращён");
        assertEquals(FULL - 1, followersLeft[1],
                "Миплы игрока 1 не затронуты");
        assertTrue(claims.isEmpty(),
                "Claims должен быть пустым после возврата мипла");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 2 — Дорога не замкнута: мипл НЕ возвращается
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Мипл остаётся на доске, когда дорога ещё не замкнута")
    void meepleNotReturnedForIncompleteRoad() {
        // Arrange: только левый конец и средний тайл; правый конец открыт
        board.placeForced(-1, 0, TileType.ROAD_CROSS, 0);
        board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);

        claims.put(pos(0, 0, E), 0);

        // Act: скорим после среднего тайла (правый конец ещё пуст)
        board.scoreAndReturnFollowers(0, 0, claims, N_PLAYERS, scores, followersLeft);

        // Assert
        assertEquals(0, scores[0], "Нет очков — дорога не завершена");
        assertEquals(FULL - 1, followersLeft[0],
                "Мипл НЕ должен быть возвращён");
        assertFalse(claims.isEmpty(),
                "Claim должен остаться, потому что дорога открыта");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 3 — Ничья: оба игрока на одной дороге — оба получают очки
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Оба игрока получают очки и оба мипла возвращаются при ничье на завершённой дороге")
    void twoPlayersShareCompletedRoadScore() {
        buildThreeTileRoad();

        // Игрок 0 на E, игрок 1 на W среднего тайла => ничья 1:1
        claims.put(pos(0, 0, E), 0);
        claims.put(pos(0, 0, W), 1);

        board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

        assertEquals(3, scores[0], "Игрок 0 получает 3 (ничья — оба majority)");
        assertEquals(3, scores[1], "Игрок 1 получает 3 (ничья — оба majority)");
        assertEquals(FULL, followersLeft[0], "Мипл игрока 0 возвращён");
        assertEquals(FULL, followersLeft[1], "Мипл игрока 1 возвращён");
        assertTrue(claims.isEmpty(), "Все claims сняты");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 4 — Доминирование: больше миплов => все очки
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Игрок с большинством миплов забирает все очки с завершённой дороги")
    void dominantPlayerTakesAllPoints() {
        // Длинная дорога из 5 тайлов: CROSS - STRAIGHT x3 - CROSS
        board.placeForced(-2, 0, TileType.ROAD_CROSS, 0);
        board.placeForced(-1, 0, TileType.ROAD_STRAIGHT, 1);
        board.placeForced(0, 0, TileType.ROAD_STRAIGHT, 1);
        board.placeForced(1, 0, TileType.ROAD_STRAIGHT, 1);
        board.placeForced(2, 0, TileType.ROAD_CROSS, 0);

        // Игрок 0: 2 мипла; Игрок 1: 1 мипл
        claims.put(pos(-1, 0, E), 0);
        claims.put(pos(0, 0, E), 1);
        claims.put(pos(1, 0, E), 0);
        followersLeft[0] = FULL - 2;
        followersLeft[1] = FULL - 1;

        board.scoreAndReturnFollowers(2, 0, claims, N_PLAYERS, scores, followersLeft);

        assertEquals(5, scores[0], "Игрок 0 (majority) получает все 5 очков");
        assertEquals(0, scores[1], "Игрок 1 (minority) не получает ничего");
        assertEquals(FULL, followersLeft[0], "Оба мипла игрока 0 возвращены");
        assertEquals(FULL, followersLeft[1], "Мипл игрока 1 тоже возвращён");
        assertTrue(claims.isEmpty(), "Все claims сняты");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 5 — Дорога завершена, но без миплов: ничего не происходит
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Завершённая дорога без миплов не даёт очков и не меняет follower-счёт")
    void completedRoadWithNoClaimScoresNothing() {
        buildThreeTileRoad();
        // Никаких claims нет

        board.scoreAndReturnFollowers(1, 0, claims, N_PLAYERS, scores, followersLeft);

        assertArrayEquals(new int[]{0, 0}, scores,
                "Нет миплов => нет очков");
        assertArrayEquals(new int[]{FULL - 1, FULL - 1}, followersLeft,
                "Нет миплов => followersLeft не изменился");
    }
}