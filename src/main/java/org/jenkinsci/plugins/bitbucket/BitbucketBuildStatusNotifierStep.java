/*
 * The MIT License
 *
 * Copyright 2015 Marco Marche (aka trik).
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

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatus;
import org.jenkinsci.plugins.bitbucket.validator.BitbucketHostValidator;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

public class BitbucketBuildStatusNotifierStep extends Step {

  private static final Logger logger = Logger.getLogger(BitbucketBuildStatusNotifierStep.class.getName());

  private String credentialsId;
  private String buildKey;
  private String buildName;
  private String buildDescription;
  private String buildState;
  private String repoSlug;
  private String commitId;

  @DataBoundConstructor
  public BitbucketBuildStatusNotifierStep(final String buildState) {
    this.buildState = buildState;
  }

  public String getCredentialsId() {
    return this.credentialsId;
  }

  @DataBoundSetter
  public void setCredentialsId(String credentialsId) {
    this.credentialsId = credentialsId;
  }

  public String getBuildKey() {
    return this.buildKey;
  }

  @DataBoundSetter
  public void setBuildKey(String buildKey) {
    this.buildKey = buildKey;
  }

  public String getBuildName() {
    return this.buildName;
  }

  @DataBoundSetter
  public void setBuildName(String buildName) {
    this.buildName = buildName;
  }

  public String getBuildDescription() {
    return this.buildDescription;
  }

  @DataBoundSetter
  public void setBuildDescription(String buildDescription) {
    this.buildDescription = buildDescription;
  }

  public String getBuildState() {
    return this.buildState;
  }

  public String getRepoSlug() {
    return this.repoSlug;
  }

  @DataBoundSetter
  public void setRepoSlug(String repoSlug) {
    this.repoSlug = repoSlug;
  }

  public String getCommitId() {
    return this.commitId;
  }

  @DataBoundSetter
  public void setCommitId(String commitId) {
    this.commitId = commitId;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new Execution(this, context);
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return Jenkins.getInstanceOrNull().getDescriptorByType(DescriptorImpl.class);
  }

  private StandardUsernamePasswordCredentials getCredentials(Run<?, ?> build) {
    StandardUsernamePasswordCredentials credentials = BitbucketBuildStatusHelper
      .getCredentials(getCredentialsId(), build.getParent());
    if (credentials == null) {
      credentials = BitbucketBuildStatusHelper
        .getCredentials(this.getDescriptor().getGlobalCredentialsId(), null);
    }
    return credentials;
  }

  private String getBitbucketHost() {
    return getDescriptor().getBitbucketHost();
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {

    private String globalCredentialsId;
    private String bitbucketHost;

    public String getGlobalCredentialsId() {
      return globalCredentialsId;
    }

    public void setGlobalCredentialsId(String globalCredentialsId) {
      this.globalCredentialsId = globalCredentialsId;
    }

    public String getBitbucketHost() {
      return bitbucketHost;
    }

    public void setBitbucketHost(String bitbucketHost) {
      this.bitbucketHost = bitbucketHost;
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return ImmutableSet.of(FilePath.class, FlowNode.class, TaskListener.class, Launcher.class);
    }

    @Override
    protected XmlFile getConfigFile() {
      return new XmlFile(new File(Jenkins.getInstanceOrNull().getRootDir(), this.getId().replace("Step", "") + ".xml"));
    }

    @Override
    public String getFunctionName() {
      return "bitbucketStatusNotify";
    }

    @Override
    @Nonnull
    public String getDisplayName() {
      return "Notify a build status to BitBucket.";
    }
  }

  public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;
    private transient Run<?, ?> build;
    private transient TaskListener taskListener;

    private transient BitbucketBuildStatusNotifierStep step;

    protected Execution(@Nonnull BitbucketBuildStatusNotifierStep step, @Nonnull StepContext context) throws IOException, InterruptedException {
      super(context);
      this.step = step;
      build = context.get(Run.class);
      taskListener = context.get(TaskListener.class);
    }

    private void readGlobalConfiguration() {
      XmlFile config = step.getDescriptor().getConfigFile();
      BitbucketBuildStatusNotifier.DescriptorImpl cfg = new BitbucketBuildStatusNotifier.DescriptorImpl();
      try {
        config.unmarshal(cfg);
        step.getDescriptor().setGlobalCredentialsId(cfg.getGlobalCredentialsId());
        step.getDescriptor().setBitbucketHost(cfg.getBitbucketHost());
      }
      catch (IOException e) {
        logger.warning("Unable to read BitbucketBuildStatusNotifier configuration");
      }
    }

    @Override
    public Void run() throws Exception {
      this.readGlobalConfiguration();

      String buildState = step.getBuildState();

      String buildKey = step.getBuildKey();
      if (buildKey == null) {
        buildKey = BitbucketBuildStatusHelper.uniqueBitbucketBuildKeyFromBuild(build);
      }

      String buildName = step.getBuildName();
      if (buildName == null) {
        buildName = BitbucketBuildStatusHelper.defaultBitbucketBuildNameFromBuild(build);
      }

      String buildDescription = step.getBuildDescription();
      if (buildDescription == null) {
        buildDescription = BitbucketBuildStatusHelper.defaultBitbucketBuildDescriptionFromBuild(build);
      }

      String commitId = step.getCommitId();
      String repoSlug = step.getRepoSlug();
      logger.info("Got commit id " + commitId);
      logger.info("Got repo slug = " + repoSlug);

      String buildUrl = BitbucketBuildStatusHelper.buildUrlFromBuild(build);

      BitbucketBuildStatus buildStatus = new BitbucketBuildStatus(buildState, buildKey, buildUrl, buildName,
        buildDescription);

      BitbucketBuildStatusHelper.notifyBuildStatus(
        step.getCredentials(build),
        step.getBitbucketHost(),
        true,
        build,
        taskListener,
        buildStatus,
        repoSlug,
        commitId
      );

      return null;
    }
  }
}
