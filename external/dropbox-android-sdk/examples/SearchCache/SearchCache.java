import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.Session;
import com.dropbox.client2.session.WebOAuth2Session;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DeltaEntry;

import com.dropbox.client2.jsonextract.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SearchCache
{
    public static final String STATE_FILE = "SearchCache.json";

    public static void main(String[] args)
        throws DropboxException
    {
        // We only need to do this because this is command-line example program.
        // Android takes care of this for us automatically.
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        if (args.length == 0) {
            printUsage(System.out);
            throw die();
        }

        String command = args[0];
        if (command.equals("link")) {
            doLink(args);
        }
        else if (command.equals("update")) {
            doUpdate(args);
        }
        else if (command.equals("find")) {
            doFind(args);
        }
        else if (command.equals("reset")) {
            doReset(args);
        }
        else {
            System.err.println("ERROR: Unknown command: \"" + command + "\"");
            System.err.println("Run with no arguments for help.");
            throw die();
        }
    }

    private static void doLink(String[] args)
        throws DropboxException
    {
        if (args.length != 3) {
            throw die("ERROR: \"link\" takes exactly two arguments.");
        }

        AppKeyPair appKeyPair = new AppKeyPair(args[1], args[2]);
        WebOAuth2Session was = new WebOAuth2Session(appKeyPair);

        // Make the user log in and authorize us.
        System.out.println("1. Go to: " + was.getAuthorizeURL(null, null));
        System.out.println("2. Allow access to this app.");
        System.out.println("3. Copy the code given here and press ENTER.");

        StringBuilder key = new StringBuilder();
        try {
            while (true) {
                char c = (char)System.in.read();
                if (c == '\n')
                    break;
                key.append(c);
            }
        }
        catch (IOException ex) {
            throw die("I/O error: " + ex.getMessage());
        }

        String accessToken = was.retrieveWebAccessToken(key.toString(), null);
        System.out.println("Link successful.");

        // Save state
        State state = new State(appKeyPair, accessToken, new Content.Folder());
        state.save(STATE_FILE);
    }

    private static void doUpdate(String[] args)
        throws DropboxException
    {
        int pageLimit;
        if (args.length == 2) {
            pageLimit = Integer.parseInt(args[1]);
        }
        else if (args.length == 1) {
            pageLimit = -1;
        }
        else {
            throw die("ERROR: \"update\" takes either zero or one arguments.");
        }

        // Load state.
        State state = State.load(STATE_FILE);

        // Connect to Dropbox.
        WebOAuth2Session session = new WebOAuth2Session(state.appKey);
        session.setOAuth2AccessToken(state.accessToken);
        DropboxAPI<?> client = new DropboxAPI<WebOAuth2Session>(session);

        int pageNum = 0;
        boolean changed = false;
        String cursor = state.cursor;
        while (pageLimit < 0 || (pageNum < pageLimit)) {
            // Get /delta results from Dropbox
            DropboxAPI.DeltaPage<DropboxAPI.Entry> page = client.delta(cursor);
            pageNum++;
            if (page.reset) {
                state.tree.children.clear();
                changed = true;
            }
            // Apply the entries one by one.
            for (DeltaEntry<DropboxAPI.Entry> e : page.entries) {
                applyDelta(state.tree, e);
                changed = true;
            }
            cursor = page.cursor;
            if (!page.hasMore) break;
        }

        // Save state.
        if (changed) {
            state.cursor = cursor;
            state.save(STATE_FILE);
        }
        else {
            System.out.println("No updates.");
        }
    }

    private static void printUsage(PrintStream out)
    {
        out.println("Usage:");
        out.println("    ./run link <app-key> <secret>  Link a user's account to the given app.");
        out.println("    ./run update                   Update cache to the latest on Dropbox.");
        out.println("    ./run update <num>             Update cache, limit to <num> pages of updates.");
        out.println("    ./run find <term>              Search cache for <term> (case-sensitive).");
        out.println("    ./run find                     Display entire cache.");
        out.println("    ./run reset                    Delete the cache.");
    }

    private static RuntimeException die(String message)
    {
        System.err.println(message);
        return die();
    }

    private static RuntimeException die()
    {
        System.exit(1);
        return new RuntimeException();
    }

    // ------------------------------------------------------------------------
    // Apply delta entries to the tree.

    private static void applyDelta(Content.Folder parent, DeltaEntry<DropboxAPI.Entry> e)
    {
        Path path = Path.parse(e.lcPath);
        DropboxAPI.Entry md = e.metadata;

        if (md != null) {
            System.out.println("+ " + e.lcPath);
            // Traverse down the tree until we find the parent of the entry we
            // want to add.  Create any missing folders along the way.
            for (String b : path.branch) {
                Node n = getOrCreateChild(parent, b);
                if (n.content instanceof Content.Folder) {
                    parent = (Content.Folder) n.content;
                } else {
                    // No folder here, automatically create an empty one.
                    n.content = parent = new Content.Folder();
                }
            }

            // Create the file/folder here.
            Node n = getOrCreateChild(parent, path.leaf);
            n.path = md.path;  // Save the un-lower-cased path.
            if (md.isDir) {
                // Only create an empty folder if there isn't one there already.
                if (!(n.content instanceof Content.Folder)) {
                    n.content = new Content.Folder();
                }
            }
            else {
                n.content = new Content.File(md.size, md.modified, md.clientMtime);
            }
        }
        else {
            System.out.println("- " + e.lcPath);
            // Traverse down the tree until we find the parent of the entry we
            // want to delete.
            boolean missingParent = false;
            for (String b : path.branch) {
                Node n = parent.children.get(b);
                if (n != null && n.content instanceof Content.Folder) {
                    parent = (Content.Folder) n.content;
                } else {
                    // If one of the parent folders is missing, then we're done.
                    missingParent = true;
                    break;
                }
            }

            if (!missingParent) {
                parent.children.remove(path.leaf);
            }
        }
    }

    private static Node getOrCreateChild(Content.Folder folder, String lowercaseName)
    {
        Node n = folder.children.get(lowercaseName);
        if (n == null) {
            folder.children.put(lowercaseName, n = new Node(null, null));
        }
        return n;
    }

    /**
     * Represent a path as a list of ancestors and a leaf name.
     *
     * For example, "/a/b/c" -> Path(["a", "b"], "c")
     */
    public static final class Path
    {
        public final String[] branch;
        public final String leaf;

        public Path(String[] branch, String leaf)
        {
            assert branch != null;
            assert leaf != null;
            this.branch = branch;
            this.leaf = leaf;
        }

        public static Path parse(String s)
        {
            assert s.startsWith("/");
            String[] parts = s.split("/");
            assert parts.length > 0;

            String[] branch = new String[parts.length-2];
            System.arraycopy(parts, 1, branch, 0, branch.length);
            String leaf = parts[parts.length-1];
            return new Path(branch, leaf);
        }
    }

    // ------------------------------------------------------------------------
    // Search through the tree.

    private static void doFind(String[] args)
        throws DropboxException
    {
        String term;
        if (args.length == 1) {
            term = "";
        }
        else if (args.length == 2) {
            term = args[1];
        }
        else {
            throw die("ERROR: \"find\" takes either zero or one arguments");
        }

        // Load cached state.
        State state = State.load(STATE_FILE);

        ArrayList<String> results = new ArrayList<String>();
        searchTree(results, state.tree, term);
        for (String r : results) {
            System.out.println(r);
        }
        if (results.isEmpty()) {
            System.out.println("[No matches.]");
        }
    }

    private static void searchTree(ArrayList<String> results, Content.Folder tree, String term)
    {
        for (Map.Entry<String,Node> child : tree.children.entrySet()) {
            Node n = child.getValue();
            String path = n.path;
            if (path != null && path.contains(term)) {
                if (n.content instanceof Content.Folder) {
                    results.add(path);
                }
                else if (n.content instanceof Content.File) {
                    Content.File f = (Content.File) n.content;
                    results.add(path + " (" + f.size + ", " + f.lastModified + ", " + f.clientMtime + ")");
                }
                else {
                    throw new AssertionError("bad type: " + n.content);
                }
            }
            // Recurse on children.
            if (n.content instanceof Content.Folder) {
                Content.Folder f = (Content.Folder) n.content;
                searchTree(results, f, term);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Reset state

    private static void doReset(String[] args)
        throws DropboxException
    {
        if (args.length != 1) {
            throw die("ERROR: \"reset\" takes no arguments");
        }

        // Load state.
        State state = State.load(STATE_FILE);

        // Clear state.
        state.tree.children.clear();
        state.cursor = null;

        // Save state back.
        state.save(STATE_FILE);
    }

    // ------------------------------------------------------------------------
    // State model (load+save to JSON)

    public static final class State
    {
        public final AppKeyPair appKey;
        public final String accessToken;
        public final Content.Folder tree;

        public State(AppKeyPair appKey, String accessToken, Content.Folder tree)
        {
            this.appKey = appKey;
            this.accessToken = accessToken;
            this.tree = tree;
        }

        public String cursor;

        public void save(String fileName)
        {
            JSONObject jstate = new JSONObject();

            // Convert app key
            JSONArray japp = new JSONArray();
            japp.add(appKey.key);
            japp.add(appKey.secret);
            jstate.put("app_key", japp);

            // Convert access token
            jstate.put("access_token", accessToken);

            // Convert tree
            JSONObject jtree = tree.toJson();
            jstate.put("tree", jtree);

            // Convert cursor, if present.
            if (cursor != null) {
                jstate.put("cursor", cursor);
            }

            try {
                FileWriter fout = new FileWriter(fileName);
                try {
                    jstate.writeJSONString(fout);
                }
                finally {
                    fout.close();
                }
            }
            catch (IOException ex) {
                throw die("ERROR: unable to save to state file \"" + fileName + "\": " + ex.getMessage());
            }
        }

        public static State load(String fileName)
        {
            JsonThing j;
            try {
                FileReader fin = new FileReader(fileName);
                try {
                    j = new JsonThing(new JSONParser().parse(fin));
                } catch (ParseException ex) {
                    throw die("ERROR: State file \"" + fileName + "\" isn't valid JSON: " + ex.getMessage());
                } finally {
                    fin.close();
                }
            }
            catch (IOException ex) {
                throw die("ERROR: unable to load state file \"" + fileName + "\": " + ex.getMessage());
            }

            try {
                JsonMap jm = j.expectMap();

                JsonList japp = jm.get("app_key").expectList();
                AppKeyPair appKey = new AppKeyPair(japp.get(0).expectString(), japp.get(1).expectString());

                String accessToken = jm.get("access_token").expectString();

                JsonMap jtree = jm.get("tree").expectMap();
                Content.Folder tree = Content.Folder.fromJson(jtree);

                State state = new State(appKey, accessToken, tree);

                JsonThing jcursor = jm.getOrNull("cursor");
                if (jcursor != null) {
                    state.cursor = jcursor.expectString();
                }

                return state;
            }
            catch (JsonExtractionException ex) {
                throw die ("ERROR: State file has incorrect structure: " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------
    // We represent our local cache as a tree of 'Node' objects.
    public static final class Node
    {
        /**
         * The original path of the file.  We track this separately because
         * Folder.children only contains lower-cased names.
         */
        public String path;

        /**
         * The node content (either Content.File or Content.Folder)
         */
        public Content content;

        public Node(String path, Content content)
        {
            this.path = path;
            this.content = content;
        }

        public final JSONArray toJson()
        {
            JSONArray array = new JSONArray();
            array.add(path);
            array.add(content.toJson());
            return array;
        }

        public static Node fromJson(JsonThing t)
            throws JsonExtractionException
        {
            JsonList l = t.expectList();
            String path = l.get(0).expectStringOrNull();
            JsonThing jcontent = l.get(1);
            Content content;
            if (jcontent.isList()) {
                content = Content.File.fromJson(jcontent.expectList());
            } else if (jcontent.isMap()) {
                content = Content.Folder.fromJson(jcontent.expectMap());
            } else {
                throw jcontent.unexpected();
            }
            return new Node(path, content);
        }
    }

    public static abstract class Content
    {
        public abstract Object toJson();

        public static final class Folder extends Content
        {
            public final HashMap<String,Node> children = new HashMap<String,Node>();

            public JSONObject toJson()
            {
                JSONObject o = new JSONObject();
                for (Map.Entry<String,Node> c : children.entrySet()) {
                    o.put(c.getKey(), c.getValue().toJson());
                }
                return o;
            }

            public static Folder fromJson(JsonMap j)
                throws JsonExtractionException
            {
                Folder folder = new Folder();
                for (Map.Entry<String,JsonThing> e : j) {
                    folder.children.put(e.getKey(), Node.fromJson(e.getValue()));
                }
                return folder;
            }
        }

        public static final class File extends Content
        {
            public final String size;
            public final String lastModified;
            public final String clientMtime;

            public File(String size, String lastModified, String clientMtime)
            {
                this.size = size;
                this.lastModified = lastModified;
                this.clientMtime = clientMtime;
            }

            public JSONArray toJson()
            {
                JSONArray j = new JSONArray();
                j.add(size);
                j.add(lastModified);
                j.add(clientMtime);
                return j;
            }

            public static File fromJson(JsonList l)
                throws JsonExtractionException
            {
                return new File(l.get(0).expectString(), l.get(1).expectString(), l.get(2).expectString());
            }
        }
    }

}
