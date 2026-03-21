package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.block.entity.ModBlockEntityTypes;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
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

    /** action == ROTATE: rotate current tile */
    public static final int ROTATE = -1;
    /** action >= 0: packed placement (see TileKingdomsGame.packAction) */

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

            if (payload.action() == ROTATE) {
                game.rotateTile();
            } else {
                // Placement
                if (game.canPlay(cp, new GamePlay(List.of(), payload.action()))) {
                    cp.play(new GamePlay(List.of(), payload.action()));
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}