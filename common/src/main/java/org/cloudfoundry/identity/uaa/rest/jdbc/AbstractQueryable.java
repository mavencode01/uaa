/*******************************************************************************
 *     Cloud Foundry 
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.rest.jdbc;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.identity.uaa.rest.Queryable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public abstract class AbstractQueryable<T> implements Queryable<T> {

    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcPagingListFactory pagingListFactory;

    private RowMapper<T> rowMapper;

    private final Log logger = LogFactory.getLog(getClass());

    private SearchQueryConverter queryConverter = new SimpleSearchQueryConverter();

    private int pageSize = 200;

    protected AbstractQueryable(JdbcTemplate jdbcTemplate, JdbcPagingListFactory pagingListFactory,
                    RowMapper<T> rowMapper) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.pagingListFactory = pagingListFactory;
        this.rowMapper = rowMapper;
    }

    public void setQueryConverter(SearchQueryConverter queryConverter) {
        this.queryConverter = queryConverter;
    }

    /**
     * The maximum number of items fetched from the database in one hit. If less
     * than or equal to zero, then there is no
     * limit.
     * 
     * @param pageSize the page size to use for backing queries (default 200)
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getPageSize() {
        return pageSize;
    }

    @Override
    public List<T> query(String filter) {
        return query(filter, null, true);
    }

    @Override
    public List<T> query(String filter, String sortBy, boolean ascending) {
        SearchQueryConverter.ProcessedFilter where = queryConverter.convert(filter, sortBy, ascending);
        logger.debug("Filtering groups with SQL: " + where);
        List<T> result;
        try {
            String completeSql = getBaseSqlQuery() + " where " + where.getSql();
            logger.debug("complete sql: " + completeSql + ", params: " + where.getParams());
            if (pageSize > 0 && pageSize < Integer.MAX_VALUE) {
                result = pagingListFactory.createJdbcPagingList(completeSql, where.getParams(), rowMapper, pageSize);
            }
            else {
                result = jdbcTemplate.query(completeSql, where.getParams(), rowMapper);
            }
            return result;
        } catch (DataAccessException e) {
            logger.debug("Filter '" + filter + "' generated invalid SQL", e);
            throw new IllegalArgumentException("Invalid filter: " + filter);
        }
    }

    protected abstract String getBaseSqlQuery();

}
