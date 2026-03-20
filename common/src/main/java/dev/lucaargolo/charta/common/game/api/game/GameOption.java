package dev.lucaargolo.charta.common.game.api.game;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class GameOption<T> {

    private final Component title;
    private final Component description;

    @Nullable public Consumer<T> consumer;
    private byte value;

    public GameOption(T value, Component title, Component description) {
        this.value = toByte(value);
        this.title = title;
        this.description = description;
    }

    protected abstract byte toByte(T value);
    protected abstract T fromByte(byte value);

    public Component getTitle() { return title; }
    public Component getDescription() { return description; }

    public T get() { return fromByte(value); }

    public void set(T value) {
        this.value = toByte(value);
        if (consumer != null) consumer.accept(value);
    }

    public byte getValue() { return value; }

    public void setValue(byte value) {
        this.value = value;
        if (consumer != null) consumer.accept(this.get());
    }

    // -------------------------------------------------------------------------

    public static class Bool extends GameOption<Boolean> {

        public Bool(boolean value, Component title, Component description) {
            super(value, title, description);
        }

        @Override protected byte toByte(Boolean value) { return value ? (byte) 1 : (byte) 0; }
        @Override protected Boolean fromByte(byte value) { return value == 1; }
    }

    public static class Number extends GameOption<Integer> {

        private final int min;
        private final int max;
        /** If > 1, the client widget multiplies the displayed value by this factor. */
        private final int displayMultiplier;

        public Number(int value, int min, int max, Component title, Component description) {
            this(value, min, max, 1, title, description);
        }

        public Number(int value, int min, int max, int displayMultiplier, Component title, Component description) {
            super(value, title, description);
            this.min = min;
            this.max = max;
            this.displayMultiplier = displayMultiplier;
        }

        public int getMin() { return min; }
        public int getMax() { return max; }
        public int getDisplayMultiplier() { return displayMultiplier; }

        @Override protected byte toByte(Integer value) { return value.byteValue(); }
        @Override protected Integer fromByte(byte value) { return (int) value; }
    }
}
