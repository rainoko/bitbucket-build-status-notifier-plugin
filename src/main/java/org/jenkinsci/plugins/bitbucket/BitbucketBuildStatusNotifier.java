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

package org.jenkinsci.plugins.bitbucket;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketBuildStatusNotifier extends Notifier {

  private static final Logger logger = Logger.getLogger(BitbucketBuildStatusNotifier.class.getName());

  private final boolean notifyStart;
  private final boolean notifyFinish;
  private final boolean overrideLatestBuild;
  private final String credentialsId;

  @DataBoundConstructor
  public BitbucketBuildStatusNotifier(final boolean notifyStart, final boolean notifyFinish,
                                      final boolean overrideLatestBuild, final String credentialsId) {
    super();
    this.notifyStart = notifyStart;
    this.notifyFinish = notifyFinish;
    this.overrideLatestBuild = overrideLatestBuild;
    this.credentialsId = credentialsId;
  }

  public boolean getNotifyStart() {
    return this.notifyStart;
  }

  public boolean getNotifyFinish() {
    return this.notifyFinish;
  }

  public boolean getOverrideLatestBuild() {
    return this.overrideLatestBuild;
  }

  public String getCredentialsId() {
    return this.credentialsId != null ? this.credentialsId : this.getDescriptor().getGlobalCredentialsId();
  }

  private StandardUsernamePasswordCredentials getCredentials(AbstractBuild<?, ?> build) {
    StandardUsernamePasswordCredentials credentials = BitbucketBuildStatusHelper
      .getCredentials(getCredentialsId(), build.getProject());
    if (credentials == null) {
      credentials = BitbucketBuildStatusHelper
        .getCredentials(this.getDescriptor().getGlobalCredentialsId(), null);
    }
    return credentials;
  }

  private String getBitbucketHost() {
    return this.getDescriptor().getGlobalCredentialsId();
  }

  @Override
  public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    if (!this.notifyStart) {
      return true;
    }
    logger.info("Bitbucket notify on start");

    try {
      BitbucketBuildStatusHelper.notifyBuildStatus(
        this.getCredentials(build),
        this.getBitbucketHost(),
        this.getOverrideLatestBuild(),
        build,
        listener
      );
    }
    catch (Exception e) {
      listener.getLogger().println("Bitbucket notify on start failed: " + e.getMessage());
      e.printStackTrace(listener.getLogger());
    }

    logger.info("Bitbucket notify on start succeeded");

    return true;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
    if (!this.notifyFinish) {
      return true;
    }
    logger.info("Bitbucket notify on finish");

    try {
      BitbucketBuildStatusHelper.notifyBuildStatus(
        this.getCredentials(build),
        this.getBitbucketHost(),
        this.getOverrideLatestBuild(),
        build,
        listener);
    }
    catch (Exception e) {
      logger.log(Level.INFO, "Bitbucket notify on finish failed: " + e.getMessage(), e);
      listener.getLogger().println("Bitbucket notify on finish failed: " + e.getMessage());
      e.printStackTrace(listener.getLogger());
    }

    logger.info("Bitbucket notify on finish succeeded");

    return true;
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return Jenkins.getInstanceOrNull().getDescriptorByType(DescriptorImpl.class);
  }

  @Override
  public boolean needsToRunAfterFinalized() {
    //This is here to ensure that the reported build status is actually correct. If we were to return false here,
    //other build plugins could still modify the build result, making the sent out HipChat notification incorrect.
    return true;
  }

  @Extension // This indicates to Jenkins that this is an implementation of an extension point.
  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    private String globalCredentialsId;
    private String bitbucketHost;

    public DescriptorImpl() {
      load();
    }

    public String getGlobalCredentialsId() {
      return this.globalCredentialsId;
    }
    public String getBitbucketHost() {
      return this.bitbucketHost;
    }

    public void setGlobalCredentialsId(String globalCredentialsId) {
      this.globalCredentialsId = globalCredentialsId;
    }

    public void setBitbucketHost(String host) {
      this.bitbucketHost = host;
    }

    @Override
    public String getDisplayName() {
      return "Bitbucket notify build status";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
      req.bindJSON(this, formData.getJSONObject("bitbucket-build-status-notifier"));
      save();

      return true;
    }

    public FormValidation doCheckBitbucketHost(@QueryParameter final String bitbucketHost,
                                               @AncestorInPath final Job<?,?> owner) {
      if(!bitbucketHost.startsWith("http")) {
        return FormValidation.error("Please enter full url of host (with http)");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillGlobalCredentialsIdItems() {
      Job owner = null;

      return new StandardUsernameListBoxModel()
        .includeEmptyValue()
        .withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null));
    }
  }
}
