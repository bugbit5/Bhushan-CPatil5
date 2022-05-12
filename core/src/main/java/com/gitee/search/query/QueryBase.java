package com.gitee.search.query;

import com.gitee.search.core.AnalyzerFactory;
import com.gitee.search.index.IndexManager;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 搜索基类
 * @author Winter Lau<javayou@gmail.com>
 */
public abstract class QueryBase implements IQuery {

    protected String searchKey;
    protected boolean parseSearchKey = false;
    protected String sort;
    protected int page = 1;
    protected int pageSize = 20;
    protected Map<String, String[]> facets = new HashMap();

    /**
     * 搜索
     * @return
     * @throws IOException
     */
    @Override
    public String search() throws IOException {
        Query query = buildQuery();
        Sort nSort = buildSort();
        return IndexManager.search(type(), query, facets, nSort, page, pageSize);
    }

    /**
     * 构建查询对象
     * @return
     */
    protected abstract Query buildQuery() ;

    /**
     * 构建排序对象
     * @return
     */
    protected abstract Sort buildSort();

    /**
     * 搜索关键字
     * @param key
     */
    @Override
    public IQuery setSearchKey(String key) {
        this.searchKey = key;
        return this;
    }

    public IQuery setParseSearchKey(boolean parseSearchKey) {
        this.parseSearchKey = parseSearchKey;
        return this;
    }

    /**
     * 排序方法
     * @param sort
     * @return
     */
    @Override
    public IQuery setSort(String sort) {
        this.sort = sort;
        return this;
    }

    /**
     * 页码
     * @param page
     * @return
     */
    @Override
    public IQuery setPage(int page) {
        this.page = page;
        return this;
    }

    /**
     * 页大小
     * @param pageSize
     * @return
     */
    @Override
    public IQuery setPageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /**
     * 添加扩展属性
     * @param name
     * @param value
     * @return
     */
    public IQuery addFacets(String name, String value) {
        if(StringUtils.isBlank(value))
            return this;
        String[] values = facets.get(name);
        if(values == null)
            facets.put(name, new String[]{value});
        else {
            String[] newValues = new String[values.length + 1];
            System.arraycopy(values, 0, newValues, 0, values.length);
            newValues[values.length] = value;
            facets.put(name, newValues);
        }
        return this;
    }

    /**
     * 获取扩展属性
     *
     * @return
     */
    @Override
    public Map<String, String[]> getFacets() {
        return facets;
    }

    /**
     * Build a query for one document field with boost
     * @param field
     * @param q
     * @param boost
     * @return
     */
    protected BoostQuery makeBoostQuery(String field, String q, float boost) {
        QueryParser parser = new QueryParser(field, getAnalyzer(false));
        parser.setDefaultOperator(QueryParser.Operator.AND);
        try {
            return new BoostQuery(parser.parse(q), boost);
        } catch (ParseException e) {
            throw new QueryException(String.format("Failed to build field query(%s,%s,%.2f)", field, q, boost), e);
        }
    }

    /**
     * 自定义分词器
     * @param forIndex
     * @return
     */
    protected Analyzer getAnalyzer(boolean forIndex) {
        return AnalyzerFactory.getInstance(false);
    }

}