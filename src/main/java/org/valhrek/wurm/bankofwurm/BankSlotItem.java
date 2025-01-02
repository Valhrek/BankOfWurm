package org.valhrek.wurm.bankofwurm;

import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTypes;
import com.wurmonline.shared.constants.IconConstants;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;

import java.io.IOException;

public class BankSlotItem {
    public static ItemTemplate bankslot;

    public static int bankSlotId;

    public static void registerBankSlot() throws IOException {
        bankslot = new ItemTemplateBuilder("valhrek.bankofwurm.bankslot")
                .name("Bank Slot", "Bank Slots", "Use to gain an additional bank slot.")
                .modelName("model.resource.sheet.papyrus.")
                .imageNumber((short) IconConstants.ICON_SCROLL_BLANK)
                .itemTypes(new short[]{
                        ItemTypes.ITEM_TYPE_NAMED,
                        ItemTypes.ITEM_TYPE_ALWAYS_BANKABLE,
                        ItemTypes.ITEM_TYPE_NODISCARD
                })
                .behaviourType((short) 1)
                .dimensions(5,5,1)
                .build();

        bankSlotId = bankslot.getTemplateId();
    }
}
