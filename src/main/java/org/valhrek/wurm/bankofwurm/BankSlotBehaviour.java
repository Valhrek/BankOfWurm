package org.valhrek.wurm.bankofwurm;

import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;

public class BankSlotBehaviour implements BehaviourProvider {

    final List<ActionEntry> useBankSlot;
    final BankSlotPerformer bankSlotPerformer;

    public BankSlotBehaviour(){
        this.bankSlotPerformer = new BankSlotPerformer();
        this.useBankSlot = Collections.singletonList(this.bankSlotPerformer.actionEntry);

        ModActions.registerActionPerformer(this.bankSlotPerformer);
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item target) {
        if (target.getTemplateId() == BankSlotItem.bankSlotId){
            return useBankSlot;
        }
        return null;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) {
        return getBehavioursFor(performer, target);
    }
}
