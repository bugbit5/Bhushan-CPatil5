package com.gitee.search.action;

import com.gitee.search.queue.QueueFactory;
import com.gitee.search.queue.QueueTask;
import io.netty.handler.codec.http.HttpResponseStatus;

import static com.gitee.search.action.ActionUtils.getParam;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 索引的维护
 * @author Winter Lau<javayou@gmail.com>
 */
public class IndexAction {

    /**
     * 添加索引
     * @param params
     * @param body
     * @return
     */
    public static String add(Map<String, List<String>> params, StringBuilder body) throws ActionException {
        pushTask(QueueTask.ACTION_ADD, params, body);
        return null;
    }

    /**
     * 修改索引
     * @param params
     * @param body
     * @return
     */
    public static String update(Map<String, List<String>> params, StringBuilder body) throws ActionException {
        pushTask(QueueTask.ACTION_UPDATE, params, body);
        return null;
    }

    /**
     * 删除索引
     * @param params
     * @param body
     * @return
     */
    public static String delete(Map<String, List<String>> params, StringBuilder body) throws ActionException {
        pushTask(QueueTask.ACTION_DELETE, params, body);
        return null;
    }

    private static void pushTask(String action, Map<String, List<String>> params, StringBuilder body) throws ActionException {
        QueueTask task = new QueueTask();
        task.setAction(action);
        task.setType(parseType(params));
        task.setBody(body.toString());
        if(task.check())
            QueueFactory.getProvider().push(Arrays.asList(task));
        else
            throw new ActionException(HttpResponseStatus.NOT_ACCEPTABLE);
    }

    /**
     * 从参数中解析对象类型字段，并判断值是否有效
     * @param params
     * @return
     * @throws ActionException
     */
    private static String parseType(Map<String, List<String>> params) throws ActionException {
        try {
            String type = getParam(params, "type");
            if(!QueueTask.isAvailType(type))
                throw new IllegalArgumentException(type);
            return type.toLowerCase();
        }catch(Exception e) {
            throw new ActionException(HttpResponseStatus.BAD_REQUEST);
        }
    }

}
