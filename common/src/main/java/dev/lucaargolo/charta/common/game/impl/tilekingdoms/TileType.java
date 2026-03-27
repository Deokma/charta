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
    MONASTERY_PLAIN(Edge.FIELD, Edge.FIELD, Edge.FIELD, Edge.FIELD, false, true, 4),
    MONASTERY_ROAD_S(Edge.FIELD, Edge.FIELD, Edge.ROAD, Edge.FIELD, false, true, 2),

    // ── Pure roads ─────────────────────────────────────────────────────────────
    ROAD_STRAIGHT(Edge.ROAD, Edge.FIELD, Edge.ROAD, Edge.FIELD, false, false, 8),   // N-S straight
    ROAD_CURVE_SE(Edge.FIELD, Edge.ROAD, Edge.ROAD, Edge.FIELD, false, false, 9),   // curve S-E
    ROAD_T(Edge.FIELD, Edge.ROAD, Edge.ROAD, Edge.ROAD, false, false, 4),   // T-junction (no N)
    ROAD_CROSS(Edge.ROAD, Edge.ROAD, Edge.ROAD, Edge.ROAD, false, false, 1),   // Crossroads

    // ── City cap (one edge) ────────────────────────────────────────────────────
    CITY_CAP(Edge.CITY, Edge.FIELD, Edge.FIELD, Edge.FIELD, false, false, 5),   // City on N only
    CITY_CAP_ROAD_LR(Edge.CITY, Edge.ROAD, Edge.FIELD, Edge.ROAD, false, false, 4),   // City N, road E-W
    CITY_CAP_ROAD_T(Edge.CITY, Edge.ROAD, Edge.ROAD, Edge.ROAD, false, false, 3),   // City N, road T
    CITY_CAP_ROAD_SE(Edge.CITY, Edge.ROAD, Edge.ROAD, Edge.FIELD, false, false, 3),   // City N, road S-E
    CITY_CAP_ROAD_SW(Edge.CITY, Edge.FIELD, Edge.ROAD, Edge.ROAD, false, false, 3),   // City N, road S-W

    // ── Two-edge city (connected) ──────────────────────────────────────────────
    CITY_BRIDGE(Edge.CITY, Edge.FIELD, Edge.CITY, Edge.FIELD, true, false, 1),   // City N-S tunnel
    CITY_BRIDGE_SECURED(Edge.CITY, Edge.FIELD, Edge.CITY, Edge.FIELD, true, false, 2),   // City N-S tunnel secured
    CITY_CORNER_NE(Edge.CITY, Edge.CITY, Edge.FIELD, Edge.FIELD, true, false, 3),   // City corner N-E
    CITY_CORNER_NE_SECURED(Edge.CITY, Edge.CITY, Edge.FIELD, Edge.FIELD, true, false, 2),   // City corner N-E secured
    CITY_CORNER_ROAD(Edge.CITY, Edge.CITY, Edge.ROAD, Edge.ROAD, true, false, 3),   // City corner + road
    CITY_CORNER_ROAD_SECURED(Edge.CITY, Edge.CITY, Edge.ROAD, Edge.ROAD, true, false, 2),   // City corner + road secured
    CITY_CAP_NE(Edge.CITY, Edge.CITY, Edge.FIELD, Edge.FIELD, true, false, 2),   // City cap N-E


    // TODO: Как здесь это различать? Вроде понял
    CITY_FIELD(Edge.CITY, Edge.FIELD, Edge.CITY, Edge.FIELD, false, false, 3),   // City N-S tunnel


    // ── Three-edge city ────────────────────────────────────────────────────────
    CITY_THREE(Edge.CITY, Edge.CITY, Edge.FIELD, Edge.CITY, true, false, 3),   // City N, E, W
    CITY_THREE_SECURED(Edge.CITY, Edge.CITY, Edge.FIELD, Edge.CITY, true, false, 3),   // City N, E, W secured
    CITY_THREE_ROAD(Edge.CITY, Edge.CITY, Edge.ROAD, Edge.CITY, true, false, 1),   // City 3 + road S
    CITY_THREE_ROAD_SECURED(Edge.CITY, Edge.CITY, Edge.ROAD, Edge.CITY, true, false, 2),   // City 3 + road secured

    // ── Full city ──────────────────────────────────────────────────────────────
    CITY_FULL(Edge.CITY, Edge.CITY, Edge.CITY, Edge.CITY, true, false, 1); // secured

    // ── Starting tile (always placed first) ───────────────────────────────────
    //START(Edge.C, Edge.R, Edge.F, Edge.R, false, false, 1);

    // NEXT UPDATES

//    LAKE(Edge.FIELD, Edge.FIELD, Edge.RIVER, Edge.FIELD, false, false, 2),
//    RIVER(Edge.FIELD, Edge.RIVER, Edge.FIELD, Edge.RIVER, false, false, 2),
//    RIVER_WS(Edge.FIELD, Edge.FIELD, Edge.RIVER, Edge.RIVER, false, false, 2),
//
//    MONASTERY_RIVER_BRIDGE(Edge.FIELD, Edge.RIVER, Edge.ROAD, Edge.RIVER, false, true, 1),
//    RIVER_BRIDGE(Edge.ROAD, Edge.RIVER, Edge.ROAD, Edge.RIVER, false, false, 1),
//    RIVER_WS_ROAD_NE(Edge.ROAD, Edge.ROAD, Edge.RIVER, Edge.RIVER, false, false, 1),
//
//    CITY_CAP_RIVER_BRIDGE(Edge.CITY, Edge.RIVER, Edge.ROAD, Edge.RIVER, true, false, 1),
//
//    CITY_RIVER_CAPS(Edge.CITY, Edge.RIVER, Edge.CITY, Edge.RIVER, true, false, 1),
//    CITY_CORNER_RIVER_WS(Edge.CITY, Edge.CITY, Edge.RIVER, Edge.RIVER, true, false, 1),

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
    public enum Edge {FIELD, ROAD, CITY, RIVER}

    /**
     * Total tiles in standard deck.
     */
    public static int totalCount() {
        int t = 0;
        for (TileType tt : values()) t += tt.count;
        return t;
    }
}