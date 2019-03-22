/*
 * The MIT License
 *
 * Copyright 2015 Flagbit GmbH & Co. KG.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.bitbucket.model;

public class BitbucketBuildStatusResource {

//    private static final String API_ENDPOINT = "https://bitbucket.atlassian.teliacompany.net";

    private final String owner;
    private final String repoSlug;
    private final String commitId;
    private final String bitbucketHost;

    public BitbucketBuildStatusResource(String bitbucketHost, String owner, String repoSlug, String commitId) {
        this.owner = owner;
        this.repoSlug = repoSlug;
        this.commitId = commitId;
        this.bitbucketHost = bitbucketHost;
    }

    public String generateUrl(String verb) throws Exception {
        if (verb.equals("POST")) {
            return bitbucketHost + "/rest/build-status/1.0/commits/" + this.commitId;
        } else {
            throw new Exception("Verb " + verb + "not allowed or implemented");
        }
    }

    public String getCommitId() {
        return this.commitId;
    }

    public String getOwner() {
        return this.owner;
    }

    public String getRepoSlug() {
        return this.repoSlug;
    }

    public String getBitbucketHost() {
        return this.bitbucketHost;
    }
}
