package dev.lucaargolo.charta.common.menu;

import dev.lucaargolo.charta.common.ChartaMod;
import dev.lucaargolo.charta.common.game.impl.blackjack.BlackjackMenu;
import dev.lucaargolo.charta.common.game.impl.crazyeights.CrazyEightsMenu;
import dev.lucaargolo.charta.common.game.impl.fun.FunMenu;
import dev.lucaargolo.charta.common.game.impl.solitaire.SolitaireMenu;
import dev.lucaargolo.charta.common.game.impl.texasholdem.TexasHoldemMenu;
import dev.lucaargolo.charta.common.registry.ModMenuTypeRegistry;

public class ModMenuTypes {

    public static final ModMenuTypeRegistry REGISTRY = ChartaMod.menuTypeRegistry();

    public static final ModMenuTypeRegistry.AdvancedMenuTypeEntry<CrazyEightsMenu, CrazyEightsMenu.Definition> CRAZY_EIGHTS = REGISTRY.register("crazy_eights", CrazyEightsMenu::new, CrazyEightsMenu.Definition.STREAM_CODEC);
    public static final ModMenuTypeRegistry.AdvancedMenuTypeEntry<FunMenu, FunMenu.Definition> FUN = REGISTRY.register("fun", FunMenu::new, FunMenu.Definition.STREAM_CODEC);
    public static final ModMenuTypeRegistry.AdvancedMenuTypeEntry<SolitaireMenu, SolitaireMenu.Definition> SOLITAIRE = REGISTRY.register("solitaire", SolitaireMenu::new, SolitaireMenu.Definition.STREAM_CODEC);
    public static final ModMenuTypeRegistry.AdvancedMenuTypeEntry<TexasHoldemMenu, AbstractCardMenu.Definition> TEXAS_HOLDEM = REGISTRY.register("texas_holdem", TexasHoldemMenu::new, AbstractCardMenu.Definition.STREAM_CODEC);
    public static final ModMenuTypeRegistry.AdvancedMenuTypeEntry<BlackjackMenu, AbstractCardMenu.Definition> BLACKJACK = REGISTRY.register("blackjack", BlackjackMenu::new, AbstractCardMenu.Definition.STREAM_CODEC);

}