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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.RequestTokenPair;
import com.dropbox.client2.session.WebAuthSession;
import com.dropbox.client2.session.WebAuthSession.WebAuthInfo;


/**
 * Unit test for simple App.
 */
public class SessionTest
    extends TestCase
{
    AppKeyPair mConsumerTokenPair;
    String mTestingUser;
    String mTestingPassword;

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public SessionTest(String testName) {
        super(testName);

        mConsumerTokenPair = new AppKeyPair("ebv4269mzcrtqcg", "xxsm3igaqylfk73");
        mTestingUser = "dropboxapitest542351346136@thisdoesnotexist.com";
        mTestingPassword = "test123";
    }

    static {
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite( SessionTest.class );
    }

    public void test_config() {
        assert mConsumerTokenPair != null : "Please make sure config/testing.json has your consumer key and secret";
        assert !mConsumerTokenPair.key.equals("YOUR CONSUMER KEY") : "Please edit config/testing.json with the consumer key you received from Dropbox";
        assert !mConsumerTokenPair.secret.equals("YOUR CONSUMER SECRET") : "You need to change your consumer secret from the default";

        assert mTestingUser != null : "Please make sure config/testing.json has your consumer key";
        assert mTestingPassword != null : "Please make sure config/testing.json has your consumer secret";
        assert !mTestingUser.equals("YOUR DROPBOX LOGIN") : "Please edit config/testing.json with the consumer key you received from Dropbox";
        assert !mTestingPassword.equals("DROPBOX PASSWORD") : "You need to change your consumer secret from the default";
    }


    public void test_session() throws Exception {
        WebAuthSession session = new WebAuthSession(mConsumerTokenPair);

        assert session.getAppKeyPair().equals(mConsumerTokenPair);
        assert !session.isLinked() : "Session not linked";
    }

    public void test_retrieveRequestToken() throws Exception
    {
        WebAuthSession session = new WebAuthSession(mConsumerTokenPair);

        try {
            WebAuthInfo info = session.getAuthInfo("somecallback");
            assert info.requestTokenPair != null;
        } catch (DropboxException e) {
            assert false : "DropboxException: " + e.toString();
        }
    }

    /*public void test_retrieveAccessToken() throws Exception
    {
        try {
            WebAuthSession session = new WebAuthSession(mConsumerTokenPair);
            WebAuthInfo info = session.getAuthInfo();

            Util.authorizeForm(info.url, mTestingUser, mTestingPassword);

            RequestTokenPair requestTokenPair = info.requestTokenPair;

            assert session.getAccessTokenPair().equals(requestTokenPair);

            session.retrieveWebAccessToken(requestTokenPair);

            assert session.getAccessTokenPair() != null : "Failed to set the access token pair.";
            assert session.getAccessTokenPair() != requestTokenPair : "Access token pair should change.";

            assert session.isLinked() : "Session isn't linked";
        } catch (DropboxException e) {
            assert false : "Error: " + e.toString();
        }
    }*/



}
