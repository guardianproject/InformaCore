/*
 * Copyright (c) 2009-2011 Dropbox Inc.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
*/

package com.dropbox.client2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.junit.Test;

import com.dropbox.client2.DropboxAPI.Account;
import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.DropboxAPI.DropboxInputStream;
import com.dropbox.client2.DropboxAPI.DropboxLink;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.DropboxAPI.NameDetails;
import com.dropbox.client2.DropboxAPI.TeamInfo;
import com.dropbox.client2.DropboxAPI.ThumbFormat;
import com.dropbox.client2.DropboxAPI.ThumbSize;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.WebOAuth2Session;

/**
 * Unit test for simple App.
 */
public class DropboxAPITest {
    private static DropboxAPI<?> api;
    static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private final static String TESTS_DIR = "/" + dateFormat.format(new Date());
    private final File foo = new File("testfiles", "foo.txt");
    private final File song = new File("testfiles", "dropbox_song.mp3");
    private final File frog = new File("testfiles", "Costa Rican Frog.jpg");

    static {
        try {
            AppKeyPair consumerTokenPair = new AppKeyPair("", "");  // Don't need this for OAuth 2
            WebOAuth2Session session = new WebOAuth2Session(consumerTokenPair, System.getProperty("access_token"));
            api = new DropboxAPI<WebOAuth2Session>(session);
        } catch (Throwable t) {
            t.printStackTrace();
            assert false : "Total failure trying to start WebAuthSession." + t;
        }
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public void assertFile(Entry e, File f, String path){
        assertEquals(e.bytes, f.length());
        assertEquals(e.isDir, false);
        assertEquals(e.path, path);
    }

    /**
     * @return the suite of tests being tested
     */
    @Test
    public void accountInfo() throws Exception {

        Account info = api.accountInfo();
        assert info.country != null : "No country for account";
        assert info.displayName != null : "No displayName for account";
        assert info.email != null : "No email for account";
        assert info.quota > 0 : "0 quota in account";
        assert info.quotaNormal > 0 : "0 normal quota in account";
        assert info.referralLink != null : "No referral link for account";
        assert info.uid > 0 : "No uid for account";
        boolean isOverQuota = info.quotaNormal + info.quotaShared > info.quota;
        assert info.isOverQuota() == isOverQuota : "IsOverQuota failed";

        TeamInfo teamInfo = info.teamInfo;
        if (teamInfo != null) {
            assert teamInfo.name != null : "No team name for account";
            assert teamInfo.teamId != null : "No team id for account";
            System.out.println("User is on team " + teamInfo.name);
        } else {
            System.out.println("User is not on a team");
        }

        // name info (English locale assumptions)
        assert info.locale.equals("en") : "Unexpected locale";
        NameDetails nameDetails = info.nameDetails;
        assert nameDetails.givenName.equalsIgnoreCase(nameDetails.familiarName);
        assert info.displayName.equals(nameDetails.givenName + " " + nameDetails.surname);
    }

    // Get metadata for a nonexistent directory
    @Test
    public void getMetadataBad() throws Exception {
        try {
            String path = TESTS_DIR + "iDoNotExist.txt";
            Entry ent = api.metadata(path, 1000, null, true, null);
            assert ent.isDeleted: "The " + path + " directory should not exist";
        } catch (DropboxServerException e) {
            if (e.error != DropboxServerException._404_NOT_FOUND) {
                assert false: "Unexpected Dropbox Server Error: " + e.toString();
            }
        } catch (DropboxException e) {
            assert false: "Unexpected Dropbox Error: " + e.toString();
        }
    }

    // Create the /tests directory
    @Test
    public void createFolder() throws Exception {

        String path = TESTS_DIR + "createdFolder";
        Entry ent = api.createFolder(path);
        assertEquals(ent.isDir, true);

    }

    // Try to create another one, and make sure we get an error
    @Test
    public void createFolderDupe() throws Exception {
        try {
            api.createFolder(TESTS_DIR);
            assert false : "Able to create duplicate folder";
        } catch (DropboxServerException e) {
            if (e.error != DropboxServerException._403_FORBIDDEN) {
                assert false: "Unexpected Dropbox Server Error: " + e.toString();
            }
        } catch (DropboxException e) {
            assert false: "Unexpected Dropbox Error: " + e.toString();
        }
    }

    // Get metadata for the new /tests directory
    @Test
    public void getMetadata() throws Exception {
        String path = TESTS_DIR + "/metadatasong.mp3";
        uploadFile(song, path);
        api.metadata(path, 1000, null, true, null);
    }

    // Get metadata for the new /tests directory, saving the hash
    @Test
    public void getMetadataCached() throws Exception {
        try {
            Entry ent = api.metadata(TESTS_DIR, 1000, null, true, null);
            api.metadata(TESTS_DIR, 1000, ent.hash, true, null);
            assert false : "Directory should have been cached";
        } catch (DropboxServerException e) {
            if (e.error != DropboxServerException._304_NOT_MODIFIED) {
                assert false: "Unexpected Dropbox Server Error: " + e.toString();
            }
        } catch (DropboxException e) {
            assert false: "Unexpected Dropbox Error: " + e.toString();
        }
    }

    // Put a simple text file into /tests/foo.txt
    @Test
    public void putFile() throws Exception {
        assertPutFile(foo, TESTS_DIR + "/putfoo.txt");
        assertPutFile(frog, TESTS_DIR + "/putfrog.jpg");
        assertPutFile(song, TESTS_DIR + "/putsong.mp3");
    }

    public void assertPutFile(File src, String destPath) throws Exception{
        FileInputStream fis = new FileInputStream(src);
        Entry ent = api.putFile(destPath, fis, src.length(), null, null);
        assertEquals(ent.path, destPath);
        assertEquals(ent.bytes, src.length());
        assertEquals(ent.isDir, false);
    }

    @Test
    public void getFile() throws Exception {
        assertGetFile(foo, TESTS_DIR + "/getfoo.txt");
        assertGetFile(song, TESTS_DIR + "/getsong.mp3");
        assertGetFile(frog, TESTS_DIR + "/getfrog.jpg");
    }

    public void assertGetFile(File src, String destPath) throws Exception{
        uploadFile(src, destPath);
        byte[] downloaded = Util.streamToBytes(api.getFileStream(destPath, null));
        byte[] local = Util.streamToBytes(new FileInputStream(src));
        assertArrayEquals(downloaded, local);
    }


    @Test
    public void getThumbnail() throws Exception {
        String path = TESTS_DIR + "/thumbnailfrog.jpg";
        uploadFile(frog, path);

        OutputStream out = new ByteArrayOutputStream();

        DropboxFileInfo info = api.getThumbnail(path, out,
                ThumbSize.BESTFIT_480x320, ThumbFormat.JPEG, null);

        assert info != null : "No info returned";
        assert info.getFileSize() > 0 : "Thumbnail length 0";
        assert info.getMetadata().bytes > 0 : "Original file size 0";
        assert info.getFileSize() != info.getMetadata().bytes : "Thumbnail equals original file size.";
    }

    @Test
    public void getThumbnailStream() throws Exception {
        String path = TESTS_DIR + "/thumbnailstreamfrog.jpg";
        uploadFile(frog, path);
        DropboxInputStream is = api.getThumbnailStream(path, ThumbSize.BESTFIT_480x320, ThumbFormat.JPEG);

        assert is != null : "No info returned";
        assert is.getFileInfo().getFileSize() > 0 : "Thumbnail length 0";
        assert is.getFileInfo().getMetadata().bytes > 0 : "Original file size 0";
    }

    private String createRandomContents(Random rng, int length, String characters) {
        char[] text = new char[length];
        for (int i = 0; i < length; i++)
        {
            text[i] = characters.charAt(rng.nextInt(characters.length()));
        }
        return new String(text);
    }

    // Only enable this test if you want to test the behavior of skipping. This
    // was written to test a bug report that came in, but it doesn't look at
    // this time that this is worthy of testing regularly.
    //@Test
    public void skipsCorrectly() throws Exception {
        String path = TESTS_DIR + "/randomtext.txt";

        // length of file in bytes to generate
        int length = 1000000;
        // number of bytes to skip
        int skipCount = 950000;
        // number of bytes after the skip offset to use to verify the correctness
        // of the skip function
        int bytesToVerify = 300;

        String contents = createRandomContents(new Random(), length, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");

        // create input stream from random byte string
        InputStream fis = new ByteArrayInputStream(contents.getBytes("US-ASCII"));

        // put & get same file
        api.putFileOverwrite(path, fis, contents.length(), null);
        DropboxInputStream is = api.getFileStream(path, null);

        // skip bytes
        long amtSkipped = is.skip(skipCount);

        byte[] outBytes = new byte[bytesToVerify];
        is.read(outBytes, 0, bytesToVerify);

        assertEquals(new String(outBytes), contents.substring((int)amtSkipped, (int)amtSkipped+bytesToVerify));
    }

    private Entry putRandomFile(String destPath, String parentRev) throws Exception {
        int len = 10;
        String data = createRandomContents(new Random(), len,
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");
        InputStream in = new ByteArrayInputStream(data.getBytes("US-ASCII"));
        Entry ent = api.putFile(destPath, in, len, parentRev, null);

        assertEquals(ent.bytes, len);
        assertEquals(ent.isDir, false);
        return ent;
    }


    private Entry putRandomFile(String destPath, String parentRev, boolean autoRename) throws Exception {
        int len = 10;
        String data = createRandomContents(new Random(), len,
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890");
        InputStream in = new ByteArrayInputStream(data.getBytes("US-ASCII"));
        return api.putFile(destPath, in, len, parentRev, autoRename, null);
    }

    @Test
    public void testConflict() throws Exception {
        String fname = TESTS_DIR + "/psingle.txt";
        Entry baseres = putRandomFile(fname, null);
        assert baseres.path.equals(fname) : ("unexpected changed file " + baseres.path + " " + fname);

        // auto rename
        Entry res = putRandomFile(fname, null);
        assert !res.path.equals(fname) : "unexpected same file!";

        // ditto
        res = putRandomFile(fname, null, true);
        assert !res.path.equals(fname) : "unexpected same file!";

        // blow up
        try {
            putRandomFile(fname, null, false);
            assert false : "Conflict should have been raised";
        } catch (DropboxServerException e) {
            if (e.error != DropboxServerException._409_CONFLICT) {
                assert false: "Unexpected Dropbox Server Error: " + e.toString();
            }
        }
    }

    public void uploadFileOverwrite(File src, String target) throws Exception{
        FileInputStream fis = new FileInputStream(src);
        api.putFileOverwrite(target, fis, src.length(), null);
    }
    public void uploadFile(File src, String target) throws Exception{
        FileInputStream fis = new FileInputStream(src);
        api.putFile(target, fis, src.length(), null, null);
    }

    @Test
    public void copy() throws Exception
    {
        String fromPath = TESTS_DIR + "/copyFrom.jpg";
        String toPath = TESTS_DIR + "/copyTo.jpg";
        uploadFile(frog, fromPath);

        Entry ent = api.copy(fromPath, toPath);
        assertFile(ent, frog, toPath);

        ent = api.metadata(toPath, 1, null, false, null);
        assertFile(ent, frog, toPath);
    }

    @Test
    public void move() throws Exception
    {

        String fromPath = TESTS_DIR + "/moveFrom.jpg";
        String toPath = TESTS_DIR + "/moveTo.jpg";

        uploadFile(frog, fromPath);

        Entry moveEnt = api.move(fromPath, toPath);
        assertFile(moveEnt, frog, toPath);

        moveEnt = api.metadata(toPath, 1, null, false, null);
        assertFile(moveEnt, frog, toPath);

        Entry oldEnt = api.metadata(fromPath, 1, null, false, null);
        assertEquals(oldEnt.isDeleted, true);
        assertEquals(oldEnt.bytes, 0);
        assert oldEnt.isDeleted;
    }

    @Test
    public void media() throws Exception {

        String path = TESTS_DIR + "/dropbox_song.mp3";
        uploadFile(song, path);

        DropboxLink link = api.media(path, true);
        assert link.url != null : "No url for streamed file";

        link = api.media(path, false);
        assert link.url != null : "No insecure url for streamed file";
    }

    @Test
    public void share() throws Exception{
        String path = TESTS_DIR + "/dropbox_song.mp3";
        uploadFile(song, path);

        DropboxLink link = api.share(path);
        assert link.url != null : "No url for streamed file";
    }

    @Test
    public void search() throws Exception {
        try {
            // Should be too short, and give an error
            api.search("/", "", 1000, false);
            assert false: "Short (empty) search string did not throw an error.";
        } catch (DropboxServerException e) {
            if (e.error != DropboxServerException._400_BAD_REQUEST) {
                assert false: "Unexpected Dropbox Error: " + e.toString();
            }
        }
        String searchDir = TESTS_DIR + "/searchDir/";
        uploadFile(foo, searchDir + "text.txt");
        uploadFile(foo, searchDir + "subFolder/text.txt");
        uploadFile(foo, searchDir + "subFolder/cow.txt");
        uploadFile(frog, searchDir + "frog.jpg");
        uploadFile(frog, searchDir + "frog2.jpg");
        uploadFile(frog, searchDir + "subFolder/frog2.jpg");

        List<Entry> results = api.search(searchDir, "sadfasdf", 1000, false);
        assertEquals(results.size(), 0);

        results = api.search(searchDir, "jpg", 1000, false);

        assertEquals(results.size(), 3);

        HashSet<String> resultPaths = new HashSet<String>();
        for (DropboxAPI.Entry e : results) {
            assertEquals(e.bytes, frog.length());
            assertEquals(e.isDir, false);
            resultPaths.add(e.path);
        }
        resultPaths.equals(new HashSet<String>(Arrays.asList(
                searchDir + "frog.jpg",
                searchDir + "frog2.jpg",
                searchDir + "subFolder/frog2.jpg")));

        results = api.search(searchDir + "subFolder", "jpg", 1000, false);

        assertEquals(results.size(), 1);

        assertFile(results.get(0), frog, searchDir + "subFolder/frog2.jpg");
    }


    @Test
    public void revisionsAndRestore() throws Exception {
        String path = TESTS_DIR + "/revfoo.txt";
        uploadFileOverwrite(foo, path);
        uploadFileOverwrite(frog, path);
        uploadFileOverwrite(foo, path);

        List<Entry> revs = api.revisions(path, 1000);

        assert revs.size() == 3 : "Not enough revs for file";

        assertFile(revs.get(0), foo, path);
        assertFile(revs.get(1), frog, path);
        assertFile(revs.get(2), foo, path);
        for (Entry rev: revs) {
            System.out.println("Revision: " + rev.rev + " modified: " + rev.modified);
        }
        Entry reverted = api.restore(path, revs.get(1).rev);
        assertFile(reverted, frog, path);
    }

    @Test
    public void chunkedUploadPlain() throws Exception {
        chunkedUploadsTestBase("/cu1.dat", 10*1024*1024, 10*1024*1024, 4000000, false);
    }

    @Test
    public void chunkedUploadStream() throws Exception {
        chunkedUploadsTestBase("/cu2.dat", 2*1024*1024, -1, 4000000, false);
    }

    @Test
    public void chunkedUploadStreamWithoutLeftover() throws Exception {
        chunkedUploadsTestBase("/cu3.dat", 2*1024*1024, -1, 1024*1024, false);
    }

    @Test
    public void chunkedUploadStreamOverrun() throws Exception {
        try {
            chunkedUploadsTestBase("/cu4.dat", 1024 * 1024, 1024 * 1024 + 1, 1024 * 1024, false);
            assert false : "Expected IllegalStateException due to insufficient data in input stream";
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void chunkedUploadAbort() throws Exception {
        try {
            chunkedUploadsTestBase("/cu5.dat", 10*1024*1024, 10*1024*1024, 4000000, true);
            assert false: "No exception???";
        } catch (DropboxPartialFileException d) {
        }
    }

    private void chunkedUploadsTestBase(String filename, int dataSizeInBytes, int bytesToUpload, int chunkSize, boolean abort) throws Exception{
        String path = TESTS_DIR + filename;
        Random r = new Random();
        byte[] bytes = new byte[dataSizeInBytes];
        r.nextBytes(bytes);
        System.out.println("Uploading");
        final boolean[] progressListenerFired = {false};
        final DropboxAPI.ChunkedUploader uploader = api.getChunkedUploader(new ByteArrayInputStream(bytes), bytesToUpload, chunkSize);
        if (abort) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                        System.out.println("KILLING");
                        uploader.abort();
                    } catch(Exception e) {
                        throw new AssertionError(e);
                    }
                }
            }).start();
        }
        while(!uploader.isComplete()) {
            try {
                uploader.upload(new ProgressListener(){
                    @Override
                    public void onProgress(long bytes, long total) {
                    System.out.println("progress: " + bytes);
                    progressListenerFired[0] = true;
                    }
                });
            } catch (Exception e) {
                System.out.println(uploader.getOffset());
                if (!uploader.getActive()) {
                    throw e;
                }
                throw e;
            }
        }

        uploader.finish(path, null);
        DropboxAPI.DropboxInputStream byteStream = api.getFileStream(path, null);

        byte[] returnedBytes = Util.streamToBytes(byteStream);
        assertTrue(progressListenerFired[0]);
        assertEquals(bytes.length, returnedBytes.length);
        assertArrayEquals(returnedBytes, bytes);
    }
}
