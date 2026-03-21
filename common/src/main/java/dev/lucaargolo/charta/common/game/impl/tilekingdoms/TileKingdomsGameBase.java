package dev.lucaargolo.charta.common.game.impl.tilekingdoms;

import dev.lucaargolo.charta.common.game.Games;
import dev.lucaargolo.charta.common.game.api.CardPlayer;
import dev.lucaargolo.charta.common.game.api.GamePlay;
import dev.lucaargolo.charta.common.game.api.card.Card;
import dev.lucaargolo.charta.common.game.api.card.Deck;
import dev.lucaargolo.charta.common.game.api.game.Game;
import dev.lucaargolo.charta.common.menu.AbstractCardMenu;
import dev.lucaargolo.charta.common.menu.ModMenuTypes;
import dev.lucaargolo.charta.common.registry.ModMenuTypeRegistry;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.function.Predicate;

public abstract class TileKingdomsGameBase extends Game<TileKingdomsGame, TileKingdomsMenu> {

    public TileKingdomsGameBase(List<CardPlayer> players, Deck deck) {
        super(players, deck);
    }

    @Override
    public ModMenuTypeRegistry.AdvancedMenuTypeEntry<TileKingdomsMenu, AbstractCardMenu.Definition> getMenuType() {
        return ModMenuTypes.TILE_KINGDOMS;
    }

    @Override
    public TileKingdomsMenu createMenu(int containerId, Inventory inv, AbstractCardMenu.Definition def) {
        return new TileKingdomsMenu(containerId, inv, def);
    }

    @Override
    public Predicate<Deck> getDeckPredicate() { return deck -> true; }

    @Override
    public Predicate<Card> getCardPredicate() { return card -> false; } // no cards used
}