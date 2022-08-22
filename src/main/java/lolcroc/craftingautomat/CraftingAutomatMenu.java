package lolcroc.craftingautomat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class CraftingAutomatMenu extends AbstractContainerMenu {

    private final CraftingAutomatBlockEntity tile;
    
    // Only on client
    public CraftingAutomatMenu(int id, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(id, playerInventory, (CraftingAutomatBlockEntity) Minecraft.getInstance().level.getBlockEntity(extraData.readBlockPos()));
    }
    
    public CraftingAutomatMenu(int id, Inventory playerInventory, CraftingAutomatBlockEntity te) {
        super(CraftingAutomat.AUTOCRAFTER_MENU.get(), id);
        tile = te;

        // Result slot
        te.resultOptional.ifPresent(h -> {
            addSlot(new CraftingAutomatResultSlot(h, playerInventory.player, te, 0, 124, 35));
        });

        // Crafting matrix
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                int finalI = i;
                int finalJ = j;
                te.matrixOptional.ifPresent(h -> {
                    addSlot(new BetterSlotItemHandler(h, finalJ + finalI * 3, 30 + finalJ * 18, 17 + finalI * 18));
                });
            }
        }
        
        // Crafting buffer
        for (int l = 0; l < 9; ++l) {
            int finalL = l;
            te.bufferOptional.ifPresent(h -> {
                addSlot(new BetterSlotItemHandler(h, finalL, 8 + finalL * 18, 84));
            });
        }

        // Player inventory
        for (int k = 0; k < 3; ++k) {
            for (int i1 = 0; i1 < 9; ++i1) {
                addSlot(new Slot(playerInventory, i1 + k * 9 + 9, 8 + i1 * 18, 115 + k * 18));
            }
        }

        // Player hotbar
        for (int l = 0; l < 9; ++l) {
            addSlot(new Slot(playerInventory, l, 8 + l * 18, 173));
        }

        addDataSlots(te.dataAccess);
    }
    
    @OnlyIn(Dist.CLIENT)
    public int getProgressWidth() {
        int ticks = tile.ticksActive;

        if (ticks <= 0) {
            return 0; // Easy return
        }
        else if (ticks > tile.crafingTicks) {
            return (tile.cooldownTicks + tile.crafingTicks - ticks) * 24 / tile.cooldownTicks;
        }
        else {
            return ticks * 24 / tile.crafingTicks;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public CraftingAutomatBlockEntity.CraftingFlag getCraftingFlag() {
        return tile.craftingFlag;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()), player, CraftingAutomat.AUTOCRAFTER_BLOCK.get());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof CraftingAutomatResultSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index == 0) {
                itemstack1.getItem().onCraftedBy(itemstack1, player.level, player);

                // Merge result slot to player inv
                if (!moveItemStackTo(itemstack1, 19, 55, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            }
            else if (index >= 1 && index < 10) {
                // Merge matrix to buffer, then to full player inv
                if (!moveItemStackTo(itemstack1, 10, 19, false) && !moveItemStackTo(itemstack1, 19, 55, true)) {
                    return ItemStack.EMPTY;
                }
            }
            else if (index >= 10 && index < 19) {
                // Merge buffer to full player inv
                if (!moveItemStackTo(itemstack1, 19, 55, true)) {
                    return ItemStack.EMPTY;
                }
            }
            else if (index >= 19 && index < 46) {
                // Merge player inv to buffer, then to hotbar
                if (!moveItemStackTo(itemstack1, 10, 19, false) && !moveItemStackTo(itemstack1, 46, 55, false)) {
                    return ItemStack.EMPTY;
                }
            }
            else if (!moveItemStackTo(itemstack1, 10, 19, false) && !moveItemStackTo(itemstack1, 19, 46, false)) {
                // Merge hotbar to buffer, then to player inv
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            }
            else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);

            if (index == 0) {
                player.drop(itemstack1, false);
            }
        }

        return itemstack;
    }

    private static class BetterSlotItemHandler extends SlotItemHandler {

        public BetterSlotItemHandler(UnsafeItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public int getMaxStackSize(@NotNull ItemStack stack) {
            ItemStack maxAdd = stack.copy();
            int maxInput = stack.getMaxStackSize();
            maxAdd.setCount(maxInput);

            int index = getSlotIndex();
            UnsafeItemStackHandler handler = getItemHandler();
            ItemStack currentStack = handler.getStackInSlot(index);

            handler.unsafeSetStackInSlot(index, ItemStack.EMPTY);

            ItemStack remainder = handler.insertItem(index, maxAdd, true);

            handler.unsafeSetStackInSlot(index, currentStack);

            return maxInput - remainder.getCount();
        }

        @Override
        public UnsafeItemStackHandler getItemHandler() {
            return (UnsafeItemStackHandler) super.getItemHandler();
        }
    }

}