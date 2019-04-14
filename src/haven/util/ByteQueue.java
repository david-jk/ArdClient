

package haven.util;

//Implementation eines circular buffers
//die Schreiboperationen fügen Bytes hinten an, die Leseoperationen nehmen sie vorne weg

public class ByteQueue
{
  private byte[] buf;
  private int used;  //Anzahl Bytes, die von buf tatsächlich benutzt werden

  public ByteQueue() {resize(1024);}
  public ByteQueue(byte[] b) {write(b);}
  public ByteQueue(byte[] b,int off,int len) {write(b,off,len);}

  public boolean write(byte[] b)
  {
    if (b==null)return false;
    reserve(b.length+used);
    for (int i=0,l=b.length;i<l;i++)buf[used++]=b[i];
    return true;
  }

  public boolean write(byte[] b,int off,int len)
  {
    if (len<1 || off<0 || b==null || off+len>b.length)return false;
    reserve(len+used);
    for (int i=0;i<len;i++)buf[used++]=b[off+i];
    return true;
  }

  public boolean write(int b)
  {
    reserve(used+1);
    buf[used++]=(byte)b;
    return true;
  }

  public boolean writeNullBytes(int len)
  {
    if (len<0)return false;
    reserve(len+used);
    for (int i=0;i<len;i++)buf[used++]=0;
    return true;
  }

  public boolean read(byte[] b,int off,int len)
  {
    if (len<1 || off<0 || b==null || off+len>b.length || len>used)return false;
    for (int i=0;i<len;i++)b[off+i]=buf[i];
    for (int i=0;i<used-len;i++)buf[i]=buf[len+i];
    used-=len;
    return true;
  }

  public boolean read(byte[] b,int len)
  {
    return read(b,0,len);
  }

  public byte[] read(int len)
  {
    if (len>used || len<1)return null;
    byte[] b=new byte[len];
    for (int i=0;i<len;i++)b[i]=buf[i];
    for (int i=0;i<used-len;i++)buf[i]=buf[len+i];
    used-=len;
    return b;
  }

  //normale Konvertierung ergibt negative Zahlen für byte-Werte>127, dies ist
  //nicht erwünscht und wird von dieser Funktion ins Reine gebracht.
  private int byteToInt(byte b)
  {
    int i=b;
    if (i<0)return 256+i;
    return i;
  }

  //Zugriffsfunktionen für einzelne Bytes inmitten des Buffers
  public int get(int i) throws IndexOutOfBoundsException {if (i<0 || i>=used)throw new IndexOutOfBoundsException();return byteToInt(buf[i]);}
  public void set(int i,int b) throws IndexOutOfBoundsException {if (i<0 || i>=used)throw new IndexOutOfBoundsException();buf[i]=(byte)b;}

  public int getCapacity() {if (buf==null)return 0;return buf.length;}
  public int getSize() {return used;}
  public boolean setSize(int ns)
  {
    if (ns<0)return false;
    if (ns>used)writeNullBytes(ns-used);
    else discardNewestBytes(used-ns);
    return true;
  }
  public boolean isEmpty() {return used==0;}

  private void doResize(int newCapacity)
  {
    if (newCapacity<=0 || (buf!=null && (newCapacity<used || buf.length==newCapacity)))return;
    byte[] nbuf=new byte[newCapacity];
    if (buf!=null)for (int i=buf.length-1;i>=0;i--)nbuf[i]=buf[i];
    buf=nbuf;
  }

  private void resize(int newCapacity)
  {
    int c=1024;
    while (c<newCapacity)c*=2;
    doResize(c);
  }

  private void reserve(int neededCapacity)
  {
    if (getCapacity()<neededCapacity)resize(neededCapacity);
  }

  public void clear()
  {
    used=0;
    buf=null;
    resize(1024);
  }

  //entfernt die n neuesten Bytes
  public boolean discardNewestBytes(int n)
  {
    if (n>used || n<1)return false;
    used-=n;
    return true;
  }

  //entfernt die n ältesten Bytes
  public boolean discard(int n)
  {
    if (n>used || n<1)return false;
    for (int i=0;i<used-n;i++)buf[i]=buf[n+i];
    used-=n;
    return true;
  }

  //ist nur gültig, solange keine weiteren Lese- und Schreibaktionen durchgeführt werden
  public byte[] getBuffer() {return buf;}
}
