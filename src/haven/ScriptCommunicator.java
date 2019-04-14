
package haven;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import haven.util.*;



class ScriptCommunicator {
    public static final int CMSG_SEND_MSG=1,CMSG_SEND_SEQ_MSG=2,CMSG_PING=3,CMSG_PONG=4;
    public static final int SMSG_MSG_RECEIVED=1,SMSG_MSG_SENT=2,SMSG_PING=3,SMSG_PONG=4,SMSG_DUMP_START=5,SMSG_DUMP_FINISHED=6,SMSG_SCRIPT_MSG=7;
    public static ScriptCommunicator globalInstance;

    private int port=0;
    private Session session;

    public ScriptCommunicator() throws IOException {
        for (int p=9411;p<=9419;p++) {
            try {
                serverSocket=new ServerSocket(p);
                port=p;
                System.out.println("Listening on port "+port+".");
                new ServerThread().start();
                break;
            }
            catch(Exception ex) {}
        }
        if (port==0)throw new RuntimeException("No free port for ScriptCommunicator.");
        globalInstance=this;
    }

    public void setSession(Session session) {
        this.session=session;
    }

    class ClientThread extends Thread implements MessageConnection.MessageHandler {
         ClientThread(Socket s) {
             this.s=s;
             mc=new MessageConnection(s,this);
             firstLiveMessageIndex=events.size();
         }

         public void run() {
             try {
                 InputStream in=s.getInputStream();
                 DataOutputStream out=new DataOutputStream(s.getOutputStream());

                 while (true) {
                     if (session!=null && !mc.receive())return;
                     try {
                         sendPendingEvents();
                         Thread.sleep(1);
                     }
                     catch(InterruptedException ex) {}
                }
            }
            catch(Exception ex) {}
         }

         public void sendPendingEvents() {
             ArrayList<LoggedMessage> toSend=new ArrayList<LoggedMessage>();
             int firstIdxToSend=-1;
             synchronized(ScriptCommunicator.this) {
                 firstIdxToSend=messageIndex;
                 while (messageIndex<events.size()) {
                     toSend.add(events.get(messageIndex++));
                 }
             }

             if (firstIdxToSend==0)mc.sendMessage(SMSG_DUMP_START,new ByteStream());

             for (int i=0;i<toSend.size();i++) {
                 int evIdx=firstIdxToSend+i;
                 if (evIdx==firstLiveMessageIndex)mc.sendMessage(SMSG_DUMP_FINISHED,new ByteStream());
                 LoggedMessage event=toSend.get(i);
                 ByteStream s=new ByteStream();
                 s.writeBlob(event.data);
                 mc.sendMessage(event.incoming? SMSG_MSG_RECEIVED : SMSG_MSG_SENT,s);
             }

             if (firstIdxToSend+toSend.size()==firstLiveMessageIndex)mc.sendMessage(SMSG_DUMP_FINISHED,new ByteStream());

             ArrayList<PendingScriptMessage> smsgToSend=null;
             synchronized(pendingScriptMessages) {
                 if (pendingScriptMessages.size()>0) {
                     smsgToSend=(ArrayList<PendingScriptMessage>)pendingScriptMessages.clone();
                     pendingScriptMessages.clear();
                 }
             }

             for (int i=0;smsgToSend!=null && i<smsgToSend.size();i++) {
                 PendingScriptMessage smsg=smsgToSend.get(i);
                 Message msg=new MessageBuf();
                 msg.addstring(smsg.type);
                 msg.addlist(smsg.args);
                 mc.sendMessage(SMSG_SCRIPT_MSG,msg.getWriteBuffer());
             }
         }

         @Override
         public void handleMessage(int type,ByteStream msg) {
             if (type==CMSG_SEND_MSG) {
                 session.sendmsg(msg.readBlob(),SessionLogger.F_SENT_BY_SCRIPT);
             }
             else if (type==CMSG_SEND_SEQ_MSG) {
                 int stype=msg.readBytes(1);
                 byte[] data=msg.readBlob();
                 PMessage pmsg=new PMessage(stype);
                 pmsg.addbytes(data);
                 session.queuemsg(pmsg,SessionLogger.F_SENT_BY_SCRIPT);
             }
             else if (type==CMSG_PING)mc.sendMessage(SMSG_PONG,null,0);
         }

         private Socket s;
         private MessageConnection mc;
         private int messageIndex=0;
         private int firstLiveMessageIndex=0;
    }

    class ServerThread extends Thread {
         ServerThread() {
         }

         public void run() {
             try {
                 while (true) {
                     Socket s=serverSocket.accept();
                     new ClientThread(s).start();
                }
             }
             catch(Exception ex) {}
         }
    }

    class LoggedMessage {
        public LoggedMessage(byte[] data,boolean incoming) {
            this.data=data;
            this.incoming=incoming;
        }

        public byte[] data;
        boolean incoming=false;
    }

    class PendingScriptMessage {
        public PendingScriptMessage(String type,List args) {
            this.type=type;
            this.args=args;
        }

        String type;
        List args;
    }

    ArrayList<LoggedMessage> events=new ArrayList<LoggedMessage>();
    ArrayList<PendingScriptMessage> pendingScriptMessages=new ArrayList<PendingScriptMessage>();

    public void notifyMessageReceived(PMessage msg) {
        synchronized(this) {
            byte[] body=msg.toByteArray();
            byte[] data=new byte[body.length+1];
            data[0]=(byte)msg.type;
            for (int i=0;i<body.length;i++)data[i+1]=body[i];
            events.add(new LoggedMessage(data,true));
        }
    }

    public void notifyMessageSent(byte[] msg) {
        synchronized(this) {
            events.add(new LoggedMessage(msg,false));
        }
    }

    public void sendScriptMessage(String type,List args) {
        synchronized(pendingScriptMessages) {
            pendingScriptMessages.add(new PendingScriptMessage(type,args));
        }
    }

    ServerSocket serverSocket;
}
