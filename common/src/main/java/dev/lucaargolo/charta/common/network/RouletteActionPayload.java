package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.impl.roulette.RouletteGame;
import dev.lucaargolo.charta.common.game.impl.roulette.RouletteMenu;
import dev.lucaargolo.charta.mixed.LivingEntityMixed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public record RouletteActionPayload(int action) implements CustomPacketPayload {

    public static final Type<RouletteActionPayload> TYPE =
            new Type<>(ChartaMod.id("roulette_action"));

    public static final StreamCodec<ByteBuf, RouletteActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RouletteActionPayload::action,
                    RouletteActionPayload::new);

    public static void handleServer(RouletteActionPayload payload, ServerPlayer player, Executor executor) {
        executor.execute(() -> {
            if (!(player.containerMenu instanceof RouletteMenu menu)) return;

            CardPlayer cardPlayer = ((LivingEntityMixed) player).charta_getCardPlayer();
            RouletteGame game = menu.getGame();
            int idx = game.getPlayers().indexOf(cardPlayer);
            if (idx < 0) return;

            int action = payload.action();

            // Determine bet type from action code
            int betType;
            if (action == RouletteGame.BET_RED) {
                betType = RouletteGame.BET_RED_T;
            } else if (action == RouletteGame.BET_BLACK) {
                betType = RouletteGame.BET_BLACK_T;
            } else if (action >= RouletteGame.BET_RANK && action <= RouletteGame.BET_RANK + 12) {
                betType = 3 + (action - RouletteGame.BET_RANK);  // 3..15
            } else {
                return; // unknown action
            }

            game.handleBet(idx, betType);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
