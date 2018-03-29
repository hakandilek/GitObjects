import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

class GitInspector {

    final File root; //git repository
    final Repository repository;
    final org.eclipse.jgit.api.Git git;

    final ProcessBuilder PB;
    final static String LINE = "==============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public GitInspector() { this(new File(".")); }
    public GitInspector(File f) {
        root = f.isDirectory()? f.getAbsoluteFile(): f.getParentFile();
        PB = new ProcessBuilder(); PB.directory(root);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try {
			repository = builder.setGitDir(new File(root, ".git")).readEnvironment().findGitDir().build();
			git = new org.eclipse.jgit.api.Git(repository);
		} catch (IOException e) {
            throw new RuntimeException(root+": not a Git repository");
		}
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
    public RevCommit[] displayAllCommits() {
        ObjectId m = head(); System.out.println(m.name());
        List<RevCommit> L = new ArrayList<>();
        try {
			Iterable<RevCommit> commits = git.log().add(m).call();
			commits.forEach((c) -> {
    			displayCommit(c); L.add(c);
    		});
		} catch (GitAPIException | IOException e) {
			System.err.println("cannot iterate commits");
		}
        return L.toArray(new RevCommit[0]);
    }
    private void displayCommit(RevCommit c) {
    	PersonIdent id = c.getAuthorIdent();
    	Date time = id.getWhen();
        String name = c.getShortMessage();
		System.out.println(FORM.format(time)+"  "+name);
		RevTree tree = c.getTree();
        if (tree != null) {
        	System.out.print(Constants.typeString(tree.getType()) + " "); //no LF 
        	System.out.print(tree.getName().substring(0, 6)+"  ");
            System.out.print("? items ***  "); //TODO: item count
		}
        RevCommit[] parents = c.getParents();
        if (parents == null || parents.length == 0) System.out.println();
        else {
			String ps = Arrays.stream(parents).map(p -> p.getName().substring(0, 6)).collect(Collectors.joining(" "));
            System.out.println("parent " + ps);
        }
        System.out.println(LINE+LINE);
	}
	public void printData(String h) {
        for (byte b : getData(h)) System.out.print((char)b);
        System.out.println();
    }
    public byte[] getData(String h) { //4 digits may suffice
        String[] CATF = {"git", "cat-file", "-p", h};
        return exec(CATF);
    }
    public ObjectId head() {
    	try {
			ObjectId head = repository.resolve(Constants.HEAD);
			return head;
		} catch (RevisionSyntaxException | IOException e) {
			System.err.println("no HEAD found");
			return null;
		}
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
              new Git.Blob(hash, name, gt, size));
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
    
    public static void saveToFile(byte[] b, String f) throws IOException {
        OutputStream out = new FileOutputStream(f);
        out.write(b); out.close();
    }
    public static byte[] toArray(InputStream in) throws IOException {
        int n = in.available();
        if (n == 0) return new byte[0];
        byte[] buf = new byte[n];
        n = in.read(buf);
        if (n == buf.length) return buf;
        else return Arrays.copyOf(buf, n);
    }
    public static void main(String[] args) {
        new GitInspector().displayAllCommits();
    }
}

