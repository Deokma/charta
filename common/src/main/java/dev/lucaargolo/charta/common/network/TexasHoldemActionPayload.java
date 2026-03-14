package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.impl.texasholdem.TexasHoldemGame;
import dev.lucaargolo.charta.common.game.impl.texasholdem.TexasHoldemMenu;
import dev.lucaargolo.charta.mixed.LivingEntityMixed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Sent from client → server when the local player chooses a betting action
 * in Texas Hold'em (Fold, Check/Call, Raise Min, All-In).
 *
 * The {@code action} field maps to one of the {@code ACTION_*} constants
 * defined in {@link TexasHoldemGame}.
 */
public record TexasHoldemActionPayload(int action) implements CustomPacketPayload {

    public static final Type<TexasHoldemActionPayload> TYPE =
            new Type<>(ChartaMod.id("texas_holdem_action"));

    public static final StreamCodec<ByteBuf, TexasHoldemActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    TexasHoldemActionPayload::action,
                    TexasHoldemActionPayload::new);

    /**
     * Server-side handler: forwards the chosen action to the game loop
     * by completing the player's pending {@code afterPlay} future.
     */
    public static void handleServer(TexasHoldemActionPayload payload,
                                    ServerPlayer player,
                                    Executor executor) {
        executor.execute(() -> {
            if (!(player.containerMenu instanceof TexasHoldemMenu menu)) return;

            CardPlayer cardPlayer = ((LivingEntityMixed) player).charta_getCardPlayer();
            TexasHoldemGame game = menu.getGame();

            // Validate: only the current player may send an action
            if (game.getCurrentPlayer() != cardPlayer) return;

            int action = payload.action();
            if (action != TexasHoldemGame.ACTION_FOLD
                    && action != TexasHoldemGame.ACTION_CALL
                    && action != TexasHoldemGame.ACTION_RAISE_MIN
                    && action != TexasHoldemGame.ACTION_ALL_IN) {
                ChartaMod.LOGGER.warn("TexasHoldemActionPayload: unknown action {} from {}",
                        action, player.getName().getString());
                return;
            }

            // Complete the CompletableFuture waiting in runBettingRound()
            cardPlayer.play(new GamePlay(List.of(), action));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
