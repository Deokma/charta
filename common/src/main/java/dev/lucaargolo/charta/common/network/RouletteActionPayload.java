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
            int betType;
            if (action == RouletteGame.ACTION_RED) {
                betType = RouletteGame.BET_RED;
            } else if (action == RouletteGame.ACTION_BLACK) {
                betType = RouletteGame.BET_BLACK;
            } else if (action >= RouletteGame.ACTION_NUMBER && action <= RouletteGame.ACTION_NUMBER + 36) {
                betType = 10 + (action - RouletteGame.ACTION_NUMBER); // 10..46
            } else {
                return;
            }
            game.handleBet(idx, betType);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
