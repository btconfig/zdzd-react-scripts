package com.example.agent.infrastructure.repository;

import com.example.agent.domain.model.CustomerSessionRecord;
import com.example.agent.infrastructure.mapper.CustomerSessionMapper;
import com.example.agent.interfaces.rest.dto.CustomerSessionAggregationSpec;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryRequest;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CustomerSessionMysqlRepository {
    private static final Map<String, String> GROUPABLE_COLUMNS = new HashMap<String, String>();
    private static final Map<String, String> AGGREGATABLE_COLUMNS = new HashMap<String, String>();
    private static final Map<String, String> GROUPABLE_ALIASES = new HashMap<String, String>();
    private static final Map<String, String> AGGREGATABLE_ALIASES = new HashMap<String, String>();

    static {
        GROUPABLE_COLUMNS.put("team_name", "team_name");
        GROUPABLE_COLUMNS.put("group_name", "group_name");
        GROUPABLE_COLUMNS.put("agent_id", "agent_id");
        GROUPABLE_COLUMNS.put("agent_name", "agent_name");
        GROUPABLE_COLUMNS.put("biz_type", "biz_type");
        GROUPABLE_COLUMNS.put("biz_type_name", "biz_type_name");
        GROUPABLE_COLUMNS.put("device_in", "device_in");
        GROUPABLE_COLUMNS.put("data_source", "data_source");
        GROUPABLE_COLUMNS.put("call_type", "call_type");
        GROUPABLE_COLUMNS.put("diff", "diff");
        GROUPABLE_COLUMNS.put("session_date", "DATE(session_time)");

        AGGREGATABLE_COLUMNS.put("id", "id");
        AGGREGATABLE_COLUMNS.put("transfer_count", "transfer_count");
        AGGREGATABLE_COLUMNS.put("total_call_time", "total_call_time");
        AGGREGATABLE_COLUMNS.put("call_time", "call_time");
        AGGREGATABLE_COLUMNS.put("logdate", "logdate");
        AGGREGATABLE_COLUMNS.put("session_time", "session_time");

        GROUPABLE_ALIASES.put("team", "team_name");
        GROUPABLE_ALIASES.put("group", "group_name");
        GROUPABLE_ALIASES.put("agent", "agent_name");
        GROUPABLE_ALIASES.put("agentid", "agent_id");
        GROUPABLE_ALIASES.put("agent_id", "agent_id");
        GROUPABLE_ALIASES.put("agentname", "agent_name");
        GROUPABLE_ALIASES.put("agent_name", "agent_name");
        GROUPABLE_ALIASES.put("biztype", "biz_type");
        GROUPABLE_ALIASES.put("business_type", "biz_type");
        GROUPABLE_ALIASES.put("business_line", "biz_type_name");
        GROUPABLE_ALIASES.put("biz_type_name", "biz_type_name");
        GROUPABLE_ALIASES.put("source", "data_source");
        GROUPABLE_ALIASES.put("date", "session_date");
        GROUPABLE_ALIASES.put("session_date", "session_date");

        AGGREGATABLE_ALIASES.put("session_id", "id");
        AGGREGATABLE_ALIASES.put("sessionid", "id");
        AGGREGATABLE_ALIASES.put("count", "id");
        AGGREGATABLE_ALIASES.put("duration", "total_call_time");
        AGGREGATABLE_ALIASES.put("total_duration", "total_call_time");
        AGGREGATABLE_ALIASES.put("call_duration", "call_time");
        AGGREGATABLE_ALIASES.put("timestamp", "logdate");
    }

    private final CustomerSessionMapper mapper;

    public CustomerSessionMysqlRepository(CustomerSessionMapper mapper) {
        this.mapper = mapper;
    }

    public List<CustomerSessionRecord> query(CustomerSessionQueryRequest request) {
        normalize(request);
        request.setLimit(resolveLimit(request.getLimit()));
        return mapper.query(request);
    }

    public List<Map<String, Object>> queryAggregates(CustomerSessionQueryRequest request) {
        normalize(request);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("keywords", request.getKeywords());
        params.put("businessLine", request.getBusinessLine());
        params.put("channel", request.getChannel());
        params.put("customerId", request.getCustomerId());
        params.put("startTime", request.getStartTime());
        params.put("endTime", request.getEndTime());
        params.put("limit", resolveLimit(request.getLimit()));
        params.put("groupBySelectSql", buildGroupBySelectSql(request));
        params.put("aggregationSelectSql", buildAggregationSelectSql(request));
        params.put("groupBySql", buildGroupBySql(request));
        params.put("orderBySql", buildOrderBySql(request));
        return mapper.queryAggregates(params);
    }

    public List<CustomerSessionRecord> queryForHandle(CustomerSessionQueryRequest request, int maxRows) {
        normalize(request);
        request.setLimit(resolveHandleLimit(maxRows));
        return mapper.query(request);
    }

    private void normalize(CustomerSessionQueryRequest request) {
        if (request.getBusinessLine() != null) {
            request.setBusinessLine(request.getBusinessLine().trim());
        }
        if (request.getChannel() != null) {
            request.setChannel(request.getChannel().trim());
        }
        if (request.getCustomerId() != null) {
            request.setCustomerId(request.getCustomerId().trim());
        }
        if (request.getStartTime() != null) {
            request.setStartTime(request.getStartTime().trim());
        }
        if (request.getEndTime() != null) {
            request.setEndTime(request.getEndTime().trim());
        }
        if (request.getGroupBy() != null && !request.getGroupBy().isEmpty()) {
            for (int i = 0; i < request.getGroupBy().size(); i++) {
                request.getGroupBy().set(i, normalizeGroupField(request.getGroupBy().get(i)));
            }
        }
        if (request.getAggregations() != null) {
            for (CustomerSessionAggregationSpec spec : request.getAggregations()) {
                if (spec == null) {
                    continue;
                }
                if (spec.getFunction() != null) {
                    spec.setFunction(spec.getFunction().trim().toUpperCase());
                }
                if (spec.getField() != null) {
                    spec.setField(normalizeAggregationField(spec.getField()));
                }
            }
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 200);
    }

    private int resolveHandleLimit(int limit) {
        if (limit <= 0) {
            return 10000;
        }
        return Math.min(limit, 50000);
    }

    private String buildGroupBySelectSql(CustomerSessionQueryRequest request) {
        if (request.getGroupBy() == null || request.getGroupBy().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String field : request.getGroupBy()) {
            String expression = resolveGroupableColumn(field);
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(expression).append(" AS ").append(safeAlias(field));
        }
        return builder.toString();
    }

    private String buildGroupBySql(CustomerSessionQueryRequest request) {
        if (request.getGroupBy() == null || request.getGroupBy().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String field : request.getGroupBy()) {
            String expression = resolveGroupableColumn(field);
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(expression);
        }
        return builder.toString();
    }

    private String buildAggregationSelectSql(CustomerSessionQueryRequest request) {
        if (request.getAggregations() == null || request.getAggregations().isEmpty()) {
            throw new IllegalArgumentException("aggregations must not be empty for aggregate query");
        }
        StringBuilder builder = new StringBuilder();
        for (CustomerSessionAggregationSpec spec : request.getAggregations()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(buildAggregationExpression(spec));
        }
        return builder.toString();
    }

    private String buildOrderBySql(CustomerSessionQueryRequest request) {
        if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
            CustomerSessionAggregationSpec first = request.getAggregations().get(0);
            return safeAlias(resolveAggregationAlias(first)) + " DESC";
        }
        if (request.getGroupBy() != null && !request.getGroupBy().isEmpty()) {
            return safeAlias(request.getGroupBy().get(0)) + " ASC";
        }
        return "1";
    }

    private String buildAggregationExpression(CustomerSessionAggregationSpec spec) {
        String function = spec == null || spec.getFunction() == null ? "" : spec.getFunction().trim().toUpperCase();
        String field = spec == null ? null : spec.getField();
        String alias = safeAlias(resolveAggregationAlias(spec));
        if ("COUNT".equals(function)) {
            if (field == null || field.trim().isEmpty() || "*".equals(field.trim())) {
                return "COUNT(*) AS " + alias;
            }
            return "COUNT(" + resolveAggregatableColumn(field) + ") AS " + alias;
        }
        if ("MAX".equals(function) || "MIN".equals(function) || "SUM".equals(function) || "AVG".equals(function)) {
            return function + "(" + resolveAggregatableColumn(field) + ") AS " + alias;
        }
        throw new IllegalArgumentException("unsupported aggregation function: " + function);
    }

    private String resolveAggregationAlias(CustomerSessionAggregationSpec spec) {
        if (spec != null && spec.getAlias() != null && !spec.getAlias().trim().isEmpty()) {
            return spec.getAlias().trim();
        }
        String function = spec == null || spec.getFunction() == null ? "agg" : spec.getFunction().trim().toLowerCase();
        String field = spec == null || spec.getField() == null || spec.getField().trim().isEmpty() ? "all" : spec.getField().trim().toLowerCase();
        return function + "_" + field.replaceAll("[^a-z0-9_]", "_");
    }

    private String resolveGroupableColumn(String field) {
        String key = normalizeGroupField(field);
        String column = GROUPABLE_COLUMNS.get(key);
        if (column == null) {
            throw new IllegalArgumentException("unsupported group_by field: " + field + "; supported fields: " + supportedGroupByFields());
        }
        return column;
    }

    private String resolveAggregatableColumn(String field) {
        String key = normalizeAggregationField(field);
        String column = AGGREGATABLE_COLUMNS.get(key);
        if (column == null) {
            throw new IllegalArgumentException("unsupported aggregation field: " + field + "; supported fields: " + supportedAggregationFields());
        }
        return column;
    }

    private String normalizeGroupField(String field) {
        String key = canonicalKey(field);
        if (GROUPABLE_COLUMNS.containsKey(key)) {
            return key;
        }
        String alias = GROUPABLE_ALIASES.get(key);
        return alias == null ? key : alias;
    }

    private String normalizeAggregationField(String field) {
        String key = canonicalKey(field);
        if (AGGREGATABLE_COLUMNS.containsKey(key) || "*".equals(key)) {
            return key;
        }
        String alias = AGGREGATABLE_ALIASES.get(key);
        return alias == null ? key : alias;
    }

    private String canonicalKey(String field) {
        return field == null ? "" : field.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    }

    private String supportedGroupByFields() {
        return String.join(", ", GROUPABLE_COLUMNS.keySet());
    }

    private String supportedAggregationFields() {
        return String.join(", ", AGGREGATABLE_COLUMNS.keySet());
    }

    private String safeAlias(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "value";
        }
        return raw.trim().replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
