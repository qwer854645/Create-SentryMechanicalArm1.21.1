package euphy.upo.sentrymechanicalarm.content;

import euphy.upo.sentrymechanicalarm.registry.SentryRegistry;
import euphy.upo.sentrymechanicalarm.util.CasingCollectors;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Ordinary 27-slot storage; item components and counts use vanilla container persistence. */
public final class CasingCollectorBlockEntity extends BlockEntity implements Container,MenuProvider {
    public static final int SLOTS=27;
    private final NonNullList<ItemStack> items=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
    private final IItemHandlerModifiable handler=new InvWrapper(this);
    public CasingCollectorBlockEntity(BlockPos pos,BlockState state){super(SentryRegistry.CASING_COLLECTOR_BE.get(),pos,state);}
    public IItemHandlerModifiable getItemHandler(){return handler;}
    @Override public int getContainerSize(){return SLOTS;}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return items.get(slot);}
    @Override public ItemStack removeItem(int slot,int amount){var stack=ContainerHelper.removeItem(items,slot,amount);if(!stack.isEmpty())setChanged();return stack;}
    @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(items,slot);}
    @Override public void setItem(int slot,ItemStack stack){items.set(slot,stack);if(!stack.isEmpty())stack.setCount(Math.min(stack.getCount(),Math.min(getMaxStackSize(),stack.getMaxStackSize())));setChanged();}
    @Override public boolean stillValid(Player player){
        if(level==null||isRemoved()||!level.isLoaded(worldPosition)||level.getBlockEntity(worldPosition)!=this)return false;
        var projected=CasingCollectors.worldPosition(level,worldPosition);return projected!=null&&player.distanceToSqr(projected)<=64;
    }
    @Override public void clearContent(){items.clear();setChanged();}
    @Override public Component getDisplayName(){return Component.translatable("block.sentrymechanicalarm.casing_collector");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player player){return ChestMenu.threeRows(id,inventory,this);}
    @Override public void setChanged(){super.setChanged();if(level!=null&&!level.isClientSide)level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);items.clear();ContainerHelper.loadAllItems(tag,items,registries);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)CasingCollectors.register(this);}
    @Override public void setRemoved(){CasingCollectors.unregister(this);super.setRemoved();}
}
