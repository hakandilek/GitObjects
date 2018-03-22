import java.io.*;
import java.text.SimpleDateFormat;

class GitInspector {

    final File root; //git repository
    public GitInspector() {
        root = new File(".").getAbsoluteFile();
        File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
            throw new RuntimeException(root+": not a Git repository");
    }
    String commitName(String[] a) {
        for (int i=0; i<a.length; i++) 
            if (a[i].equals("")) return a[i+1];
        return null;
    }
    String findString(String str, String[] a) {
        for (String s : a) 
            if (s.startsWith(str)) return s;
        return null;
    }
    public String reportCommit(String h) { //returns parent
        String data = getData(h);
        //System.out.println(data);
        String[] a = data.split("\n");
        String name = commitName(a);
        String author = findString("author", a);
        if (author != null) {
            int k = author.length();
            String timeStr = author.substring(k-16, k-6);
            long time = 1000*Long.parseLong(timeStr); //msec
            System.out.println(FORM.format(time)+"  "+name); 
        }
        String tree = findString("tree", a);
        if (tree != null) {
            System.out.print(tree.substring(0, 11)+"  "); //no LF
            String[] t = getData(tree.substring(5)).split("\n");
            System.out.print(t.length+" items ***  ");
        }
        String parent = findString("parent", a);
        if (parent == null) System.out.println();
        else System.out.println(parent.substring(0, 13));
        System.out.println(LINE+LINE);
        return (parent == null? null : parent.substring(7));
    }
    public void reportTree(String h) {
        System.out.println(getData(h));
    }
    public void reportBlob(String h) {
        String data = getData(h);
        int n = data.length();
        System.out.println(n<200? data : n+" bytes");
    }
    public void allCommits() {
        String m = master(); System.out.println(m);
        while (m != null) m = reportCommit(m);
    }
    public String getData(String h) { //4 digits may suffice
        String[] CATF = {"git", "cat-file", "-p", h} ;
        return exec(CATF);
    }
    public String master() {
        String[] HEAD = {"git", "rev-parse", "HEAD"};
        return exec(HEAD).substring(0, 40);  //skip LF
    }
    
    final static String LINE = "==============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    final static ProcessBuilder PB = new ProcessBuilder();
    
    public static String exec(String[] a) {
        try { 
            //Process p = Runtime.getRuntime().exec(a);
            PB.command(a); Process p = PB.start();
            InputStream in  = p.getInputStream();
            InputStream err = p.getErrorStream();
            int i = 0;
            while (in.available()==0 && err.available()==0 && i<100) {
                Thread.sleep(5); i++;  //wait at most 500 msec
            }
            //System.out.println((2*n)+" msec waiting");
            int n = in.available();
            if (n == 0) return null;
            byte[] buf = new byte[in.available()];
            n = in.read(buf);
            return new String(buf, 0, n);
        }
        catch (Exception x) {
            System.out.println(x); return null;
        }
    }
    public static void main(String[] args) {
        new GitInspector().allCommits();
    }
}

