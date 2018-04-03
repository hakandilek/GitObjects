import java.io.*;
import java.text.SimpleDateFormat;
import java.util.zip.InflaterInputStream;

class SimpleObjects {

    final File root; //git repository
    final File obj;  //git objects are in this folder

    final static String LINE = "==============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    
    public SimpleObjects() { this(new File(".")); }
    public SimpleObjects(File f) {
        root = f.isDirectory()? f.getAbsoluteFile(): f.getParentFile();
        obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
            throw new RuntimeException(root+": not a Git repository");
    }
    public String master() {
        File git = new File(root, ".git");
        File refs = new File(git, "refs");
        File heads = new File(refs, "heads");
        File master = new File(heads, "master");
        return fileContents(master).substring(0, 40); //skip LF
    }
    public void decode(String h) { //h has 40 chars
        File d = new File(obj, ""+h.charAt(0)+h.charAt(1));
        decode(new File(d, h.substring(2)));
    }
    public void decode(File f) {
        int num;  //number of bytes read
        byte[] buf = new byte[2000];
        String kind = "";
        try (InputStream in = new InflaterInputStream(new FileInputStream(f))) {
            char c;
            while ((c = (char) in.read()) > 0)
                kind += c;
            num = in.read(buf);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        String data = new String(buf, 0, num);
        System.out.println(kind+" bytes *** "+num); //data.length());
        if (kind.startsWith("commit")) reportCommit(data);
        else if (kind.startsWith("blob")) reportBlob(data);
        else { //tree data contains hash in binary
            int p = 0;
            while (p < num) { //for each entry
                while (buf[p] != 32) p++; //space
                int k = p+1;
                while (buf[p] != 0) p++; //char zero
                String name = new String(buf, k, p-k); 
                String hash = ""; p++;
                for (int i=0; i<20; i++) hash += toHex(buf[p+i]);
                System.out.println(hash+"  "+name); //20 bytes
                p += 20;
            }
        }

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
    void reportCommit(String data) {
        String[] a = data.split("\n");
        System.out.println(commitName(a));
        String author = findString("author", a);
        if (author != null) {
            int k = author.length();
            String timeStr = author.substring(k-16, k-6);
            long time = 1000*Long.parseLong(timeStr); //msec
            System.out.println(timeStr+"  "+FORM.format(time)); 
        }
        String tree = findString("tree", a);
        if (tree != null) {
            System.out.println(tree);  //.substring(0, 11));
            decode(tree.substring(5));
        }
        String parent = findString("parent", a);
        if (parent != null) {
            System.out.println(parent);  //.substring(0, 13));
            System.out.println(LINE+LINE);
            decode(parent.substring(7));
        }
    }
    void reportBlob(String data) { //do nothing
    }

    static String toHex(byte b) {
        if (b > 15) return Integer.toHexString(b);
        if (b < 0) return Integer.toHexString(b+256);
        return "0"+Integer.toHexString(b); //single digit
    }
    public static String fileContents(File f) {
        try {
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[in.available()];
                int n = in.read(buf);
                return new String(buf, 0, n);
            }
        } catch (IOException x) {
            return null;
        }
    }
    public static void main(String[] args) {
        //String d = "/home/maeyler/github/hackernoon/.git/objects/af";
        //String n = "2c47da3037fada6f53bb3e9f838160a636b5cb";
        //G.decode(new File(d, n));
        SimpleObjects G = new SimpleObjects();
        String m = G.master(); System.out.println(m);
        G.decode(m);
    }
}
