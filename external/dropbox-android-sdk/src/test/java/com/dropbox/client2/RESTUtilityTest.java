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

/**
 * Unit test for simple App.
 */
public class RESTUtilityTest extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public RESTUtilityTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( RESTUtilityTest.class );
    }

    /**
     * Right now, we just test buidURL, since the other RESTUtility
     * methods require a Session object.
     * @throws Exception
     */
    public void test_builUrlNoparams() throws Exception {
        RESTUtility.buildURL("foo", 0, "bar", null);

        String noparamsurl = RESTUtility.buildURL("foo", 0, "/bar", null);
        assert noparamsurl.equals("https://foo:443/0/bar") : "Error in no params: " + noparamsurl;
    }

    public void test_buildUrlParams() throws Exception {
        // Check normal characters
        String[] params = {"hey", "there"};
        String paramsurl = RESTUtility.buildURL("foo", 0, "/bar", params);
        assert paramsurl.equals("https://foo:443/0/bar?hey=there")  : "Error in params: " + paramsurl;
    }


    public void test_buildLongUrl() throws Exception {
        // Check for long urls with lots of slashes
        String[] params = {"hey", "there"};
        String longurl = RESTUtility.buildURL("foo", 0, "/bar/meta/data/is/fun/we/love/slashes", params);
        assert longurl.equals("https://foo:443/0/bar/meta/data/is/fun/we/love/slashes?hey=there") :
            "Error in long url: " + longurl;
    }

    public void test_buildCrazyUrl() throws Exception {
        // Check for crazy params
        String[] crazyparams = {"my file", "We have spaces, ? marks & ampersand # hashes"};
        String crazyurl = RESTUtility.buildURL("foo", 0, "/bar", crazyparams);
        assert crazyurl.equals("https://foo:443/0/bar?my%20file=We%20have%20spaces" +
                "%2C%20%3F%20marks%20%26%20ampersand%20%23%20hashes") :
            "Error in crazy params: " + crazyurl;
    }

    public void test_buildOddParams() throws Exception {
        try {
            String[] oddParams = {"hey", "there", "you"};
            RESTUtility.buildURL("foo", 0, "/bar", oddParams);
            assert false  : "Didn't throw error with odd-numbered params";
        } catch (IllegalArgumentException e) {
        }
    }

}


