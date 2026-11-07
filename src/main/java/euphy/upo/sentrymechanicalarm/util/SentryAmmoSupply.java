package euphy.upo.sentrymechanicalarm.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.*;
import com.tacz.guns.config.sync.SyncConfig;
import euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Transfers physical ammunition once, through the inventory extraction contract. */
public final class SentryAmmoSupply {
    private SentryAmmoSupply(){}
    public static void refill(SentryArmBlockEntity arm,IItemHandler inventory){
        if(inventory==null || !(arm.getHeldItem().getItem() instanceof IGun gun))return;
        var index=TimelessAPI.getCommonGunIndex(gun.getGunId(arm.getHeldItem()));if(index.isEmpty())return;
        var id=index.get().getGunData().getAmmoId();var ammo=TimelessAPI.getCommonAmmoIndex(id);if(ammo.isEmpty())return;
        for(int attached=0;attached<arm.attachedAmmoBoxes.size();attached++){
            if(!arm.attachedAmmoBoxes.get(attached).isEmpty())continue;
            for(int slot=0;slot<inventory.getSlots();slot++){
                ItemStack source=inventory.getStackInSlot(slot);
                if(!(source.getItem() instanceof IAmmoBox box) || !(box.isAllTypeCreative(source)
                        || id.equals(box.getAmmoId(source)) && (box.isCreative(source)||box.getAmmoCount(source)>0)))continue;
                ItemStack extracted=inventory.extractItem(slot,1,false);
                if(extracted.isEmpty())continue;
                arm.attachedAmmoBoxes.set(attached,extracted);arm.setChanged();arm.sendData();break;
            }
        }
        for(ItemStack box:arm.attachedAmmoBoxes){
            if(!(box.getItem() instanceof IAmmoBox b) || b.isCreative(box) || b.isAllTypeCreative(box))continue;
            int count=Math.max(0,b.getAmmoCount(box));if(count>0 && !id.equals(b.getAmmoId(box)))continue;
            int cap=(int)Math.min(Integer.MAX_VALUE,(long)ammo.get().getStackSize()*SyncConfig.AMMO_BOX_STACK_SIZE.get()*(Math.max(0,b.getAmmoLevel(box))+1));
            for(int slot=0;slot<inventory.getSlots() && count<cap;slot++){
                ItemStack source=inventory.getStackInSlot(slot);
                if(!(source.getItem() instanceof IAmmo a) || !id.equals(a.getAmmoId(source)))continue;
                ItemStack extracted=inventory.extractItem(slot,Math.min(cap-count,source.getCount()),false);
                if(!extracted.isEmpty()){count+=extracted.getCount();b.setAmmoId(box,id);b.setAmmoCount(box,count);arm.setChanged();arm.sendData();}
            }
        }
    }
}
