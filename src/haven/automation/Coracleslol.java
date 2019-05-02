package haven.automation;


import haven.*;
import haven.purus.pbot.PBotAPI;
import haven.purus.pbot.PBotUtils;

import java.awt.*;
import java.util.List;

public class Coracleslol implements Runnable {
    public GameUI gui;
    public boolean havecoracle;
    public WItem coracle;
    public Gob coraclegob;
    public Boolean invcoracle = false, equipcoracle = false;

    public Coracleslol(GameUI gui) {
        this.gui = gui;
    }

    public void run() {
        try {
            if (gui.maininv.getItemPartial("Coracle") != null) {
                coracle = gui.maininv.getItemPartial("Coracle");
                invcoracle = true;
                equipcoracle = false;
            }
            else if (gui.getequipory().quickslots[11] != null) {
                if (gui.getequipory().quickslots[11].item.getname().contains("Coracle")) {
                    coracle = gui.getequipory().quickslots[11];
                    equipcoracle = true;
                    invcoracle = false;
                }
                else
                    havecoracle = false;
            } else havecoracle = false;

            if (coracle != null)
                havecoracle = true;
            if (!havecoracle) {
                    if(invcoracle)
                    while (PBotUtils.findObjectByNames(10, "gfx/terobjs/vehicle/coracle") == null)
                        PBotUtils.sleep(10);

                    coraclegob = PBotUtils.findObjectByNames(10, "gfx/terobjs/vehicle/coracle");

                    if (coraclegob == null) {
                        PBotUtils.sysMsg("Coracle not found.", Color.white);
                        return;
                    } else {
                        FlowerMenu.setNextSelection("Pick up");
                        PBotUtils.doClick(coraclegob, 3, 1);
                        PBotUtils.sleep(250);
                        List<Coord> slots = PBotUtils.getFreeInvSlots(PBotAPI.gui.maininv);
                        for (Coord i : slots) {
                            PBotUtils.dropItemToInventory(i, PBotAPI.gui.maininv);
                            PBotUtils.sleep(10);
                        }
                    }
            } else {
                    coracle.item.wdgmsg("drop", Coord.z);
                  //  PBotAPI.gui.map.wdgmsg("gk", 27); //sends escape key to mapview to stop movement so you dont walk away from the coracle
                    PBotAPI.gui.ui.root.wdgmsg("gk",27);
                    if(invcoracle) {
                        while (gui.maininv.getItemPartial("Coracle") != null)
                            PBotUtils.sleep(10);
                    }else if(equipcoracle) {
                        while (gui.getequipory().quickslots[11] != null)
                            PBotUtils.sleep(10);
                    }else{
                        PBotUtils.sysMsg("Somehow I don't know if the coracle came from inv or equipory, breaking.",Color.white);
                        return;
                    }
                    while (PBotUtils.findObjectByNames(10, "gfx/terobjs/vehicle/coracle") == null)
                        PBotUtils.sleep(10);
                    Gob coraclegob = PBotUtils.findObjectByNames(10, "gfx/terobjs/vehicle/coracle");
                    if (coraclegob == null) {
                        PBotUtils.sysMsg("Coracle not found, breaking.", Color.white);
                        return;
                    } else {//Into the blue yonder!
                        FlowerMenu.setNextSelection("Into the blue yonder!");
                        PBotUtils.doClick(coraclegob, 3, 1);
                        while (gui.ui.root.findchild(FlowerMenu.class) == null) {
                        }
                    }
                }
            }catch (NullPointerException | Resource.Loading qqq) {
            PBotUtils.sysMsg("Prevented crash, something went wrong dropping and mounting coracle.", Color.white);
        }
        }
    }



