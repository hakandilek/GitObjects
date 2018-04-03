import java.io.File;
import java.io.IOException;
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
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

class GitInspector {

    final Repository repository;
    final org.eclipse.jgit.api.Git git;

    final static String LINE = "==============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public GitInspector() { this(new File(".")); }
    public GitInspector(File f) {
        File root = f.isDirectory()? f.getAbsoluteFile(): f.getParentFile();
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try {
			File gitDir = new File(root, ".git");
			repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build();
			git = new org.eclipse.jgit.api.Git(repository);
		} catch (IOException e) {
            throw new RuntimeException(root+": not a Git repository");
		}
    }

    public Git.Commit[] displayAllCommits() {
        ObjectId m = head(); System.out.println(m.name());
        List<Git.Commit> L = new ArrayList<>();
        try {
			Iterable<RevCommit> commits = git.log().add(m).call();
			commits.forEach((c) -> {
				Git.Commit gc = displayCommit(c); L.add(gc);
    		});
		} catch (GitAPIException | IOException e) {
			System.err.println("cannot iterate commits");
		}
        return L.toArray(new Git.Commit[0]);
    }
    private String hash(ObjectId o) {
    	return Git.trim(o.getName());
    }
    private Git.Commit displayCommit(RevCommit c) {
    	PersonIdent id = c.getAuthorIdent();
    	Date time = id.getWhen();
        String name = c.getShortMessage();
		System.out.println(FORM.format(time)+"  "+name);
		RevTree tree = c.getTree();
        if (tree != null) {
			String type = Constants.typeString(tree.getType());
			String hash = hash(tree);
        	int count = size(tree);
			System.out.printf("%s %s %2s items ***  ", type, hash, count); // no LF
		}

        String parent = null;
        RevCommit[] parents = c.getParents();
        if (parents == null || parents.length == 0) System.out.println();
        else {
			parent = Arrays.stream(parents).map(p -> Git.trim(p.getName())).collect(Collectors.joining(" "));
            System.out.println("parent " + parent);
        }
        System.out.println(LINE+LINE);
		return new Git.Commit(c.getName(), name, tree.getName(), time.getTime(), parent);
	}
    private int size(RevTree tree) {
    	int size = 0;
		try (TreeWalk tw = new TreeWalk(repository)) {
			tw.addTree(tree);
			tw.setRecursive(false);
	    	// don't know a better way
			while (tw.next()) {
				size++;
			}
		} catch (Exception e) {
			System.err.println("cannot iterate tree");
		}
		return size;
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
    public Git.Tree displayTree(ObjectId objectId) {  //top level has no parent
        return displayTree(objectId, "root", null); 
    }

	Git.Tree displayTree(ObjectId objectId, String n, Git.Tree p) {
		Git.Tree gt = new Git.Tree(objectId.getName(), n, p);
		try {
			RevCommit revCommit = revCommit(head());
			RevTree tree = revCommit.getTree();

			try (TreeWalk tw = new TreeWalk(repository)) {
				tw.addTree(tree);
				tw.setRecursive(false);
				while (tw.next()) {
					ObjectId currentObject = tw.getObjectId(0);
					String hash = hash(currentObject);
					String name = tw.getPathString();
					String size = tw.isSubtree() ? "-"
							: "" + tw.getObjectReader().open(currentObject).getSize();
					System.out.printf("%s %8s %s\n", hash, size, name);
					if (tw.isSubtree()) {
						tw.enterSubtree();
						//TODO: gt.add(displayTree(currentObject, name, gt));
					} else {
						//TODO: gt.add(new Git.Blob(hash, name, gt, size));
					}
				}
			}
		} catch (IOException e) {
			System.err.println("error displaying tree");
			e.printStackTrace();
		}
		return gt;
    }
    
    private RevCommit revCommit(ObjectId commit) throws IOException {
        // a RevWalk allows to walk over commits based on some filtering that is defined
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(commit);
        }
    }

    public static void main(String[] args) {
        GitInspector gi = new GitInspector();
		gi.displayAllCommits();
		gi.displayTree(gi.head());
    }
}

