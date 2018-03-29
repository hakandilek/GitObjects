import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.security.MessageDigest;

class Git {

    static int count, pass; 
    final static int M = 7; //hash length -- abbrev default to 7 chars
    final static MessageDigest MD; 

      static {
        try { 
            MD = MessageDigest.getInstance("SHA-1");         
        } catch (java.security.NoSuchAlgorithmException x) {
            throw new RuntimeException(x);
        }
      }
    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }
    static String toHex(byte b) {
        if (b > 15) return Integer.toHexString(b);
        if (b < 0) return Integer.toHexString(b+256);
        return "0"+Integer.toHexString(b); //single digit
    }
    public static String toHex(byte[] buf) {
        String hash = "";
        for (int i=0; i<buf.length; i++) hash += toHex(buf[i]);
        return hash;
    }
    public static String toSHA(byte[] ba) {
        return toHex(MD.digest(ba));  //java.security.MessageDigest
    }
    public static String toSHA(String s) {
        return toSHA(s.getBytes()); 
    }
    public static void saveToFile(byte[] b, File f) {
        try {
            OutputStream out = new FileOutputStream(f);
            out.write(b); out.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    static class Commit {
       String hash, name, hTree; Tree data;
       long time; String parent;
       Commit(String h, String n, String x, long t, String p) { 
          hash = trim(h); name = n; hTree = x; time = t; parent = p; 
       }
       public boolean dataIsRead() { return data != null; }
       public String toString() { return hash+" "+name; }
    }
    static abstract class Entry implements TreeNode {
       String hash, name; Tree parent;
       Entry(String h, String n, Tree p) { 
           hash = trim(h); name = n; parent = p; 
       }
       public boolean getAllowsChildren() { return !isLeaf(); }
       public TreeNode getParent() { return parent; }
       public java.util.Enumeration children() { return null; }
       public abstract boolean dataIsRead();
       public abstract void verify();
       public abstract void saveTo(File dir);
    }
    static class Blob extends Entry {
       String size; byte[] data;
       Blob(String h, String n, Tree p, String s, byte[] d) { 
           super(h, n, p); size = s; data = d;
       }
       public boolean isLeaf() { return true; }
       public int getIndex(TreeNode node) { return -1; }
       public int getChildCount() { return 0; }
       public TreeNode getChildAt(int i) { return null; }
       public String toString() { return hash+" "+size+" - "+name; }
       public boolean dataIsRead() { return data != null; }
       public void verify() {
           byte[] b = data; count++;
           String t = "blob "+b.length;
           byte[] a = t.getBytes();
           byte[] ab = new byte[a.length+1+b.length];
           System.arraycopy(a, 0 ,ab, 0, a.length);  //char 0
           System.arraycopy(b, 0 ,ab, a.length+1, b.length);
           String s = toSHA(ab);
           boolean OK = s.startsWith(hash);
           if (OK) pass++;
           System.out.println(hash+size+" "+OK+" "+name);
       }
       public void saveTo(File d) {
           byte[] b = data; count++;
           System.out.println(hash+size+" = "+b.length+" "+name);
           saveToFile(b, new File(d, name));
       }
    }
    static class Tree extends Entry {
       List<Entry> list = new ArrayList<>();
       Tree(String h, String n, Tree p) { super(h, n, p); }
       public void add(Entry e) { list.add(e); } 
       public boolean isLeaf() { return false; }
       public int getIndex(TreeNode node) { return -1; }
       public int getChildCount() { return list.size(); }
       public TreeNode getChildAt(int i) { return list.get(i); }
       public String toString() { return hash+" "+name+": "+list.size(); }
       public boolean dataIsRead() { return list != null; }
       public void verify() {
           for (Entry e : list) e.verify();
       }
       public void saveTo(File d) {
           File f = new File(d, name);
           System.out.println(hash+"  "+f);
           if (f.exists()) 
             throw new RuntimeException("cannot overwrite "+f);
           if (!f.mkdir()) 
             throw new RuntimeException("cannot mkdir "+f);
           for (Entry e : list) e.saveTo(f);
       }
    }
}
