package dev.lucaargolo.charta.common.item;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tile Kingdoms box item.
 * Extends DeckItem so the CardTableBlock accepts it as the "deck" placed on the table
 * (visible 3-D render, picked up on shift-right-click, starts the game via TableScreen).
 * On right-click in hand opens a tile viewer screen.
 */
public class TileKingdomsBoxItem extends DeckItem {

    /** A null-deck resource — TileKingdoms doesn't need a card deck. */
    public static final ResourceLocation FAKE_DECK_ID = ChartaMod.id("tile_kingdoms_deck");

    /** Client-side opener for the tile viewer. Set by ChartaModClient. */
    public static Runnable TILE_VIEWER_OPENER = null;

    public TileKingdomsBoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable("item.charta.tile_kingdoms_box");
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        // Open tile viewer on client
        if (level.isClientSide() && TILE_VIEWER_OPENER != null) {
            TILE_VIEWER_OPENER.run();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("Place on a Card Table to play Tile Kingdoms").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("2-5 players  •  ~40 tiles  •  No meeple limit").withStyle(ChatFormatting.GRAY));
    }

    /** Returns null — Tile Kingdoms has no card deck. CardTableBlock handles null gracefully. */
    @Nullable
            //@Override
    public static Deck getDeck(ItemStack stack) { return null; }
}