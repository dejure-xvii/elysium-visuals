package dev.elysium.visuals.client.module.impl.utils;

import dev.elysium.visuals.client.module.Category;
import dev.elysium.visuals.client.module.Module;
import dev.elysium.visuals.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Основа для модулей «свап на предмет, использовать, вернуться». Работает по бинду, только хотбар. */
public abstract class ClickSwapModule extends Module {
    private static final int HOTBAR = 9;

    private enum State { IDLE, SWAP, USE, RETURN }

    private final NumberSetting swapDelay = add(new NumberSetting("swap_delay", "Тики до свапа", 1, 1, 5, 1));
    private final NumberSetting returnDelay = add(new NumberSetting("return_delay", "Тики до возврата", 5, 1, 5, 1));

    private State state = State.IDLE;
    private int previousSlot = -1;
    private int itemSlot = -1;
    private int ticks;

    protected ClickSwapModule(String id, String name, String description) {
        super(id, name, description, Category.UTILS);
        enableByDefault();
    }

    /** Подходит ли предмет. */
    protected abstract boolean matches(ItemStack stack);

    /** Действие, когда нужный предмет уже в руке. */
    protected abstract void use(Minecraft mc, LocalPlayer player);


    protected boolean bindIsAction() {
        return true;
    }


    protected void onBindPressed(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (state != State.IDLE || player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        int selected = player.getInventory().getSelectedSlot();
        int slot = findItem(player);
        if (selected < 0 || selected >= HOTBAR || slot == -1) {
            return;
        }
        previousSlot = selected;
        itemSlot = slot;
        ticks = 0;
        state = State.SWAP;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (state == State.IDLE) {
            return;
        }
        if (player == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }
        switch (state) {
            case SWAP -> {
                if (++ticks >= swapDelay.floatValue()) {
                    player.getInventory().setSelectedSlot(itemSlot);
                    state = State.USE;
                }
            }
            case USE -> {
                use(mc, player);
                ticks = 0;
                state = State.RETURN;
            }
            case RETURN -> {
                if (++ticks >= returnDelay.floatValue()) {
                    player.getInventory().setSelectedSlot(previousSlot);
                    reset();
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && previousSlot >= 0 && state != State.IDLE) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        reset();
    }

    private void reset() {
        previousSlot = -1;
        itemSlot = -1;
        ticks = 0;
        state = State.IDLE;
    }

    private int findItem(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();
        if (matches(inv.getItem(selected))) {
            return selected;
        }
        for (int i = 0; i < HOTBAR; i++) {
            if (matches(inv.getItem(i))) {
                return i;
            }
        }
        return -1;
    }
}