import java.util.*;
import javax.swing.tree.TreeNode;

class Git {

    final static int M = 7; //hash length -- abbrev default to 7 chars
    
    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }

    static class Commit {
       String hash, name, tree; 
       long time; String parent;
       Commit(String h, String n, String x, long t, String p) { 
          hash = trim(h); name = n; tree = x; time = t; parent = p; 
       }
       public String toString() { return hash+" "+name; }
    }
    static abstract class Entry implements TreeNode {
       String hash, name; Tree parent;
       Entry(String h, String n, Tree p) { 
           hash = trim(h); name = n; parent = p; 
       }
       public boolean getAllowsChildren() { return !isLeaf(); }
       public TreeNode getParent() { return parent; }
       public Enumeration<?> children() { return null; }
    }
    static class Blob extends Entry {
       String size;
       Blob(String h, String n, Tree p, String s) { 
           super(h, n, p); size = s; 
       }
       public boolean isLeaf() { return true; }
       public int getIndex(TreeNode node) { return -1; }
       public int getChildCount() { return 0; }
       public TreeNode getChildAt(int i) { return null; }
       public String toString() { return hash+" "+size+" - "+name; }
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
    }
}
