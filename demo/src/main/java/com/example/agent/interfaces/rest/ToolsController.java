package com.example.agent.interfaces.rest;

import com.example.agent.application.service.CustomerSessionQueryService;
import com.example.agent.application.service.AgentTimeContextService;
import com.example.agent.application.service.AnalysisJobService;
import com.example.agent.application.service.QueryHandleService;
import com.example.agent.application.service.ReportAssemblyService;
import com.example.agent.application.service.ToolRegistry;
import com.example.agent.domain.model.ToolValidationException;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.infrastructure.repository.SessionRepository;
import com.example.agent.infrastructure.tool.DateExpressionParser;
import com.example.agent.infrastructure.tool.SampleSessionItem;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryRequest;
import com.example.agent.interfaces.rest.dto.CustomerSessionAggregationSpec;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ToolsController exposes endpoints that mirror the tool functions used
 * by the planning agent. These endpoints may be called directly by
 * the ToolExecutor or externally for testing and demonstration.
 */
@RestController
@RequestMapping("/tools")
public class ToolsController {
    private final SessionRepository sessionRepository;
    private final CustomerSessionQueryService customerSessionQueryService;
    private final AgentTimeContextService agentTimeContextService;
    private final ReportAssemblyService reportAssemblyService;
    private final QueryHandleService queryHandleService;
    private final AnalysisJobService analysisJobService;
    private final ToolRegistry toolRegistry;

    public ToolsController(SessionRepository sessionRepository,
                           CustomerSessionQueryService customerSessionQueryService,
                           AgentTimeContextService agentTimeContextService,
                           ReportAssemblyService reportAssemblyService,
                           QueryHandleService queryHandleService,
                           AnalysisJobService analysisJobService,
                           ToolRegistry toolRegistry) {
        this.sessionRepository = sessionRepository;
        this.customerSessionQueryService = customerSessionQueryService;
        this.agentTimeContextService = agentTimeContextService;
        this.reportAssemblyService = reportAssemblyService;
        this.queryHandleService = queryHandleService;
        this.analysisJobService = analysisJobService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Samples sessions by keywords within a date range. Accepts a JSON
     * body with keywords, start_date, end_date and limit.
     *
     * @param request request containing tool parameters
     * @return list of session items
     */
    @PostMapping("/sample-sessions-by-keywords")
    public ResponseEntity<Map<String, Object>> sampleSessions(@RequestBody Map<String, Object> request) {
        List<String> keywords = new ArrayList<>();
        if (request.get("keywords") instanceof List) {
            keywords.addAll((List<String>) request.get("keywords"));
        }
        String startDate = (String) request.get("start_date");
        String endDate = (String) request.get("end_date");
        Integer limit = request.get("limit") != null ? ((Number) request.get("limit")).intValue() : 10;
        java.time.LocalDate start = DateExpressionParser.parseToDate(startDate);
        java.time.LocalDate end = DateExpressionParser.parseToDate(endDate);
        List<SampleSessionItem> result = sessionRepository.sampleSessionsByKeywords(keywords, start, end, limit);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("tool", "sample_sessions_by_keywords");
        response.put("status", "SUCCEEDED");
        response.put("data", result);
        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("probe_sample_size", result.size());
        artifacts.put("time_range", start + " ~ " + end);
        response.put("artifacts", artifacts);
        return ResponseEntity.ok(response);
    }

    /**
     * 分析模式查询：返回查询实例 ID，不返回全量数据。
     */
    @PostMapping("/query-sessions-handle")
    public ResponseEntity<Map<String, Object>> querySessionsHandle(@RequestBody Map<String, Object> request) {
        ToolDefinition definition = requireTool("query_sessions_handle");
        Map<String, Object> normalizedRequest = agentTimeContextService.normalizeArgs("", definition, request);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("tool", "query_sessions_handle");
        response.put("status", "SUCCEEDED");
        response.put("data", new HashMap<String, Object>());
        response.put("artifacts", queryHandleService.createHandle(normalizedRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * 启动异步分析任务：返回分析任务 ID。
     */
    @PostMapping("/analyze-keywords-async")
    public ResponseEntity<Map<String, Object>> analyzeKeywordsAsync(@RequestBody Map<String, Object> request) {
        Map<String, Object> artifacts = analysisJobService.createJob(
                request,
                stringValue(request.get("__session_id")),
                stringValue(request.get("__turn_id")),
                stringValue(request.get("__tool_call_id"))
        );
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("tool", "analyze_keywords_async");
        response.put("status", "PENDING");
        response.put("data", new HashMap<String, Object>());
        response.put("artifacts", artifacts);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/build-keyword-report")
    public ResponseEntity<Map<String, Object>> buildKeywordReport(@RequestBody Map<String, Object> request) {
        String reportId = "report-" + UUID.randomUUID().toString();
        Map<String, Object> resolvedInputs = request.get("resolved_report_inputs") instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) request.get("resolved_report_inputs"))
                : new LinkedHashMap<String, Object>();
        com.example.agent.domain.model.RenderedReport renderedReport = reportAssemblyService.assemble(
                String.valueOf(request.get("report_title")),
                request.get("report_blueprint"),
                resolvedInputs
        );
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("tool", "build_keyword_report");
        response.put("status", "SUCCEEDED");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("markdown", renderedReport.getMarkdown());
        data.put("sections", renderedReport.getSections());
        response.put("data", data);
        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("report_id", reportId);
        artifacts.put("report_markdown", renderedReport.getMarkdown());
        artifacts.put("report_blueprint", request.get("report_blueprint"));
        artifacts.put("report_summary", renderedReport.getSummary());
        response.put("artifacts", artifacts);
        return ResponseEntity.ok(response);
    }

    /**
     * 真实 MySQL 客服会话查询工具。
     */
    @PostMapping("/query-customer-sessions-mysql")
    public ResponseEntity<Map<String, Object>> queryCustomerSessionsMysql(@RequestBody Map<String, Object> request) {
        ToolDefinition definition = requireTool("query_customer_sessions_mysql");
        Map<String, Object> normalizedRequest = agentTimeContextService.normalizeArgs("", definition, request);
        CustomerSessionQueryRequest queryRequest = new CustomerSessionQueryRequest();
        if (normalizedRequest.get("keywords") instanceof List) {
            queryRequest.setKeywords(new ArrayList<String>((List<String>) normalizedRequest.get("keywords")));
        }
        queryRequest.setBusinessLine((String) normalizedRequest.get("business_line"));
        queryRequest.setChannel((String) normalizedRequest.get("channel"));
        queryRequest.setCustomerId((String) normalizedRequest.get("customer_id"));
        queryRequest.setStartTime((String) normalizedRequest.get("start_time"));
        queryRequest.setEndTime((String) normalizedRequest.get("end_time"));
        if (normalizedRequest.get("group_by") instanceof List) {
            queryRequest.setGroupBy(new ArrayList<String>((List<String>) normalizedRequest.get("group_by")));
        }
        if (normalizedRequest.get("aggregations") instanceof List) {
            List<CustomerSessionAggregationSpec> specs = new ArrayList<CustomerSessionAggregationSpec>();
            for (Object raw : (List<?>) normalizedRequest.get("aggregations")) {
                if (!(raw instanceof Map)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>((Map<String, Object>) raw);
                CustomerSessionAggregationSpec spec = new CustomerSessionAggregationSpec();
                spec.setFunction(stringValue(item.get("function")) != null ? stringValue(item.get("function")) : stringValue(item.get("type")));
                spec.setField(stringValue(item.get("field")));
                spec.setAlias(stringValue(item.get("alias")));
                specs.add(spec);
            }
            queryRequest.setAggregations(specs);
        }
        if (normalizedRequest.get("limit") instanceof Number) {
            queryRequest.setLimit(((Number) normalizedRequest.get("limit")).intValue());
        }
        CustomerSessionQueryResponse queryResponse;
        try {
            queryResponse = customerSessionQueryService.query(queryRequest);
        } catch (IllegalArgumentException e) {
            throw new ToolValidationException(
                    "QUERY_ARGUMENT_INVALID",
                    "query_customer_sessions_mysql validation failed: " + e.getMessage(),
                    "Use supported wide-table field names for group_by/aggregations, for example group_by=[team_name] and aggregation fields like id, total_call_time, call_time, logdate, session_time."
            );
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("tool", "query_customer_sessions_mysql");
        response.put("status", "SUCCEEDED");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("mode", queryResponse.getMode());
        data.put("records", queryResponse.getRecords());
        data.put("aggregate_rows", queryResponse.getAggregateRows());
        data.put("group_by", queryRequest.getGroupBy());
        response.put("data", data);
        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("query_total", queryResponse.getTotal());
        artifacts.put("source", "mysql.customer_service_sessions");
        artifacts.put("start_time", queryRequest.getStartTime());
        artifacts.put("end_time", queryRequest.getEndTime());
        artifacts.put("time_range", queryRequest.getStartTime() + " ~ " + queryRequest.getEndTime());
        artifacts.put("result_mode", queryResponse.getMode());
        artifacts.put("group_by", queryRequest.getGroupBy());
        response.put("artifacts", artifacts);
        return ResponseEntity.ok(response);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ToolDefinition requireTool(String toolId) {
        return toolRegistry.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolId));
    }
}
