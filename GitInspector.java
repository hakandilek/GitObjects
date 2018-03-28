import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

class GitInspector {

    final File root; //git repository
    final ProcessBuilder PB;
    final static String LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public GitInspector() { this(new File(".")); }
    public GitInspector(File f) {
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
    public Git.Commit displayCommit(String h) {
        String data = new String(getData(h));
        //System.out.println(data);
        String[] a = data.split("\n");
        String name = commitName(a);
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
            tree = Git.trim(tree);
        }
        String parent = findString("parent", a);
        if (parent == null) System.out.println();
        else {
            System.out.println(parent.substring(0, 14));
            parent = parent.substring(7, 14);
        }
        System.out.println(LINE+LINE);
        return 
            new Git.Commit(h, name, tree, time, parent);
    }
    public Git.Commit[] displayAllCommits() {
        String m = head(); System.out.println(m);
        List<Git.Commit> L = new ArrayList<>();
        Git.Commit c = displayCommit(m); L.add(c);
        while (c.parent != null) L.add(c = displayCommit(c.parent));
        return L.toArray(new Git.Commit[0]);
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
    public Git.Tree displayTree(String h) {  //top level has no parent
        return displayTree(h, "root", null); 
    }
    Git.Tree displayTree(String h, String n, Git.Tree p) {
        String[] TREE = {"git", "ls-tree", "-l", "--abbrev", h};
        String data = new String(exec(TREE));
        String[] sa = data.split("\n"); 
        Git.Tree gt = new Git.Tree(h, n, p);
        for (String s : sa) { 
            int k = s.indexOf(32);   //find space
            int i = s.indexOf(32, k+1); //second space
            int j = s.indexOf(9, i+1);  //find TAB
            String hash = s.substring(i+1, i+1+Git.M);
            String size = s.substring(j-Git.M, j);
            String name = s.substring(j+1);
            System.out.println(hash+" "+size+" "+name); //s.substring(k+1));
            gt.add(  s.charAt(j-1) == '-'?
              displayTree(hash, name, gt) : 
              new Git.Blob(hash, name, gt, size, getData(hash)));
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
            p.waitFor();
            
            out = toArray(p.getInputStream());
            err = toArray(p.getErrorStream());         
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
        if (out.length > 0) return out; 
        throw new RuntimeException(new String(err));
    }
    public boolean verifyAllBlobs(Git.Commit c) {
        String h = c.hash; Git.count = 0; Git.pass = 0; 
        displayTree(c.tree).verify();
        System.out.println(Git.count+" blobs, "+Git.pass+" OK");
        return Git.count == Git.pass;
    }
    public void saveAllBlobs(Git.Commit c, String name) {
        saveAllBlobs(c, root);
    }
    public void saveAllBlobs(Git.Commit c, File f) {
        if (f.exists()) 
            throw new RuntimeException("cannot overwrite "+f);
        String h = c.hash; Git.count = 0;
        displayTree(c.tree).saveTo(f);
        System.out.println(Git.count+" blobs written");
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
        GitInspector G = new GitInspector();
        G.verifyAllBlobs(G.displayCommit(G.head()));
    }
}

