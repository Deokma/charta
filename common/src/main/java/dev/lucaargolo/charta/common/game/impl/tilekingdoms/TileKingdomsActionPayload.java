package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.mixed.LivingEntityMixed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;

public record TileKingdomsActionPayload(int containerId, int action) implements CustomPacketPayload {

    public static final int ROTATE     = -1;
    public static final int SKIP_CLAIM = -2;

    public static final Type<TileKingdomsActionPayload> TYPE =
            new Type<>(ChartaMod.id("tile_kingdoms_action"));

    public static final StreamCodec<ByteBuf, TileKingdomsActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, TileKingdomsActionPayload::containerId,
                    ByteBufCodecs.INT, TileKingdomsActionPayload::action,
                    TileKingdomsActionPayload::new);

    public static void handleServer(TileKingdomsActionPayload payload, ServerPlayer player, Executor executor) {
        executor.execute(() -> {
            if (!(player instanceof LivingEntityMixed mixed)) return;
            if (!(player.containerMenu instanceof TileKingdomsMenu menu)) return;
            if (menu.containerId != payload.containerId()) return;

            TileKingdomsGame game = menu.getGame();
            CardPlayer cp = mixed.charta_getCardPlayer();
            int action = payload.action();

            if (action == ROTATE) {
                game.rotateTile();
            } else if (action == SKIP_CLAIM) {
                if (game.canPlay(cp, new GamePlay(List.of(), SKIP_CLAIM))) {
                    cp.play(new GamePlay(List.of(), SKIP_CLAIM));
                }
            } else if (TileKingdomsGame.isClaimAction(action)) {
                // Claim feature: only valid in PHASE_CLAIM
                if (game.canPlay(cp, new GamePlay(List.of(), action))) {
                    cp.play(new GamePlay(List.of(), action));
                }
            } else if (action >= 0) {
                // Tile placement
                if (game.canPlay(cp, new GamePlay(List.of(), action))) {
                    cp.play(new GamePlay(List.of(), action));
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}