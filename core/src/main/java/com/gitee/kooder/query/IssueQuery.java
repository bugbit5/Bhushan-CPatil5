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
package com.gitee.kooder.query;

import com.gitee.kooder.core.AnalyzerFactory;
import com.gitee.kooder.core.Constants;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;

/**
 * Issue 搜索
 * @author Winter Lau<javayou@gmail.com>
 */
public class IssueQuery extends QueryBase {
    /**
     * 索引类型
     *
     * @return
     */
    @Override
    public String type() {
        return Constants.TYPE_ISSUE;
    }

    /**
     * Last issue based on created_at field
     * Because gitlab issue api doesn't support filter by id
     * @return
     */
    @Override
    protected Sort getLastestObjectSort() {
        return new Sort(new SortField(Constants.FIELD_CREATED_AT, SortField.Type.LONG, true));
    }

    /**
     * 构建查询对象
     *
     * @return
     */
    @Override
    protected Query buildUserQuery() {
        if(parseSearchKey) {
            QueryParser parser = new QueryParser("issue", AnalyzerFactory.getInstance(false));
            try {
                return parser.parse(searchKey);
            } catch (ParseException e) {
                throw new QueryException("Failed to parse \""+searchKey+"\"", e);
            }
        }
        String q = QueryParser.escape(searchKey);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        //filter
        //search
        BooleanQuery.Builder qbuilder = new BooleanQuery.Builder();
        qbuilder.add(makeBoostQuery(Constants.FIELD_IDENT, q, 100.0f), BooleanClause.Occur.SHOULD);
        qbuilder.add(makeBoostQuery(Constants.FIELD_TITLE, q, 10.0f), BooleanClause.Occur.SHOULD);
        qbuilder.add(makeBoostQuery(Constants.FIELD_TAGS, q, 1.0f), BooleanClause.Occur.SHOULD);
        qbuilder.add(makeBoostQuery(Constants.FIELD_DESC, q, 1.0f), BooleanClause.Occur.SHOULD);
        qbuilder.setMinimumNumberShouldMatch(1);

        builder.add(qbuilder.build(), BooleanClause.Occur.MUST);

        return builder.build();
    }

    /**
     * 对搜索加权
     * @param field
     * @param q
     * @param boost
     * @return
     */
    @Override
    protected BoostQuery makeBoostQuery(String field, String q, float boost) {
        if("ident".equals(field))
            return new BoostQuery(new TermQuery(new Term(Constants.FIELD_IDENT, q)), boost);
        return super.makeBoostQuery(field, q, boost);
    }

    /**
     * 构建排序对象
     *
     * @return
     */
    @Override
    protected Sort buildSort() {
        if("create".equals(sort))
            return new Sort(new SortedNumericSortField(Constants.FIELD_CREATED_AT, SortField.Type.LONG, true));
        if("update".equals(sort))
            return new Sort(new SortedNumericSortField(Constants.FIELD_UPDATED_AT, SortField.Type.LONG, true));
        return Sort.RELEVANCE;
    }

}
