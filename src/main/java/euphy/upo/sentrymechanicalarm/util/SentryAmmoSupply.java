package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.*;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.config.sync.SyncConfig;
import euphy.upo.sentrymechanicalarm.content.*;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;

/** Server-owned, bounded transfers. Special component variants never collapse into ammo counts. */
public final class SentryAmmoSupply {
    public enum State {NONE,NO_BOX,FULL,NO_STOCK,SPECIAL_ONLY,SUPPLIED,CREATIVE}
    private SentryAmmoSupply(){}
    public static void refillAdjacent(SentryArmBlockEntity arm){
        var level=arm.getLevel();
        if(level==null || level.isClientSide || arm instanceof VirtualSentryArmBlockEntity)return;
        List<IItemHandler> handlers=new ArrayList<>();
        for(Direction direction:Direction.values()){
            var pos=arm.getBlockPos().relative(direction);
            if(!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AmmoStorageBlockEntity))continue;
            var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,direction.getOpposite());
            if(handler!=null)handlers.add(handler);
        }
        if(handlers.isEmpty()){arm.setSupplyState(State.NONE);return;}
        refill(arm,combine(handlers));
    }
    private static IItemHandler combine(List<IItemHandler> handlers){
        record Slot(IItemHandler handler,int index){}
        java.util.function.IntFunction<Slot> locate=index->{
            if(index<0)return null;
            for(IItemHandler handler:handlers){if(index<handler.getSlots())return new Slot(handler,index);index-=handler.getSlots();}
            return null;
        };
        return new IItemHandler(){
            public int getSlots(){return handlers.stream().mapToInt(IItemHandler::getSlots).sum();}
            public ItemStack getStackInSlot(int slot){var s=locate.apply(slot);return s==null?ItemStack.EMPTY:s.handler().getStackInSlot(s.index());}
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){var s=locate.apply(slot);return s==null?stack:s.handler().insertItem(s.index(),stack,simulate);}
            public ItemStack extractItem(int slot,int amount,boolean simulate){var s=locate.apply(slot);return s==null?ItemStack.EMPTY:s.handler().extractItem(s.index(),amount,simulate);}
            public int getSlotLimit(int slot){var s=locate.apply(slot);return s==null?0:s.handler().getSlotLimit(s.index());}
            public boolean isItemValid(int slot,ItemStack stack){var s=locate.apply(slot);return s!=null && s.handler().isItemValid(s.index(),stack);}
        };
    }
    public static void refill(SentryArmBlockEntity arm,IItemHandler inventory){
        if(arm.getLevel()==null || arm.getLevel().isClientSide || inventory==null || !(arm.getHeldItem().getItem() instanceof IGun gun))return;
        var index=TimelessAPI.getCommonGunIndex(gun.getGunId(arm.getHeldItem()));if(index.isEmpty())return;
        var id=index.get().getGunData().getAmmoId();var ammo=TimelessAPI.getCommonAmmoIndex(id);if(ammo.isEmpty())return;
        ItemStack standard=AmmoItemBuilder.create().setId(id).setCount(1).build();
        boolean changed=false,compatible=false,needs=false,special=false,creative=false;
        for(int attached=0;attached<arm.attachedAmmoBoxes.size();attached++){
            if(!arm.attachedAmmoBoxes.get(attached).isEmpty())continue;
            for(int slot=0;slot<inventory.getSlots();slot++){
                ItemStack source=inventory.getStackInSlot(slot);
                if(!(source.getItem() instanceof IAmmoBox box) || !(box.isAllTypeCreative(source)
                        || id.equals(box.getAmmoId(source)) && (box.isCreative(source)||box.getAmmoCount(source)>0)))continue;
                ItemStack preview=inventory.extractItem(slot,1,true);if(preview.isEmpty())continue;
                ItemStack extracted=inventory.extractItem(slot,1,false);if(extracted.isEmpty())continue;
                arm.attachedAmmoBoxes.set(attached,extracted);changed=true;break;
            }
        }
        int budget=128; // Bound work even for huge depot stock or unusual stack sizes.
        for(ItemStack box:arm.attachedAmmoBoxes){
            if(!(box.getItem() instanceof IAmmoBox b))continue;
            if(b.isAllTypeCreative(box) || b.isCreative(box) && id.equals(b.getAmmoId(box))){creative=true;compatible=true;continue;}
            if(b.isCreative(box) || b.isAllTypeCreative(box))continue;
            int count=Math.max(0,b.getAmmoCount(box));if(count>0 && !id.equals(b.getAmmoId(box)))continue;
            compatible=true;
            int cap=(int)Math.min(Integer.MAX_VALUE,(long)ammo.get().getStackSize()*SyncConfig.AMMO_BOX_STACK_SIZE.get()*((long)Math.max(0,b.getAmmoLevel(box))+1));
            if(count>=cap)continue;
            needs=true;
            for(int slot=0;slot<inventory.getSlots() && count<cap && budget>0;slot++){
                ItemStack source=inventory.getStackInSlot(slot);
                if(!(source.getItem() instanceof IAmmo a) || !id.equals(a.getAmmoId(source)))continue;
                if(!ItemStack.isSameItemSameComponents(source,standard)){special=true;continue;}
                while(count<cap && budget-->0){
                    int request=Math.min(cap-count,Math.max(1,standard.getMaxStackSize()));
                    ItemStack preview=inventory.extractItem(slot,request,true);
                    if(preview.isEmpty() || !ItemStack.isSameItemSameComponents(preview,standard))break;
                    ItemStack extracted=inventory.extractItem(slot,request,false);if(extracted.isEmpty())break;
                    count+=extracted.getCount();b.setAmmoId(box,id);b.setAmmoCount(box,count);changed=true;
                }
            }
        }
        if(changed){arm.setChanged();arm.sendData();}
        arm.setSupplyState(changed?State.SUPPLIED:creative?State.CREATIVE:!compatible?State.NO_BOX:!needs?State.FULL:special?State.SPECIAL_ONLY:State.NO_STOCK);
    }
}
