package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.impl.blackjack.BlackjackGame;
import dev.lucaargolo.charta.common.game.impl.blackjack.BlackjackMenu;
import dev.lucaargolo.charta.mixed.LivingEntityMixed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;

public record BlackjackActionPayload(int action) implements CustomPacketPayload {

    public static final Type<BlackjackActionPayload> TYPE =
            new Type<>(ChartaMod.id("blackjack_action"));

    public static final StreamCodec<ByteBuf, BlackjackActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BlackjackActionPayload::action,
                    BlackjackActionPayload::new);

    public static void handleServer(BlackjackActionPayload payload, ServerPlayer player, Executor executor) {
        executor.execute(() -> {
            if (!(player.containerMenu instanceof BlackjackMenu menu)) return;

            CardPlayer cardPlayer = ((LivingEntityMixed) player).charta_getCardPlayer();
            BlackjackGame game = menu.getGame();
            int idx = game.getPlayers().indexOf(cardPlayer);
            if (idx < 0) return;

            int action = payload.action();

            // BET: handled directly — doesn't need currentPlayer check
            if (action >= BlackjackGame.ACTION_BET) {
                if (game.getPhase() == BlackjackGame.Phase.BETTING) {
                    game.handleBet(idx, action - BlackjackGame.ACTION_BET);
                }
                return;
            }

            // HIT/STAND/DOUBLE: only current player
            if (game.getCurrentPlayer() != cardPlayer) return;

            boolean valid = action == BlackjackGame.ACTION_HIT
                    || action == BlackjackGame.ACTION_STAND
                    || action == BlackjackGame.ACTION_DOUBLE;
            if (!valid) return;

            cardPlayer.play(new GamePlay(List.of(), action));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}