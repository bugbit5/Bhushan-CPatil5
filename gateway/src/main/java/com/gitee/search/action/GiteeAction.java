package com.gitee.search.action;

import com.gitee.search.server.Action;
import io.vertx.ext.web.RoutingContext;

/**
 * Handle Gitee webhook
 * http://localhost:8080/gitee   web hook  (new project/project updated etc)
 * @author Winter Lau<javayou@gmail.com>
 */
public class GiteeAction implements Action {

    /**
     * Gitee webhook handler
     * @param context
     */
    public void index(RoutingContext context) {

    }

}
