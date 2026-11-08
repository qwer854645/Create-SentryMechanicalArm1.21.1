package euphy.upo.sentrymechanicalarm.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.util.AmmoStorageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Bulk ledger: codec and unmount preserve long counts and every sample component. */
public final class AmmoDepotMountedStorage extends MountedItemStorage {
    public record Stock(int slot,ItemStack sample,long count) {
        static final Codec<Stock> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.intRange(0,26).fieldOf("slot").forGetter(Stock::slot),
                ItemStack.CODEC.fieldOf("sample").forGetter(Stock::sample),
                Codec.LONG.validate(n->n>0?com.mojang.serialization.DataResult.success(n):com.mojang.serialization.DataResult.error(()->"Invalid ammo count"))
                        .fieldOf("count").forGetter(Stock::count)).apply(i,Stock::new));
    }
    public static final MapCodec<AmmoDepotMountedStorage> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            Codec.intRange(0,Integer.MAX_VALUE).fieldOf("capacity_stacks").forGetter(s->s.capacity),
            Stock.CODEC.sizeLimitedListOf(27).fieldOf("stock").forGetter(AmmoDepotMountedStorage::stock)
    ).apply(i,AmmoDepotMountedStorage::new));
    private final int capacity;
    private final ItemStack[] samples=new ItemStack[27];
    private final long[] counts=new long[27];

    public AmmoDepotMountedStorage(int capacity,List<Stock> saved){
        super(SentryAmmoDepotCompat.TYPE.get());this.capacity=capacity;Arrays.fill(samples,ItemStack.EMPTY);
        for(Stock entry:saved){
            if(entry.slot()<0 || entry.slot()>=27 || entry.count()<=0 || entry.sample().isEmpty())throw new IllegalArgumentException("Invalid ammo ledger");
            if(counts[entry.slot()]!=0)throw new IllegalArgumentException("Duplicate ammo slot");
            samples[entry.slot()]=entry.sample().copyWithCount(1);counts[entry.slot()]=entry.count();
        }
    }
    public static AmmoDepotMountedStorage capture(AmmoStorageBlockEntity depot){
        List<Stock> saved=new ArrayList<>();
        // Capture complete variants and long totals; no ItemStackHandler conversion.
        for(var entry:depot.getContents())saved.add(new Stock(saved.size(),entry.sample().copyWithCount(1),entry.count()));
        return new AmmoDepotMountedStorage(depot.getCapacityStacks(),saved);
    }
    public List<Stock> stock(){
        List<Stock> saved=new ArrayList<>();
        for(int slot=0;slot<27;slot++)if(counts[slot]>0)saved.add(new Stock(slot,samples[slot].copy(),counts[slot]));
        return saved;
    }
    public long count(int slot){return slot>=0 && slot<27?counts[slot]:0;}
    private static int stackSize(ItemStack sample){return Math.max(1,AmmoStorageHelper.getStackSize(sample));}
    private static long stacks(long count,ItemStack sample){int size=stackSize(sample);return count/size+(count%size==0?0:1);}
    public long usedStacks(){long total=0;for(int slot=0;slot<27;slot++)if(counts[slot]>0){long used=stacks(counts[slot],samples[slot]);total=total>Long.MAX_VALUE-used?Long.MAX_VALUE:total+used;}return total;}
    private int matching(ItemStack stack){for(int slot=0;slot<27;slot++)if(counts[slot]>0 && ItemStack.isSameItemSameComponents(samples[slot],stack))return slot;return -1;}
    @Override public int getSlots(){return 27;}
    @Override public ItemStack getStackInSlot(int slot){return count(slot)==0?ItemStack.EMPTY:samples[slot].copyWithCount((int)Math.min(Integer.MAX_VALUE,counts[slot]));}
    @Override public boolean isItemValid(int slot,ItemStack stack){return slot>=0 && slot<27 && AmmoStorageHelper.isValidAmmo(stack);}
    @Override public int getSlotLimit(int slot){return slot<0 || slot>=27?0:counts[slot]==0?Integer.MAX_VALUE:(int)Math.min(Integer.MAX_VALUE,(long)capacity*stackSize(samples[slot]));}
    @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
        if(!isItemValid(slot,stack))return stack;
        int match=matching(stack);
        if(match>=0 && match!=slot || counts[slot]>0 && !ItemStack.isSameItemSameComponents(samples[slot],stack))return stack;
        long used=usedStacks();if(used>capacity)return stack;
        long own=counts[slot];long available=((long)capacity-used+(own==0?0:stacks(own,stack)))*stackSize(stack)-own;
        int accepted=(int)Math.min(stack.getCount(),Math.max(0,available));
        if(!simulate && accepted>0){samples[slot]=stack.copyWithCount(1);counts[slot]+=accepted;}
        return accepted==stack.getCount()?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-accepted);
    }
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
        if(count(slot)==0 || amount<=0)return ItemStack.EMPTY;
        int take=(int)Math.min(counts[slot],Math.min(amount,samples[slot].getMaxStackSize()));
        ItemStack result=samples[slot].copyWithCount(take);
        if(!simulate){counts[slot]-=take;if(counts[slot]==0)samples[slot]=ItemStack.EMPTY;}
        return result;
    }
    @Override public void setStackInSlot(int slot,ItemStack stack){
        if(slot<0 || slot>=27)throw new IndexOutOfBoundsException(slot);
        ItemStack oldSample=samples[slot];long oldCount=counts[slot];samples[slot]=ItemStack.EMPTY;counts[slot]=0;
        if(stack.isEmpty())return;
        if(!insertItem(slot,stack,true).isEmpty()){
            samples[slot]=oldSample;counts[slot]=oldCount;throw new IllegalArgumentException("Invalid bulk ammo replacement");
        }
        insertItem(slot,stack,false);
    }
    @Override public void unmount(Level level,BlockState state,BlockPos pos,BlockEntity entity){
        if(!(entity instanceof AmmoStorageBlockEntity depot))return;
        CompoundTag tag=depot.saveWithoutMetadata(level.registryAccess());ListTag entries=new ListTag();
        for(Stock entry:stock()){
            CompoundTag saved=new CompoundTag();saved.putByte("Slot",(byte)entry.slot());
            saved.put("Stack",entry.sample().saveOptional(level.registryAccess()));saved.putLong("Count",entry.count());entries.add(saved);
        }
        tag.put("Entries",entries);depot.loadWithComponents(tag,level.registryAccess());depot.setChanged();
        level.updateNeighbourForOutputSignal(pos,state.getBlock());
    }
    @Override public boolean handleInteraction(ServerPlayer player,com.simibubi.create.content.contraptions.Contraption contraption,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info){
        // The generic chest menu cannot represent a bulk ledger. Never expose oversize slots through it.
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sentry.supply.depot_mounted"),true);return true;
    }
}
