package dev.lucaargolo.charta.common.network;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.GameSlot;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.utils.ExpandedStreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record GameSlotCompletePayload(BlockPos pos, int index, List<Card> cards, float x, float y, float z, float angle, Direction stackDirection, float maxStack, boolean centered) implements CustomPacketPayload {

    public GameSlotCompletePayload(BlockPos pos, int index, GameSlot slot) {
        this(pos, index, slot.stream().toList(), slot.getX(), slot.getY(), slot.getZ(), slot.getAngle(), slot.getStackDirection(), slot.getMaxStack(), slot.isCentered());
    }

    public static final CustomPacketPayload.Type<GameSlotCompletePayload> TYPE = new CustomPacketPayload.Type<>(ChartaMod.id("game_slot_complete"));

    public static StreamCodec<RegistryFriendlyByteBuf, GameSlotCompletePayload> STREAM_CODEC = ExpandedStreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GameSlotCompletePayload::pos,
            ByteBufCodecs.INT,
            GameSlotCompletePayload::index,
            ByteBufCodecs.collection(ArrayList::new, Card.STREAM_CODEC),
            GameSlotCompletePayload::cards,
            ByteBufCodecs.FLOAT,
            GameSlotCompletePayload::x,
            ByteBufCodecs.FLOAT,
            GameSlotCompletePayload::y,
            ByteBufCodecs.FLOAT,
            GameSlotCompletePayload::z,
            ByteBufCodecs.FLOAT,
            GameSlotCompletePayload::angle,
            Direction.STREAM_CODEC,
            GameSlotCompletePayload::stackDirection,
            ByteBufCodecs.FLOAT,
            GameSlotCompletePayload::maxStack,
            ByteBufCodecs.BOOL,
            GameSlotCompletePayload::centered,
            GameSlotCompletePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
