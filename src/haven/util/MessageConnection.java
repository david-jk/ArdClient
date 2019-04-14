
package haven.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

//MessageConnection bietet ein Interface, um über das Netzwerk vollständige Nachrichten
//zu versenden und zu empfangen.
//Eine Nachricht besteht aus einem 3-Byte-Header und Nutzdaten. Der Header enthält
//Nachrichtentyp und Größe. Eine Nachricht wird erst verarbeitet, wenn sie vollständig
//empfangen wurde (was mithilfe der Größe im Header bestimmt wird).
//Die Verarbeitung erfolgt durch den Aufruf von handleMessage eines Objekts, das
//MessageHandler implementiert.

public class MessageConnection
{
  public interface MessageHandler
  {
    public abstract void handleMessage(int msgType,ByteStream msg);
  }
  private Socket socket;
  private InputStream is;
  private OutputStream os;
  private boolean connected=false;
  private ByteQueue iq;
  private MessageHandler handler;
  private static final int headerSize=5;

  public MessageConnection(MessageHandler mh) {handler=mh;}
  public MessageConnection(Socket s,MessageHandler mh)
  {
    socket=s;
    handler=mh;
    try
    {
      is=socket.getInputStream();
      os=socket.getOutputStream();
      connected=true;
      iq=new ByteQueue();
    }
    catch(Exception ex) {disconnect();}
  }

  public boolean isConnected() {return connected;}

  public int connectTo(String host,int port)
  {
    disconnect();
    try
    {
      socket=new Socket(host,port);
      is=socket.getInputStream();
      os=socket.getOutputStream();
    }
    catch(UnknownHostException ex) {disconnect();return 1;}
    catch(IOException ex) {disconnect();return 2;}

    connected=true;
    iq=new ByteQueue();

    return 0;
  }

  public boolean disconnect()
  {
    if (!connected)return false;
    try{is.close();os.close();socket.close();}catch(IOException ex) {}
    is=null; os=null;
    socket=null;
    iq=null;
    connected=false;
    return true;
  }

  //um GC für Leseoperationen <= 1 KiB zu entlasten
  private static byte[] recvbuf=new byte[1024];
  //gibt false zurück, wenn die Verbindung beendet wurde bzw. ein Fehler auftrat, ansonsten true  
  //liest Daten vom Socket und schreibt sie in den Buffer.
  public boolean receive()
  {
    if (!connected)return false;
    try
    {
      if (is.available()>0)
      {
        int av=is.available();
        byte[] b=av>recvbuf.length ? (new byte[av]) : recvbuf;
        is.read(b,0,av);
        iq.write(b,0,av);
      }
      else if (!socket.isConnected())
      {
        disconnect();
        return false;
      }
    }
    catch (IOException ex) {disconnect();return false;}
    handleMessages();
    if (!connected)return false;
    return true;
  }

  //überprüft, ob bereits eine vollständige Nachricht empfangen wurde und leitet sie
  //ggf, an handleMessage() weiter.
  private void handleMessages()
  {
    if (iq.getSize()<headerSize)return;
    int msgType=iq.get(0);
    int msgSize=(iq.get(1)<<24) | (iq.get(2)<<16) | (iq.get(3)<<8) | iq.get(4);
    if (msgSize<headerSize) //d.h. Gegenseite sendet Unsinn - jede Nachricht ist durch Header >=5 bytes
    {
      disconnect();
      return;
    }
    if (iq.getSize()<msgSize)return;
    byte[] b=iq.read(msgSize);
    handleMessage(msgType,b);
    handleMessages(); //erneuter Aufruf, da möglicherweise weitere unverarbeitet Nachrichten bereitstehen
  }

  //gibt false zurück, wenn die Verbindung beendet wurde bzw. ein Fehler auftrat, ansonsten true
  public boolean sendMessage(int msgType,byte[] b,int size)
  {
    if (!connected)return false;
    try
    {
      int msgSize=size+headerSize;
      os.write(msgType);
      os.write((msgSize&0xFF000000)>>24);
      os.write((msgSize&0x00FF0000)>>16);
      os.write((msgSize&0x0000FF00)>>8);
      os.write((msgSize&0x000000FF));
      if (size!=0)os.write(b,0,size);
      os.flush();
      if (!socket.isConnected())
      {
        disconnect();
        return false;
      }
    }
    catch (IOException ex) {disconnect();return false;}
    return true;
  }

  public boolean sendMessage(int msgType,ByteStream bq)
  {
    return sendMessage(msgType,bq.getBuffer(),bq.getSize());
  }

  public boolean sendMessage(int msgType,byte[] b) {
      return sendMessage(msgType,b,b.length);
  }

  private void handleMessage(int msgType,byte[] msg)
  {
    if (msg==null || msg.length<headerSize || handler==null)return;
    handler.handleMessage(msgType,new ByteStream(msg,headerSize,msg.length-headerSize));
  }
}
