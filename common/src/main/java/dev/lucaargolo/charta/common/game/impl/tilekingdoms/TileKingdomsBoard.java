package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import java.util.*;

public class TileKingdomsBoard {

    public static final int SIZE = 25, HALF = SIZE / 2;
    private static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};

    // Duplicated from TileKingdomsGame to keep TileKingdomsBoard free of Minecraft dependencies
    // (TileKingdomsGame pulls net.minecraft.* which is not available in plain unit tests)
    static final int SLOT_CENTER = 4;

    static long packPos(int lx, int ly, int slot) {
        return (slot & 0xFF) | (((lx + 64) & 0xFF) << 8L) | (((ly + 64) & 0xFF) << 16L);
    }

    private final short[] grid = new short[SIZE * SIZE];
    private int minX = HALF, maxX = HALF, minY = HALF, maxY = HALF;

    public TileKingdomsBoard() {
        Arrays.fill(grid, PlacedTile.EMPTY);
    }

    // ── Grid access ───────────────────────────────────────────────────────────
    private int idx(int gx, int gy) {
        return gy * SIZE + gx;
    }

    public short get(int lx, int ly) {
        int gx = lx + HALF, gy = ly + HALF;
        if (gx < 0 || gx >= SIZE || gy < 0 || gy >= SIZE) return PlacedTile.EMPTY;
        return grid[idx(gx, gy)];
    }

    public boolean isEmpty(int lx, int ly) {
        return PlacedTile.isEmpty(get(lx, ly));
    }

    // ── Placement ─────────────────────────────────────────────────────────────
    public boolean canPlace(int lx, int ly, TileType type, int rot) {
        if (!isEmpty(lx, ly)) return false;
        short cand = PlacedTile.pack(type, rot);
        boolean hasNb = false;
        for (int d = 0; d < 4; d++) {
            short nb = get(lx + DX[d], ly + DY[d]);
            if (!PlacedTile.isEmpty(nb)) {
                hasNb = true;
                if (!PlacedTile.compatible(cand, d, nb)) return false;
            }
        }
        return hasNb;
    }

    public boolean place(int lx, int ly, TileType type, int rot) {
        if (!canPlace(lx, ly, type, rot)) return false;
        int gx = lx + HALF, gy = ly + HALF;
        grid[idx(gx, gy)] = PlacedTile.pack(type, rot);
        minX = Math.min(minX, gx);
        maxX = Math.max(maxX, gx);
        minY = Math.min(minY, gy);
        maxY = Math.max(maxY, gy);
        return true;
    }

    public void placeForced(int lx, int ly, TileType type, int rot) {
        int gx = lx + HALF, gy = ly + HALF;
        if (gx < 0 || gx >= SIZE || gy < 0 || gy >= SIZE) return;
        grid[idx(gx, gy)] = PlacedTile.pack(type, rot);
        minX = Math.min(minX, gx);
        maxX = Math.max(maxX, gx);
        minY = Math.min(minY, gy);
        maxY = Math.max(maxY, gy);
    }

    public boolean hasAnyValidPosition(TileType type) {
        for (int r = 0; r < 4; r++)
            for (int gy = minY - 1; gy <= maxY + 1; gy++)
                for (int gx = minX - 1; gx <= maxX + 1; gx++)
                    if (canPlace(gx - HALF, gy - HALF, type, r)) return true;
        return false;
    }

    // ── Region BFS (for claiming check) ──────────────────────────────────────

    /**
     * Returns set of (slot|lx|ly) packed positions in the same connected region
     * as the given starting edge slot on tile (lx,ly).
     */
    public Set<Long> getRegion(int lx, int ly, int slot) {
        Set<Long> visited = new HashSet<>();
        short tile = get(lx, ly);
        if (PlacedTile.isEmpty(tile)) return visited;
        TileType.Edge edge = (slot == SLOT_CENTER) ? TileType.Edge.F : PlacedTile.edgeOf(tile, slot);
        if (edge == TileType.Edge.F) return visited;
        bfsRegion(lx, ly, slot, edge, visited, new HashSet<>());
        return visited;
    }

    /**
     * BFS region: collect all (lx,ly,slot) positions belonging to the same connected
     * city or road feature starting from (lx,ly,slot).
     * For roads: ROAD_CROSS and ROAD_T are endpoints (roads terminate there).
     * For cities: connectedCity tiles spread across all city edges.
     */
    private void bfsRegion(int lx, int ly, int slot, TileType.Edge targetEdge,
                           Set<Long> visited, Set<String> tileVisited) {
        long posKey = packPos(lx, ly, slot);
        if (visited.contains(posKey)) return;
        visited.add(posKey);
        short tile = get(lx, ly);
        if (PlacedTile.isEmpty(tile)) return;
        TileType type = PlacedTile.typeOf(tile);
        if (type == null) return;

        if (targetEdge == TileType.Edge.C) {
            // Cities: spread to all connected city edges on this tile
            if (type.connectedCity) {
                for (int d = 0; d < 4; d++) {
                    if (PlacedTile.edgeOf(tile, d) == TileType.Edge.C) {
                        long dk = packPos(lx, ly, d);
                        if (!visited.contains(dk)) bfsRegion(lx, ly, d, targetEdge, visited, tileVisited);
                    }
                }
            }
            // Cross into neighbour via this slot
            int nx = lx + DX[slot], ny = ly + DY[slot];
            short nb = get(nx, ny);
            if (!PlacedTile.isEmpty(nb)) {
                int opp = TileType.opposite(slot);
                if (PlacedTile.edgeOf(nb, opp) == TileType.Edge.C)
                    bfsRegion(nx, ny, opp, targetEdge, visited, tileVisited);
            }
        } else {
            // Roads: find connected road exits on this tile and follow them
            // ROAD_CROSS and ROAD_T: all roads terminate here (no through-connection)
            boolean isEndpoint = (type == TileType.ROAD_CROSS || type == TileType.ROAD_T);
            if (!isEndpoint) {
                // Follow road through the tile: find the OTHER road exit(s) and traverse them
                for (int d = 0; d < 4; d++) {
                    if (d == slot) continue;
                    if (PlacedTile.edgeOf(tile, d) == TileType.Edge.R) {
                        long dk = packPos(lx, ly, d);
                        if (!visited.contains(dk)) {
                            // Follow this road exit into the neighbour
                            visited.add(dk); // mark this exit as part of the region
                            int nx = lx + DX[d], ny = ly + DY[d];
                            short nb = get(nx, ny);
                            if (!PlacedTile.isEmpty(nb)) {
                                int opp = TileType.opposite(d);
                                if (PlacedTile.edgeOf(nb, opp) == TileType.Edge.R)
                                    bfsRegion(nx, ny, opp, targetEdge, visited, tileVisited);
                            }
                        }
                    }
                }
            }
            // Also cross into neighbour via the incoming slot (entry edge)
            int nx = lx + DX[slot], ny = ly + DY[slot];
            short nb = get(nx, ny);
            if (!PlacedTile.isEmpty(nb)) {
                int opp = TileType.opposite(slot);
                if (PlacedTile.edgeOf(nb, opp) == TileType.Edge.R) {
                    long nk = packPos(nx, ny, opp);
                    if (!visited.contains(nk)) bfsRegion(nx, ny, opp, targetEdge, visited, tileVisited);
                }
            }
        }
    }

    // ── Scoring after tile placement ──────────────────────────────────────────

    /**
     * Score completions caused by placing tile at (lx,ly).
     * Updates scores[] and returns followers from completed regions.
     */
    public void scoreAndReturnFollowers(int lx, int ly, Map<Long, Integer> claims,
                                        int nPlayers, int[] scores, int[] followersLeft) {
        short tile = get(lx, ly);
        if (PlacedTile.isEmpty(tile)) return;
        TileType type = PlacedTile.typeOf(tile);

        Set<String> processedRegions = new HashSet<>();

        // Check each edge
        for (int dir = 0; dir < 4; dir++) {
            TileType.Edge edge = PlacedTile.edgeOf(tile, dir);
            if (edge == TileType.Edge.F) continue;
            String regionId = getRegionId(lx, ly, dir);
            if (processedRegions.contains(regionId)) continue;
            processedRegions.add(regionId);

            if (edge == TileType.Edge.C) scoreCity(lx, ly, dir, claims, nPlayers, scores, followersLeft, true);
            else if (edge == TileType.Edge.R) scoreRoad(lx, ly, dir, claims, nPlayers, scores, followersLeft, true);
        }
        // Monastery
        if (type != null && type.monastery) scoreMonastery(lx, ly, claims, nPlayers, scores, followersLeft, true);
        // Check adjacent monasteries
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                short nb = get(lx + dx, ly + dy);
                TileType nbt = PlacedTile.typeOf(nb);
                if (nbt != null && nbt.monastery)
                    scoreMonastery(lx + dx, ly + dy, claims, nPlayers, scores, followersLeft, true);
            }
    }

    public Set<Long> getCompletedRegionClaims(int lx, int ly, Map<Long, Integer> claims) {
        // Called after scoreAndReturnFollowers - but we fold them into scoreAndReturnFollowers directly
        return new HashSet<>(); // handled inline now
    }

    private long getRegionHash(Set<Long> region) {
        long hash = 1469598103934665603L; // FNV offset
        for (long v : region) {
            hash ^= v;
            hash *= 1099511628211L;
        }
        return hash;
    }

    private String getRegionId(int lx, int ly, int slot) {
        Set<Long> region = getRegion(lx, ly, slot);
        TreeSet<Long> sorted = new TreeSet<>(region);
        return sorted.toString();
    }

    // ── City scoring ──────────────────────────────────────────────────────────
    private void scoreCity(int lx, int ly, int startSlot, Map<Long, Integer> claims,
                           int nPlayers, int[] scores, int[] followersLeft, boolean onlyIfComplete) {
        Set<Long> region = new HashSet<>();
        Set<String> tiles = new HashSet<>();
        boolean complete = bfsCityComplete(lx, ly, startSlot, region, tiles);
        if (onlyIfComplete && !complete) return;
        int[] owners = getMajorityOwners(region, claims, nPlayers);
        int pts = complete ? tiles.size() * 2 : tiles.size();
        for (int o : owners) if (o >= 0 && o < scores.length) scores[o] += pts;
        if (complete) returnFollowers(region, claims, followersLeft);
    }

    private boolean bfsCityComplete(int lx, int ly, int slot, Set<Long> region, Set<String> tiles) {
        long key = packPos(lx, ly, slot);
        if (region.contains(key)) return true;
        region.add(key);
        short tile = get(lx, ly);
        if (PlacedTile.isEmpty(tile)) return false; // open edge
        tiles.add(lx + "," + ly);
        TileType type = PlacedTile.typeOf(tile);
        boolean complete = true;
        // Spread within tile for connected city
        if (type != null && type.connectedCity) {
            for (int d = 0; d < 4; d++) {
                if (PlacedTile.edgeOf(tile, d) == TileType.Edge.C) {
                    long dk = packPos(lx, ly, d);
                    if (!region.contains(dk)) complete &= bfsCityComplete(lx, ly, d, region, tiles);
                }
            }
        }
        // Cross to neighbour
        int nx = lx + DX[slot], ny = ly + DY[slot];
        short nb = get(nx, ny);
        if (PlacedTile.isEmpty(nb)) return false;
//        if (PlacedTile.edgeOf(nb, TileType.opposite(slot)) == TileType.Edge.C)
//            complete &= bfsCityComplete(nx, ny, TileType.opposite(slot), region, tiles);
        int opp = TileType.opposite(slot);
        if (PlacedTile.edgeOf(nb, opp) != TileType.Edge.C) {
            return false; // НЕ город - город открыт
        }

        complete &= bfsCityComplete(nx, ny, opp, region, tiles);
        return complete;
    }

    // ── Road scoring ──────────────────────────────────────────────────────────
    private void scoreRoad(int lx, int ly, int startSlot, Map<Long, Integer> claims,
                           int nPlayers, int[] scores, int[] followersLeft, boolean onlyIfComplete) {
        Set<Long> region = new HashSet<>();
        Set<String> tiles = new HashSet<>();
        boolean complete = bfsRoadComplete(lx, ly, startSlot, region, tiles);
        if (onlyIfComplete && !complete) return;
        int[] owners = getMajorityOwners(region, claims, nPlayers);
        int pts = tiles.size();
        for (int o : owners) if (o >= 0 && o < scores.length) scores[o] += pts;
        if (complete) returnFollowers(region, claims, followersLeft);
    }

//    private boolean bfsRoadComplete(int lx, int ly, int slot, Set<Long> region, Set<String> tiles) {
//        long key = packPos(lx, ly, slot);
//        if (region.contains(key)) return true;
//        region.add(key);
//        short tile = get(lx, ly);
//        if (PlacedTile.isEmpty(tile)) return false;
//        tiles.add(lx + "," + ly);
//        TileType type = PlacedTile.typeOf(tile);
//        // Crossroad/T-junction = road endpoint (complete)
//        if (type == TileType.ROAD_CROSS || type == TileType.ROAD_T) return true;
//        // Find the other road exit on this tile
//        boolean complete = true;
//        for (int d = 0; d < 4; d++) {
//            if (d == slot || d == TileType.opposite(slot)) continue;
//            if (PlacedTile.edgeOf(tile, d) == TileType.Edge.R) {
//                long dk = packPos(lx, ly, d);
//                if (!region.contains(dk)) complete &= bfsRoadComplete(lx, ly, d, region, tiles);
//            }
//        }
//        // Cross into neighbour at this slot
//        int nx = lx + DX[slot], ny = ly + DY[slot];
//        short nb = get(nx, ny);

    /// /        if (PlacedTile.isEmpty(nb)) return false;
    /// /        if (PlacedTile.edgeOf(nb, TileType.opposite(slot)) == TileType.Edge.R)
    /// /            complete &= bfsRoadComplete(nx, ny, TileType.opposite(slot), region, tiles);
    /// /        else return true; // road ends at city/monastery
//        if (PlacedTile.isEmpty(nb)) return false;
//
//        int opp = TileType.opposite(slot);
//
//        if (PlacedTile.edgeOf(nb, opp) == TileType.Edge.R) {
//            complete &= bfsRoadComplete(nx, ny, opp, region, tiles);
//        } else {
//            // дорога упёрлась — это нормальный конец
//            return true;
//        }
//        return complete;
//    }
    private boolean bfsRoadComplete(int lx, int ly, int slot, Set<Long> region, Set<String> tiles) {
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{lx, ly, slot});
        boolean complete = true;

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0], cy = cur[1], cslot = cur[2];
            long key = packPos(cx, cy, cslot);
            if (region.contains(key)) continue;
            region.add(key);

            short tile = get(cx, cy);
            if (PlacedTile.isEmpty(tile)) {
                complete = false; // конец дороги свободен — не завершена
                continue;
            }

            tiles.add(cx + "," + cy);
            TileType type = PlacedTile.typeOf(tile);
            TileType.Edge edge = PlacedTile.edgeOf(tile, cslot);

            if (edge != TileType.Edge.R) continue;
            // Перекрёстки и T-джанкции — конец дороги на этом тайле.
            // НО: нужно всё равно зайти в соседа по входящему слоту, иначе BFS
            // не обойдёт дорогу, если scoring начат именно с этого endpoint-тайла
            // (например, ROAD_CROSS — последний размещённый тайл).
            if (type == TileType.ROAD_CROSS || type == TileType.ROAD_T) {
                int ex = cx + DX[cslot], ey = cy + DY[cslot];
                short en = get(ex, ey);
                if (!PlacedTile.isEmpty(en)) {
                    int eopp = TileType.opposite(cslot);
                    if (PlacedTile.edgeOf(en, eopp) == TileType.Edge.R) {
                        long ek = packPos(ex, ey, eopp);
                        if (!region.contains(ek)) queue.add(new int[]{ex, ey, eopp});
                    }
                }
                continue; // не распространяться по другим рёбрам этого тайла
            } // не дорога — игнорируем

            // остальные рёбра на тайле — продолжение дороги
            for (int d = 0; d < 4; d++) {
                if (d == cslot) continue;
                if (PlacedTile.edgeOf(tile, d) == TileType.Edge.R) {
                    long nk = packPos(cx, cy, d);
                    if (!region.contains(nk)) queue.add(new int[]{cx, cy, d});
                }
            }

            // соседний тайл в направлении cslot
            int nx = cx + DX[cslot], ny = cy + DY[cslot];
            short nb = get(nx, ny);
            int opp = TileType.opposite(cslot);

            if (PlacedTile.isEmpty(nb)) {
                complete = false; // свободный конец
            } else if (PlacedTile.edgeOf(nb, opp) == TileType.Edge.R) {
                long nk = packPos(nx, ny, opp);
                if (!region.contains(nk)) queue.add(new int[]{nx, ny, opp});
            } else {
                TileType ntype = PlacedTile.typeOf(nb);
                // конец дороги — город или монастырь
                if (ntype != null && (ntype.monastery || hasCityEdge(nb, opp))) {
                    complete = complete && true;
                } else {
                    complete = false;
                }
            }
        }

        return complete;
    }

    // helper для проверки, есть ли на тайле город в нужной грани
    private boolean hasCityEdge(short tile, int slot) {
        TileType type = PlacedTile.typeOf(tile);
        if (type == null) return false;
        return PlacedTile.edgeOf(tile, slot) == TileType.Edge.C;
    }

    // ── Monastery scoring ─────────────────────────────────────────────────────
    private void scoreMonastery(int lx, int ly, Map<Long, Integer> claims, int nPlayers,
                                int[] scores, int[] followersLeft, boolean onlyIfComplete) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (!isEmpty(lx + dx, ly + dy)) count++;
        if (onlyIfComplete && count < 9) return;
        long key = packPos(lx, ly, SLOT_CENTER);
        Integer owner = claims.get(key);
        if (owner != null && owner >= 0 && owner < scores.length) {
            scores[owner] += count;
            if (count == 9) {
                claims.remove(key);
                followersLeft[owner]++;
            }
        }
    }

    // ── Final scoring ─────────────────────────────────────────────────────────
    public void scoreFinalAll(Map<Long, Integer> claims, int nPlayers, int[] scores, int[] followersLeft) {
        Set<String> processed = new HashSet<>();
        for (int gy = 0; gy < SIZE; gy++)
            for (int gx = 0; gx < SIZE; gx++) {
                int lx = gx - HALF, ly = gy - HALF;
                short tile = get(lx, ly);
                if (PlacedTile.isEmpty(tile)) continue;
                TileType type = PlacedTile.typeOf(tile);
                for (int dir = 0; dir < 4; dir++) {
                    TileType.Edge e = PlacedTile.edgeOf(tile, dir);
                    if (e == TileType.Edge.F) continue;
                    String rid = getRegionId(lx, ly, dir);
                    if (processed.contains(rid)) continue;
                    processed.add(rid);
                    if (e == TileType.Edge.C) scoreCity(lx, ly, dir, claims, nPlayers, scores, followersLeft, false);
                    else if (e == TileType.Edge.R)
                        scoreRoad(lx, ly, dir, claims, nPlayers, scores, followersLeft, false);
                }
                if (type != null && type.monastery)
                    scoreMonastery(lx, ly, claims, nPlayers, scores, followersLeft, false);
            }
    }

    // ── Ownership helpers ─────────────────────────────────────────────────────
    private int[] getMajorityOwners(Set<Long> region, Map<Long, Integer> claims, int nPlayers) {
        int[] count = new int[nPlayers];
        for (Long pos : region) {
            Integer o = claims.get(pos);
            if (o != null && o >= 0 && o < nPlayers) count[o]++;
        }
        int max = 0;
        for (int c : count) max = Math.max(max, c);
        if (max == 0) return new int[0];
        List<Integer> winners = new ArrayList<>();
        for (int i = 0; i < nPlayers; i++) if (count[i] == max) winners.add(i);
        return winners.stream().mapToInt(Integer::intValue).toArray();
    }

    private void returnFollowers(Set<Long> region, Map<Long, Integer> claims, int[] followersLeft) {
        for (Long pos : new HashSet<>(region)) {
            Integer o = claims.remove(pos);
            if (o != null && o >= 0 && o < followersLeft.length) followersLeft[o]++;
        }
    }

    // ── Serialisation ─────────────────────────────────────────────────────────
    public short[] getGridCopy() {
        return Arrays.copyOf(grid, grid.length);
    }

    public int[] getBounds() {
        return new int[]{minX - HALF, minY - HALF, maxX - HALF, maxY - HALF};
    }
}