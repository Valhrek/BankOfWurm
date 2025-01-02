package org.valhrek.wurm.bankofwurm;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BankOfWurm implements WurmServerMod, PreInitable, Configurable, Versioned, ItemTemplatesCreatedListener, ServerStartedListener {
    private static final Logger logger = Logger.getLogger(BankOfWurm.class.getName());

    public static int startingBankSlots;
    public static int maxBankSlots;
    public static boolean disableExtraFoodDamage;

    @Override
    public void configure(Properties properties) {
        startingBankSlots = Integer.parseInt(properties.getProperty("startingBankSlots", "5"));
        maxBankSlots = Integer.parseInt(properties.getProperty("maxBankSlots", "15"));
        disableExtraFoodDamage = Boolean.parseBoolean(properties.getProperty("disableExtraFoodDamage", "true"));
    }

    @Override
    public void preInit() {
        // allow bank slots to be mutable
        // add new method to increment bank size and slots
        try {
            CtClass ctBank = HookManager.getInstance().getClassPool().get(
                    "com.wurmonline.server.banks.Bank");
            // clear final modifier on slots
            CtField bankslotsField = ctBank.getDeclaredField("slots");
            int modifiers = bankslotsField.getModifiers();
            int notFinalModifier = Modifier.clear(modifiers, Modifier.FINAL);
            bankslotsField.setModifiers(notFinalModifier);

            // clear final modifier on size
            CtField sizeField = ctBank.getDeclaredField("size");
            modifiers = sizeField.getModifiers();
            notFinalModifier = Modifier.clear(modifiers, Modifier.FINAL);
            sizeField.setModifiers(notFinalModifier);

            // increase bank capacity by one
            // update size in database
            // create larger bankslot array and copy over any existing BankSlots
            CtMethod m = CtNewMethod.make(
                "public boolean incrementBankSize() {\n\n" +
                        "boolean worked = true;\n" +
                        "this.size += 1;\n" +
                        "java.sql.Connection dbcon = null;\n" +
                        "java.sql.PreparedStatement ps = null;\n" +
                        "try {\n" +
                        "  dbcon = com.wurmonline.server.DbConnector.getEconomyDbCon();\n" +
                        "  ps = dbcon.prepareStatement(\"UPDATE BANKS set SIZE=? where OWNER=?\");\n" +
                        "  ps.setInt(1, this.size);\n" +
                        "  ps.setLong(2, this.owner);\n" +
                        "  ps.executeUpdate();\n" +
                        "}\n" +
                        "catch (java.sql.SQLException sqx) {\n" +
                        "  sqx.printStackTrace();" +
                        "  com.wurmonline.server.banks.Bank.logger.log(java.util.logging.Level.WARNING, \"Failed to update bank size for owner \" + this.owner + \", SqlState: \" + sqx.getSQLState() + \", ErrorCode: \" + sqx.getErrorCode(), sqx);\n" +
                        "  worked = false;\n" +
                        "}\n" +
                        "finally {\n" +
                        "  com.wurmonline.server.utils.DbUtilities.closeDatabaseObjects(ps, null);\n" +
                        "  com.wurmonline.server.DbConnector.returnConnection(dbcon);\n" +
                        "}\n" +
                        "com.wurmonline.server.banks.BankSlot[] bs = new com.wurmonline.server.banks.BankSlot[this.size];\n" +
                        "for (int x = 0; x < this.slots.length; ++x) {\n" +
                        "  bs[x] = this.slots[x];\n" +
                        "}\n" +
                        "this.slots = bs;" +
                        "return worked;\n" +
                        "}", ctBank
            );
            ctBank.addMethod(m);
        } catch (NotFoundException | CannotCompileException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Failed to clear final modifier in Bank.java. ", e);
        }

        // change bank name
        try {
            CtClass ctPlayer = HookManager.getInstance().getClassPool().get(
                    "com.wurmonline.server.players.Player");

            String bankNameCode = "$proceed($1,\"Bank of Wurm\");";
            ctPlayer.getDeclaredMethod("openBank").instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("sendOpenInventoryWindow")) {
                        m.replace(bankNameCode);
                    }else if (m.getMethodName().equals("getCurrentVillage")) {
                        m.replace("$_ = \"\";");
                        //m.replace("$_ = {}");
                    }else if (m.getMethodName().equals("getName")){
                        m.replace("$_ = \"Bank of Wurm\";");
                        //m.replace("$_ = {}");
                    }
                }
            });

            // starting bank slots
            ctPlayer.getDeclaredMethod("startBank").instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("startBank")) {
                        m.replace(String.format("$_ = $proceed($1,%s,$3);",startingBankSlots));
                    }
                }
            });

        } catch (NotFoundException | CannotCompileException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Failed to instrument Player. ", e);
        }

        // allow players to use any token
        try {
            CtClass ctVTB = HookManager.getInstance().getClassPool().get(
                    "com.wurmonline.server.behaviours.VillageTokenBehaviour");

            // set the bank villageId to the current villageId
            ctVTB.getDeclaredMethod("action",  new CtClass[]{
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Action"),
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                    HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                    CtClass.shortType,
                    CtClass.floatType
            }).instrument(new ExprEditor() {
                public void edit(FieldAccess f) throws CannotCompileException {
                    if (f.getFieldName().equals("currentVillage") && f.isReader())
                        f.replace("$_ = villageId;");
                }
            });
        } catch (NotFoundException | CannotCompileException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Failed to update action to permit bank access at any token. ", e);
        }

        if (disableExtraFoodDamage){
            try {
                CtClass ctBank = HookManager.getInstance().getClassPool().get(
                        "com.wurmonline.server.banks.Bank");

                // set the bank villageId to the current villageId
                ctBank.getDeclaredMethod("pollItems").instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("isFood")) {
                            m.replace("$_ = false;");
                        }
                    }
                });
            } catch (NotFoundException | CannotCompileException e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, "Failed to update bank.pollItems to eliminate extra food damage. ", e);
            }

            try {
                CtClass ctCom = HookManager.getInstance().getClassPool().get(
                        "com.wurmonline.server.creatures.Communicator");

                // set the bank villageId to the current villageId
                ctCom.getDeclaredMethod("reallyHandle_CMD_MOVE_INVENTORY").instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("isFood") && m.getClassName().equals("com.wurmonline.server.items.Item")) {
                            m.replace("$_ = false;");
                        }
                    }
                });
            } catch (NotFoundException | CannotCompileException e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, "Failed to update bank.pollItems to eliminate extra food damage. ", e);
            }
        }

        ModActions.init();
    }

    @Override
    public String getVersion(){
        return "1.0";
    }

    @Override
    public void onItemTemplatesCreated() {
        try{
            BankSlotItem.registerBankSlot();
        } catch(IOException e){
            e.printStackTrace();
            logger.log(Level.SEVERE, "Failed to register BankSlotItem. ", e);
        }
    }

    @Override
    public void onServerStarted() {
        ModActions.registerBehaviourProvider(new BankSlotBehaviour());
    }
}
