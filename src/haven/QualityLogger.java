package haven;

import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class QualityLogger {
    private static PrintWriter pw;

    private static boolean threadStarted=false;

    static class PendingItemsThread extends Thread {
        public void run() {
            setName("Pending items thread");
            for(;;) {
                tick();
                try {Thread.sleep(20);}catch(Exception ex) {}
            }
        }
    }

    public static void logSpriteCreation(Gob gob,String resName,String className) {
        boolean withoutPlayerPos=resName.contains("fishjump") || resName.equals("sfx/hammer") ||
                                  resName.equals("sfx/breakwood") || resName.equals("sfx/creak");

        log("SC "+gob.id+" "+resName+" "+className+" "+gob.rc.x+","+gob.rc.y);
        if (!withoutPlayerPos)logPlayerPosition();
    }

    private static void logPlayerPosition() {
        if (MapView.mv==null)return;
        Gob p=MapView.mv.player();
        if (p!=null) {
            log("PP "+p.id+" "+p.rc.x+" "+p.rc.y);
            lastPlayerPosLogged=System.currentTimeMillis();
        }
    }

    public static void logNewItem(GItem item) {
        item.tag=System.currentTimeMillis();
        String attr=itemAttrString(item);
        if (attr!=null) {
            log("NI "+attr);
            logPlayerPosition();
        }
        else {
            synchronized(QualityLogger.class) {
                pendingItems.add(item);
                startPendingThread();
            }
        }
    }

    public static void logItemProgress(GItem item)
    {
        String attr=itemAttrString(item);
        if (attr!=null) {
            log("IP "+item.meter+" "+attr);
        }
    }

    public static void logItemDestruction(GItem item) {
        String attr=itemAttrString(item);
        if (attr!=null) {
            log("DI "+attr);
            logPlayerPosition();
        }
    }

    public static void logObjectRemoval(Gob gob) {
        String resName="";
        try {
            resName=gob.getres().name;
        } catch(Exception ex) {
        }

        if (gob.id>=0) {
            if (System.currentTimeMillis()-lastPlayerPosLogged>=500)logPlayerPosition();
            log("OD "+gob.id+" "+gob.rc.x+","+gob.rc.y+" "+resName);
        }
    }

    public static void logMapTileSave(MCache map, MCache.Grid g,Coord origin,String session) {
        Coord rel=g.gc.sub(origin);
        log("MS "+rel.x+","+rel.y+" "+origin.x+","+origin.y+" "+session+" "+g.id);
    }

    public static void logLogin(String playerName) {
        log("LG \""+playerName+"\"");
        logPlayerPosition();
    }

    public static void logPlayerAttribute(String attr,int base,int total) {
        log("AT \""+getPlayerName()+"\" "+attr+" "+base+"+"+(total-base));
    }

    public static void logResMapping(int resId,String resName,int resVersion) {
        log("RS "+resId+" "+resName+" "+resVersion);
    }

    public static synchronized void logGobCreation(Gob gob) {
        pendingObjects.add(gob);
        gob.tag=System.currentTimeMillis();
        startPendingThread();
    }

    public static void logCursor(Widget w,String resName,int resVer) {
        if (resName!=null)log("CR "+w.id+" "+w.getClass().getSimpleName()+" "+resName+" "+resVer);
        else log("CR "+w.id+" "+w.getClass().getSimpleName()+" null");
    }

    public static void logUiMessage(String msg,boolean error) {
        log("UI "+(error? "err" : "msg")+" "+msg);
    }

    public static void logSoundEffect(Indir<Resource> res,double volume,double speed) {
        log("SF "+res.toString()+" "+volume+" "+speed);
    }

    public static void tick() {
        if (MapView.mv==null || MapView.mv.glob.oc==null)return;

        synchronized(MapView.mv.glob.oc) {
            synchronized(QualityLogger.class) {
                if (pendingItems.size()>0) {
                    handlePendingItems();
                }

                if (pendingObjects.size()>0) {
                    handlePendingObjects();
                }
            }
        }
    }

    private static String itemInfoAttrString(List<ItemInfo> infos) {
        double quality=0;
        String name="";
        String contents=null;
        try {
            for (ItemInfo info : infos) {
                if (info.getClass().getSimpleName().equals("Quality")) {
                    try {
                        double val = (Double) info.getClass().getField("q").get(info);
                        quality=val;
                    } catch (Exception ex) {
                    }
                }

                if (info.getClass().getSimpleName().equals("Name")) {
                    ItemInfo.Name n=(ItemInfo.Name)info;
                    name=n.str.text;
                }

                if (info.getClass().getSimpleName().equals("Contents")) {
                    ItemInfo.Contents c=(ItemInfo.Contents)info;
                    if (c.sub!=null)contents=itemInfoAttrString(c.sub);
                }
            }

            if (name.equals(""))return null;

            String attrStr="\""+name+"\" "+quality;
            if (contents!=null)attrStr=attrStr+" contents=("+contents+")";
            return attrStr;
        } catch (Exception ex) {
        }

        return null;
    }


    private static String itemAttrString(GItem item) {
        try {
            String attrStr=itemInfoAttrString(item.info());
            if (attrStr!=null)return item.tag+" "+attrStr;
        } catch (Exception ex) {
        }
        return null;
    }

    private synchronized static void log(String s) {
        if (pw==null) {
            try {
                (new File("qlog")).mkdir();
                pw = new PrintWriter(new FileWriter("qlog/qaction"+System.currentTimeMillis()+".log", true));
                pw.println(System.currentTimeMillis()+" VR 10");
            } catch(Exception ex) {
                return;
            }
        }

        pw.println(System.currentTimeMillis()+" "+s);
        pw.flush();
    }

    private synchronized static void handlePendingItems() {
        for (Iterator<GItem> it = pendingItems.iterator();it.hasNext();) {
            GItem item = it.next();

            String attr=itemAttrString(item);

            if (attr!=null) {
                it.remove();
                log("NI "+attr);
                logPlayerPosition();
            }
        }
    }

    private synchronized static void handlePendingObjects() {
        for (Iterator<Gob> it = pendingObjects.iterator();it.hasNext();) {
            Gob gob = it.next();
            long age=System.currentTimeMillis()-gob.tag;
            if (age>10000) {
                it.remove();
                continue;
            }
            Resource res=null;
            try {
                res=gob.getres();
            } catch (Exception ex) {
                continue;
            }
            String resName="";
            if (res!=null)resName=res.name+":v"+res.ver;
            if (gob.rc.x!=0 || gob.rc.y!=0) {
                it.remove();
                if (gob.id>=0) {
                    if (System.currentTimeMillis()-lastPlayerPosLogged>=500)logPlayerPosition();
                    log("GO "+gob.tag+" "+gob.id+" "+gob.rc.x+","+gob.rc.y+" "+gob.frame+" "+resName+" "+(int)(gob.a/3.141592653589*180.0+0.5)+" "+getDrawableAttr(gob)+" "+getGobHealth(gob)+" "+getResAttr(gob));
                }
            }
        }
    }

    private static String getPlayerName() {
        return MapView.mv.glob.sess.username;
    }

    private static synchronized void startPendingThread() {
        if (!threadStarted) {
            threadStarted=true;
            (new PendingItemsThread()).start();
        }
    }

    private static int getResStage(Gob gob) {
        GAttrib rd=gob.getattr(ResDrawable.class);
        if (rd!=null) {
            try {
                return ((ResDrawable)rd).sdt.peekrbuf(0);
            } catch (Exception ex) {
            }
        }
        return -1;
    }

    private static String getResAttr(Gob gob) {
        try {
            String str="";
            for (Iterator<Gob.ResAttr.Cell<?>> i = gob.rdata.iterator(); i.hasNext(); ) {
                Gob.ResAttr.Cell<?> rd = i.next();
                if (!str.equals(""))str+=",";
                str+=rd.resid+":";
                if (rd.odat==null)str+="null";
                else str+=rd.odat.toHexString();
                try {
                    if (rd.resid.toString().contains("/vmat")) {
                       MessageBuf data=rd.odat.clone();
                       data.rewind();
                       int size=data.rem();
                       for (int o=0;o<size;o+=3) {
                           int resid=data.uint16();
                           str+=":"+gob.glob.sess.getres(resid);
                           int d=data.uint8();
                       }
                    }
                }
                catch (Exception ex) {
                }
            }
            if (str.equals(""))str="-";
            return str;
        }
        catch (Exception ex) {
            return "error";
        }
    }

    private static String getDrawableAttr(Gob gob) {
        GAttrib rda=gob.getattr(ResDrawable.class);
        if (rda!=null) {
            try {
                ResDrawable rd=(ResDrawable)rda;
                String str=rd.sdt.toHexString();
                if (str.equals(""))str="-";
                return str;
            } catch (Exception ex) {
            }
        }
        return "-";
    }



    private static int getGobHealth(Gob gob) {
        GobHealth h=gob.getattr(GobHealth.class);
        if (h!=null)return h.hp;
        return -1;
    }

    private static ArrayList<GItem> pendingItems=new ArrayList<GItem>();
    private static ArrayList<Gob> pendingObjects=new ArrayList<Gob>();
    private static long lastPlayerPosLogged=0;
}
