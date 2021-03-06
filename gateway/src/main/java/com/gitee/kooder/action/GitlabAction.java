/**
 * Copyright (c) 2021, OSChina (oschina.net@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitee.kooder.action;

import com.gitee.kooder.models.CodeRepository;
import com.gitee.kooder.core.Constants;
import com.gitee.kooder.core.KooderConfig;
import com.gitee.kooder.indexer.Gitlab;
import com.gitee.kooder.models.Issue;
import com.gitee.kooder.models.Repository;
import com.gitee.kooder.queue.QueueTask;
import com.gitee.kooder.server.Action;
import com.gitee.kooder.webhook.GitlabSystemHookManager;
import com.gitee.kooder.webhook.GitlabWebhookManager;
import io.vertx.ext.web.RoutingContext;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.systemhooks.*;
import org.gitlab4j.api.webhook.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle gitlab webhook
 * http://localhost:8080/gitlab/system   System hook  (new project/project updated etc)
 * http://localhost:8080/gitlab/project  Project hook (issue\push\pr etc)
 *
 * @author Winter Lau<javayou@gmail.com>
 */
public class GitlabAction implements Action {

    private final static Logger log = LoggerFactory.getLogger(GitlabAction.class);

    private String secret_token = KooderConfig.getProperty("gitlab.secret_token", "gsearch");

    /**
     * handle system webhook from gitlab
     * @param context
     * @throws GitLabApiException
     */
    public void system(RoutingContext context) throws GitLabApiException {
        GitlabSystemHookManager hookMgr = new GitlabSystemHookManager(secret_token){
            @Override
            protected void fireProjectEvent(ProjectSystemHookEvent event) {
                switch(event.getEventName()) {
                    case GitlabProjectEvent.E_PROJECT_CREATE:
                        //get project object
                        Project p = getProject(event.getProjectId());
                        if(p != null) {
                            Repository repo = new Repository(p);
                            QueueTask.add(Constants.TYPE_REPOSITORY, repo);
                        }
                        break;
                    case GitlabProjectEvent.E_PROJECT_DESTROY:
                        Repository repo = new Repository();
                        repo.setId(event.getProjectId());
                        QueueTask.delete(Constants.TYPE_REPOSITORY, repo);
                        break;
                    default:
                        p = getProject(event.getProjectId());
                        if(p != null) {
                            repo = new Repository(p);
                            QueueTask.update(Constants.TYPE_REPOSITORY, repo);
                        }
                }
            }

            @Override
            protected void firePushEvent(PushSystemHookEvent event) {
                this.fireCodeUpdate(event.getProjectId());
            }

            private void fireCodeUpdate(int pid) {
                Project p = getProject(pid);
                if(p != null) {
                    Repository repo = new Repository(p);
                    CodeRepository coder = new CodeRepository(repo);
                    coder.setScm(CodeRepository.SCM_GIT);
                    coder.setVender(Constants.GITLAB);
                    QueueTask.add(Constants.TYPE_CODE, coder); //update source code indexes
                }
            }
        };
        hookMgr.handleEvent(context);
    }

    /**
     * handle project webhook from gitlab
     * @param context
     * @throws GitLabApiException
     */
    public void project(RoutingContext context) throws GitLabApiException {
        GitlabWebhookManager hookMgr = new GitlabWebhookManager(this.secret_token){
            @Override
            protected void fireIssueEvent(IssueEvent e) {
                Issue issue = new Issue(e);
                QueueTask.update(Constants.TYPE_ISSUE, issue);
            }
        };
        hookMgr.handleEvent(context);
    }

    /**
     * Read project detail via gitlab api
     * @param id
     * @return
     */
    private Project getProject(int id) {
        try {
            return Gitlab.INSTANCE.getProjectApi().getProject(id);
        } catch (GitLabApiException e) {
            log.error("Failed to read project object, id = " + id, e);
        }
        return null;
    }

    /**
     * Gitlab repositories events definition
     */
    class GitlabProjectEvent {

        public final static String E_PROJECT_CREATE     = "project_create";
        public final static String E_PROJECT_DESTROY    = "project_destroy";
        public final static String E_PROJECT_RENAME     = "project_rename";
        public final static String E_PROJECT_TRANSFER   = "project_transfer";
        public final static String E_PROJECT_UPDATE     = "project_update";

    }

}

