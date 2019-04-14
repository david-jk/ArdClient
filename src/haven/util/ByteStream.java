
package haven.util;

import java.io.FileOutputStream;

//Streamklasse, die einfaches Schreiben und Lesen von Zahlen mit
//variabler Bytelänge (1-4 bytes) und Strings (max. Länge: 2^16-1 bytes) ermöglicht.
//Verwendet ByteQueue zur Speicherung der Daten.

public class ByteStream
{
  private ByteQueue data=new ByteQueue();
  private int pos;

  public ByteStream() {}
  public ByteStream(byte[] b) {data.write(b);}
  public ByteStream(byte[] b,int off,int len) {data.write(b,off,len);}

  public int getPosition() {return pos;}
  public int setPosition(int np)
  {
    if (np<0)np=0;
    if (np>getSize())np=getSize();
    pos=np;
    return pos;
  }
  public int getSize() {return data.getSize();}
  public int setSize(int newSize) {data.setSize(newSize);return getSize();}
  
  public int readBytes(int n)
  {
    int val=0;
    while (--n>=0)val|=readNextByte()<<(8*n);
    return val;
  }

  public void writeBytes(int val,int n)
  {
    while (--n>=0)writeByte((val&(255<<(n*8)))>>(n*8));      
  }

  private StringBuilder ssb=new StringBuilder();
  public String readString()
  {
    int strlen=readBytes(2);
    ssb.setLength(0);
    for (int i=0;i<strlen;i++)ssb.append((char)readNextByte());
    return ssb.toString();
  }

  public byte[] readBlob()
  {
    int len=readBytes(4);
    byte[] buf=new byte[len];
    for (int i=0;i<len;i++)buf[i]=(byte)readNextByte();
    return buf;
  }

  public void writeString(String str)
  {
    int strlen=str.length();
    writeBytes(strlen,2);
    for (int i=0;i<strlen;i++)writeByte(str.charAt(i));
  }

  public void writeBlob(byte[] blob)
  {
    int length=blob.length;
    writeBytes(length,4);
    for (int i=0;i<length;i++)writeByte(blob[i]);
  }

  private int readNextByte()
  {
    if (pos>=data.getSize())return 0;
    return data.get(pos++);
  }

  private void writeByte(int b)
  {
    if (pos>=data.getSize())data.write(b);
    else data.set(pos,b);
    pos++;
  }

  public void saveToFile(String fileName)
  {
    try
    {
      FileOutputStream fs=new FileOutputStream(fileName,false);
      setPosition(0);
      for (int i=0;i<getSize();i++)fs.write(readNextByte());
      fs.close();
    }
    catch(Exception ex) {}
  }

  //ist nur solange gültig, wie keine weiteren Lese- und Schreibaktionen durchgeführt werden
  public byte[] getBuffer() {return data.getBuffer();}
}
