package org.valhrek.wurm.bankofwurm;

import com.wurmonline.server.Items;
import com.wurmonline.server.banks.Bank;
import com.wurmonline.server.banks.BankSlot;
import com.wurmonline.server.banks.Banks;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import org.gotti.wurmunlimited.modsupport.actions.ActionEntryBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ActionPropagation;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class BankSlotPerformer implements ActionPerformer {

    public ActionEntry actionEntry;
    public BankSlotPerformer(){
        actionEntry = new ActionEntryBuilder( (short) ModActions.getNextActionId(), "Add bank slot", "adding a bank slot", new int[]{})
                .range(3)
                .priority(10)
                .build();

        ModActions.registerAction(actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) {
        return action(action, performer, target, num, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item target, short num, float counter) {
        // get players bank
        Bank bank = Banks.getBank(performer.getWurmId());

        if (bank == null){
            performer.getCommunicator().sendNormalServerMessage("You must establish a bank before adding bank slots.");
            return propagate(action,
                    ActionPropagation.FINISH_ACTION,
                    ActionPropagation.NO_SERVER_PROPAGATION,
                    ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
        }

        // get number of slots
        BankSlot[] slots = bank.slots;
        int size = bank.size;
        int newSize = size + 1;

        // if adding a slot exceeds the max slots then inform player
        if ( newSize > BankOfWurm.maxBankSlots){
            performer.getCommunicator().sendNormalServerMessage("You already have the maximum number of bank slots.");
        } else{
            // use reflection to call bank incrementbankslots method
            try {
                Method m = Bank.class.getDeclaredMethod("incrementBankSize");
                if (!(boolean) m.invoke(bank)) {
                    performer.getCommunicator().sendNormalServerMessage("Incrementing bank size failed!");
                    return propagate(action,
                            ActionPropagation.FINISH_ACTION,
                            ActionPropagation.NO_SERVER_PROPAGATION,
                            ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
                }

            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            Items.destroyItem(target.getWurmId());
            String sb = "Congratulations! Your bank now has " + newSize + " slots.";
            performer.getCommunicator().sendNormalServerMessage(sb);
        }

        return propagate(action,
                ActionPropagation.FINISH_ACTION,
                ActionPropagation.NO_SERVER_PROPAGATION,
                ActionPropagation.NO_ACTION_PERFORMER_PROPAGATION);
    }

    @Override
    public short getActionId() {
        return actionEntry.getNumber();
    }
}
