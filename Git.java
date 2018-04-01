import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

class Git {

    final File root; //git repository
    final ProcessBuilder PB;
    int count, pass; 
    
    final static int M = 7; //hash length -- abbrev default to 7 chars
    final static MessageDigest MD; 
    final static String LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public Git() { this(new File(".")); }
    public Git(File f) {
        root = f.isDirectory()? f.getAbsoluteFile(): f.getParentFile();
        PB = new ProcessBuilder(); PB.directory(root);
        File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
            throw new RuntimeException(root+": not a Git repository");
    }
    String commitName(String[] a) {
        for (int i=0; i<a.length; i++) 
            if (a[i].length() == 0) return a[i+1];
        return null;
    }
    String findString(String str, String[] a) {
        for (String s : a) 
            if (s.startsWith(str)) return s;
        return null;
    }
    public Commit getCommit(String h) {
        String data = new String(getData(h));
        String[] a = data.split("\n");
        String name = commitName(a);
        System.out.println(h+"  "+name);
        String author = findString("author", a);
        long time = 0;
        if (author != null) {
            int k = author.length();
            String tStr = author.substring(k-16, k-6);
            while (tStr.startsWith(" ")) tStr = tStr.substring(1);
            time = 1000*Long.parseLong(tStr); //msec
            System.out.println(FORM.format(time)+"  "+name);
        }
        String tree = findString("tree", a);
        if (tree != null) {
            System.out.print(tree.substring(0, 11)+"  "); //no LF
            tree = tree.substring(5);
            String[] t = new String(getData(tree)).split("\n");
            System.out.print(t.length+" items ***  ");
            tree = trim(tree);
        }
        String parent = findString("parent", a);
        if (parent == null) System.out.println();
        else {
            System.out.println(parent.substring(0, 14));
            parent = parent.substring(7, 14);
        }
        System.out.println(LINE+LINE);
        return new Commit(h, name, tree, time, parent);
    }
    public Commit[] getAllCommits() {
        String m = head(); System.out.println(m);
        List<Commit> L = new ArrayList<>();
        Commit c = getCommit(m); L.add(c);
        while (c.hParent != null) {
            Commit p = getCommit(c.hParent);
            c.parent = p; L.add(p); c = p;
        }
        return L.toArray(new Commit[0]);
    }
    public void saveAllBlobs(Commit c) {
        c.saveTo(root);
    }
    public void printData(String h) {
        for (byte b : getData(h)) System.out.print((char)b);
        System.out.println();
    }
    public byte[] getData(String h) { //4 digits may suffice
        String[] CATF = {"git", "cat-file", "-p", h};
        return exec(CATF);
    }
    public String head() {
        String[] HEAD = {"git", "rev-parse", "HEAD"};
        return new String(exec(HEAD), 0, 40);  //skip LF
    }
    public Tree makeTree(String h) {  //top level has no parent
        return makeTree(h, "root", null); 
    }
    Tree makeTree(String h, String n, Tree p) {
        String[] TREE = {"git", "ls-tree", "-l", "--abbrev", h};
        String data = new String(exec(TREE));
        String[] sa = data.split("\n"); 
        Tree gt = new Tree(h, n, p);
        for (String s : sa) { 
            int k = s.indexOf(32);   //find space
            int i = s.indexOf(32, k+1); //second space
            int j = s.indexOf(9, i+1);  //find TAB
            String hash = s.substring(i+1, i+1+M);
            String size = s.substring(j-M, j);
            String name = s.substring(j+1);
            System.out.println(hash+" "+size+" "+name); //s.substring(k+1));
            gt.add(  s.charAt(j-1) == '-'?
              makeTree(hash, name, gt) : 
              new Blob(hash, name, gt, size, getData(hash)));
        }
        return gt;
    }
    public void execute(String... a) {
        System.out.println(new String(exec(a)));
    }
    byte[] exec(String... a) {
        byte[] out, err;
        try { 
            //Process p = Runtime.getRuntime().exec(a);
            PB.command(a); Process p = PB.start();
            p.waitFor(2, TimeUnit.SECONDS);
            
            out = toArray(p.getInputStream());
            err = toArray(p.getErrorStream());         
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
        if (out.length > 0) return out; 
        throw new RuntimeException(new String(err));
    }

    abstract class Entry implements TreeNode {
       String hash, name; Entry parent;
       Entry(String h, String n, Entry p) { 
           hash = trim(h); name = n; parent = p; 
       }
       public boolean getAllowsChildren() { return !isLeaf(); }
       public TreeNode getParent() { return parent; }
       public java.util.Enumeration children() { return null; }
       public abstract void verify();
       public abstract void saveTo(File dir);
    }
    class Commit extends Entry {
       String hTree, hParent; Tree data; long time;
       Commit(String h, String n, String x, long t, String p) {
           super(h, n, null); hTree = x; time = t; hParent = p;
       }
       public boolean isLeaf() { return false; }
       public int getIndex(TreeNode node) { return -1; }
       public int getChildCount() { return 1; }
       public TreeNode getChildAt(int i) { return data; }
       public String toString() { return hash+" - "+name; }
       public void verify() {
           count = 0; pass = 0;
           if (data == null) data = makeTree(hTree);
           data.verify();
           System.out.println(count+" blobs, "+pass+" OK");
       }
       public void saveTo(File d) { 
           count = 0;
           if (data == null) data = makeTree(hTree);
           data.saveTo(d);
           System.out.println(count+" blobs written");
       }
    }
    class Blob extends Entry {
       String size; byte[] data;
       Blob(String h, String n, Tree p, String s, byte[] d) { 
           super(h, n, p); size = s; data = d;
       }
       public boolean isLeaf() { return true; }
       public int getIndex(TreeNode node) { return -1; }
       public int getChildCount() { return 0; }
       public TreeNode getChildAt(int i) { return null; }
       public String toString() { return hash+" "+size+" - "+name; }
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
    class Tree extends Entry {
       List<Entry> list = new ArrayList<>();
       Tree(String h, String n, Tree p) { super(h, n, p); }
       public void add(Entry e) { list.add(e); } 
       public boolean isLeaf() { return false; }
       public int getIndex(TreeNode node) { return -1; }
       public int getChildCount() { return list.size(); }
       public TreeNode getChildAt(int i) { return list.get(i); }
       public String toString() { return hash+" "+name+": "+list.size(); }
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
    public static byte[] toArray(InputStream in) {
        try {
            int n = in.available();
            if (n == 0) return new byte[0];
            byte[] buf = new byte[n];
            n = in.read(buf);
            if (n == buf.length) return buf;
            else return Arrays.copyOf(buf, n);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
    public static void main(String[] args) {
        Git G = new Git(); G.getAllCommits();
        //G.getCommit(G.head()).verify();
    }
}
