package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

/**
 * Defines each unique tile type in Tile Kingdoms.
 * Edges are ordered N, E, S, W.  Values: F=Field, R=Road, C=City.
 * connectedCities: true if opposite/adjacent city edges are one connected city.
 * monastery: true if this tile has a monastery in the centre.
 * count: how many copies exist in the standard deck.
 */
public enum TileType {

    // ── Monasteries ────────────────────────────────────────────────────────────
    MONASTERY_PLAIN(Edge.F, Edge.F, Edge.F, Edge.F, false, true, 4),
    MONASTERY_ROAD_S(Edge.F, Edge.F, Edge.R, Edge.F, false, true, 2),

    // ── Pure roads ─────────────────────────────────────────────────────────────
    ROAD_STRAIGHT(Edge.R, Edge.F, Edge.R, Edge.F, false, false, 8),   // N-S straight
    ROAD_CURVE_SE(Edge.F, Edge.R, Edge.R, Edge.F, false, false, 9),   // curve S-E
    ROAD_T(Edge.F, Edge.R, Edge.R, Edge.R, false, false, 4),   // T-junction (no N)
    ROAD_CROSS(Edge.R, Edge.R, Edge.R, Edge.R, false, false, 1),   // Crossroads

    // ── City cap (one edge) ────────────────────────────────────────────────────
    CITY_CAP(Edge.C, Edge.F, Edge.F, Edge.F, false, false, 5),   // City on N only
    CITY_CAP_ROAD_LR(Edge.C, Edge.R, Edge.F, Edge.R, false, false, 3),   // City N, road E-W
    CITY_CAP_ROAD_T(Edge.C, Edge.R, Edge.R, Edge.R, false, false, 3),   // City N, road T

    // ── Two-edge city (connected) ──────────────────────────────────────────────
    CITY_BRIDGE(Edge.C, Edge.F, Edge.C, Edge.F, true, false, 1),   // City N-S tunnel
    CITY_CORNER_NE(Edge.C, Edge.C, Edge.F, Edge.F, true, false, 3),   // City corner N-E
    CITY_CORNER_ROAD(Edge.C, Edge.C, Edge.R, Edge.R, true, false, 3),   // City corner + road

    // ── Three-edge city ────────────────────────────────────────────────────────
    CITY_THREE(Edge.C, Edge.C, Edge.F, Edge.C, true, false, 3),   // City N, E, W
    CITY_THREE_ROAD(Edge.C, Edge.C, Edge.R, Edge.C, true, false, 2),   // City 3 + road S

    // ── Full city ──────────────────────────────────────────────────────────────
    CITY_FULL(Edge.C, Edge.C, Edge.C, Edge.C, true, false, 1);

    // ── Starting tile (always placed first) ───────────────────────────────────
    //START(Edge.C, Edge.R, Edge.F, Edge.R, false, false, 1);

    // ────────────────────────────────────────────────────────────────────────────

    public final Edge[] edges; // [N, E, S, W]
    public final boolean connectedCity;
    public final boolean monastery;
    public final int count;

    TileType(Edge n, Edge e, Edge s, Edge w, boolean connectedCity, boolean monastery, int count) {
        this.edges = new Edge[]{n, e, s, w};
        this.connectedCity = connectedCity;
        this.monastery = monastery;
        this.count = count;
    }

    /**
     * Returns the edge for a given direction (0=N,1=E,2=S,3=W).
     */
    public Edge edge(int dir) {
        return edges[dir & 3];
    }

    /**
     * Returns the opposite direction index.
     */
    public static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    /**
     * Edge type. F-Field, R-Road, C-City
     */
    public enum Edge {F, R, C}

    /**
     * Total tiles in standard deck.
     */
    public static int totalCount() {
        int t = 0;
        for (TileType tt : values()) t += tt.count;
        return t;
    }
}