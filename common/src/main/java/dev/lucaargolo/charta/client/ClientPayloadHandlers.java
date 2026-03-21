package dev.lucaargolo.charta.client;

// All client-side packet handlers live here so that payload classes
// in common/ have zero client imports — safe for dedicated servers.

import dev.lucaargolo.charta.client.render.screen.ConfirmScreen;
import dev.lucaargolo.charta.client.render.screen.HistoryScreen;
import dev.lucaargolo.charta.client.render.screen.TableScreen;
import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.block.entity.CardTableBlockEntity;
import dev.lucaargolo.charta.common.block.entity.ModBlockEntityTypes;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.impl.fun.FunScreen;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

public class ClientPayloadHandlers {

    public static void handleTexasHoldemChips(dev.lucaargolo.charta.common.network.TexasHoldemChipsPayload payload, Executor executor) {
        executor.execute(() -> {
            ChartaModClient.TABLE_POKER_CHIPS.put(payload.pos(), payload.chips());
            ChartaModClient.TABLE_POKER_GAME_SLOT_COUNT.put(payload.pos(), payload.gameSlotCount());
            ChartaModClient.TABLE_POKER_FOLDED.put(payload.pos(), payload.foldedMask());
            ChartaModClient.TABLE_POKER_ALLIN.put(payload.pos(), payload.allInMask());
            ChartaModClient.TABLE_POKER_STARTING_CHIPS.put(payload.pos(), payload.startingChips());
        });
    }

    public static void handleCardDecks(CardDecksPayload payload, Executor executor) {
        executor.execute(() -> ChartaMod.CARD_DECKS.setDecks(payload.cardDecks()));
    }

    public static void handleGameStart(GameStartPayload payload, Executor executor) {
        executor.execute(ChartaModClient.LOCAL_HISTORY::clear);
    }

    public static void handleImages(ImagesPayload payload, Executor executor) {
        executor.execute(() -> {
            ChartaModClient.clearImages();
            ChartaMod.SUIT_IMAGES.setImages(payload.suitImages());
            ChartaMod.CARD_IMAGES.setImages(payload.cardImages());
            ChartaMod.DECK_IMAGES.setImages(payload.deckImages());
            ChartaModClient.generateImages();
        });
    }

    public static void handlePlayerOptions(PlayerOptionsPayload payload, Executor executor) {
        executor.execute(() -> {
            ChartaModClient.LOCAL_OPTIONS.clear();
            ChartaModClient.LOCAL_OPTIONS.putAll(payload.playerOptions());
        });
    }

    public static void handleCardPlay(CardPlayPayload payload, Executor executor) {
        executor.execute(() -> {
            ChartaModClient.LOCAL_HISTORY.add(ImmutableTriple.of(payload.playerName(), payload.playerCards(), payload.play()));
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof HistoryScreen screen) {
                screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        });
    }

    public static void handleGameLeave(GameLeavePayload payload, Executor executor) {
        executor.execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ConfirmScreen(null, Component.translatable("message.charta.leaving_game"), true, () -> {
                if (minecraft.player != null) {
                    minecraft.player.stopRiding();
                    ChartaMod.getPacketManager().sendToServer(new GameLeavePayload());
                }
            }));
        });
    }

    public static void handleGameSlotComplete(GameSlotCompletePayload payload, Executor executor) {
        executor.execute(() -> {
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                level.getBlockEntity(payload.pos(), ModBlockEntityTypes.CARD_TABLE.get()).ifPresent(cardTable -> {
                    List<Card> list = new LinkedList<>(payload.cards());
                    if (payload.index() == cardTable.getSlotCount()) {
                        cardTable.addSlot(new GameSlot(list, payload.x(), payload.y(), payload.z(),
                                payload.angle(), payload.stackDirection(), payload.maxStack(), payload.centered()));
                    } else {
                        GameSlot tracked = cardTable.getSlot(payload.index());
                        tracked.setCards(list);
                        tracked.setX(payload.x());
                        tracked.setY(payload.y());
                        tracked.setZ(payload.z());
                        tracked.setAngle(payload.angle());
                        tracked.setStackDirection(payload.stackDirection());
                        tracked.setMaxStack(payload.maxStack());
                        tracked.setCentered(payload.centered());
                    }
                });
            }
        });
    }

    public static void handleGameSlotPosition(GameSlotPositionPayload payload, Executor executor) {
        executor.execute(() -> {
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                level.getBlockEntity(payload.pos(), ModBlockEntityTypes.CARD_TABLE.get()).ifPresent(cardTable -> {
                    GameSlot slot = cardTable.getSlot(payload.index());
                    slot.setX(payload.x());
                    slot.setY(payload.y());
                    slot.setZ(payload.z());
                    slot.setAngle(payload.angle());
                });
            }
        });
    }

    public static void handleGameSlotReset(GameSlotResetPayload payload, Executor executor) {
        executor.execute(() -> {
            net.minecraft.core.BlockPos pos = payload.pos();
            // Clear poker chip overlays — this fires when any game ends/resets,
            // so chips won't linger when a different game starts at the same table.
            ChartaModClient.TABLE_POKER_CHIPS.remove(pos);
            ChartaModClient.TABLE_POKER_GAME_SLOT_COUNT.remove(pos);
            ChartaModClient.TABLE_POKER_FOLDED.remove(pos);
            ChartaModClient.TABLE_POKER_ALLIN.remove(pos);
            ChartaModClient.TABLE_POKER_STARTING_CHIPS.remove(pos);

            Level level = Minecraft.getInstance().level;
            if (level != null) {
                level.getBlockEntity(pos, ModBlockEntityTypes.CARD_TABLE.get())
                        .ifPresent(CardTableBlockEntity::resetSlots);
            }
        });
    }

    public static void handleLastFun(LastFunPayload payload, Executor executor) {
        executor.execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.screen instanceof FunScreen funScreen) {
                mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.TOTEM_USE, mc.player.getSoundSource(), 1.0F, 1.0F, false);
                funScreen.displayItemActivation(payload.deckStack());
            }
        });
    }

    public static void handleTableScreen(TableScreenPayload payload, Executor executor) {
        executor.execute(() ->
                Minecraft.getInstance().setScreen(new TableScreen(payload.pos(), payload.deck(), payload.players()))
        );
    }

    public static void handleUpdateCarried(UpdateCardContainerCarriedPayload payload, Executor executor) {
        executor.execute(() -> {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof AbstractCardMenu<?, ?> cardMenu
                    && cardMenu.containerId == payload.containerId()) {
                cardMenu.setCarriedCards(payload.stateId(), new GameSlot(payload.cards()));
            }
        });
    }

    public static void handleUpdateSlot(UpdateCardContainerSlotPayload payload, Executor executor) {
        executor.execute(() -> {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof AbstractCardMenu<?, ?> cardMenu
                    && cardMenu.containerId == payload.containerId()) {
                cardMenu.setCards(payload.slotId(), payload.stateId(), payload.cards());
            }
        });
    }
}