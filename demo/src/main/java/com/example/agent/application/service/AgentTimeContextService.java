package com.example.agent.application.service;

import com.example.agent.domain.model.TimeNormalizationMode;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.infrastructure.tool.DateExpressionParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentTimeContextService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern LAST_N_DAYS = Pattern.compile("(最近|近)(\\d+)(天|日)");

    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public AgentTimeContextService(@Value("${agent.runtime.timezone:Asia/Shanghai}") String timezone) {
        this(ZoneId.of(timezone), Clock.system(ZoneId.of(timezone)));
    }

    AgentTimeContextService(ZoneId zoneId, Clock clock) {
        this.zoneId = zoneId;
        this.clock = clock;
    }

    public Map<String, Object> buildTrustedTimeContext() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> timeContext = new HashMap<String, Object>();
        timeContext.put("timezone", zoneId.getId());
        timeContext.put("current_date", now.toLocalDate().format(DATE_FORMATTER));
        timeContext.put("current_datetime", now.format(DATETIME_FORMATTER));
        timeContext.put("current_time_window_hint", "Prefer relative time expressions like now-30d, now, today, this_month, last_month when calling query tools.");
        return timeContext;
    }

    public Map<String, Object> normalizeQueryTimeArgs(String userMessage, String toolName, Map<String, Object> args) {
        if ("sample_sessions_by_keywords".equals(toolName) || "query_sessions_handle".equals(toolName)) {
            ToolDefinition definition = new ToolDefinition();
            definition.setTimeNormalizationMode(TimeNormalizationMode.DATE_RANGE);
            return normalizeArgs(userMessage, definition, args);
        }
        if ("query_customer_sessions_mysql".equals(toolName)) {
            ToolDefinition definition = new ToolDefinition();
            definition.setTimeNormalizationMode(TimeNormalizationMode.DATETIME_RANGE);
            return normalizeArgs(userMessage, definition, args);
        }
        return normalizeArgs(userMessage, null, args);
    }

    public Map<String, Object> normalizeArgs(String userMessage, ToolDefinition definition, Map<String, Object> args) {
        Map<String, Object> normalized = new HashMap<String, Object>(args == null ? new HashMap<String, Object>() : args);
        LocalDateTime now = LocalDateTime.now(clock);

        TimeNormalizationMode mode = definition == null || definition.getTimeNormalizationMode() == null
                ? TimeNormalizationMode.NONE
                : definition.getTimeNormalizationMode();
        if (mode == TimeNormalizationMode.DATE_RANGE) {
            normalizeDateRange(userMessage, normalized, now.toLocalDate());
            return normalized;
        }
        if (mode == TimeNormalizationMode.DATETIME_RANGE) {
            normalizeDateTimeRange(userMessage, normalized, now);
            return normalized;
        }
        return normalized;
    }

    private void normalizeDateRange(String userMessage, Map<String, Object> args, LocalDate baseDate) {
        String inferredStart = inferRelativeStartDate(userMessage, baseDate);
        String inferredEnd = inferRelativeEndDate(userMessage, baseDate);

        if (inferredStart != null) {
            args.put("start_date", inferredStart);
        } else if (args.get("start_date") instanceof String) {
            args.put("start_date", normalizeDateValue((String) args.get("start_date"), baseDate));
        }

        if (inferredEnd != null) {
            args.put("end_date", inferredEnd);
        } else if (args.get("end_date") instanceof String) {
            args.put("end_date", normalizeDateValue((String) args.get("end_date"), baseDate));
        }
    }

    private void normalizeDateTimeRange(String userMessage, Map<String, Object> args, LocalDateTime now) {
        String inferredStart = inferRelativeStartDateTime(userMessage, now);
        String inferredEnd = inferRelativeEndDateTime(userMessage, now);

        if (inferredStart != null) {
            args.put("start_time", inferredStart);
        } else if (args.get("start_time") instanceof String) {
            args.put("start_time", normalizeDateTimeValue((String) args.get("start_time"), now, true));
        }

        if (inferredEnd != null) {
            args.put("end_time", inferredEnd);
        } else if (args.get("end_time") instanceof String) {
            args.put("end_time", normalizeDateTimeValue((String) args.get("end_time"), now, false));
        }
    }

    private String inferRelativeStartDate(String userMessage, LocalDate baseDate) {
        LocalDateRange range = inferDateRange(userMessage, baseDate);
        return range == null ? null : range.start.format(DATE_FORMATTER);
    }

    private String inferRelativeEndDate(String userMessage, LocalDate baseDate) {
        LocalDateRange range = inferDateRange(userMessage, baseDate);
        return range == null ? null : range.end.format(DATE_FORMATTER);
    }

    private String inferRelativeStartDateTime(String userMessage, LocalDateTime now) {
        LocalDateRange range = inferDateRange(userMessage, now.toLocalDate());
        if (range == null) {
            return null;
        }
        return LocalDateTime.of(range.start, LocalTime.MIN).format(DATETIME_FORMATTER);
    }

    private String inferRelativeEndDateTime(String userMessage, LocalDateTime now) {
        LocalDateRange range = inferDateRange(userMessage, now.toLocalDate());
        if (range == null) {
            return null;
        }
        LocalTime endOfDay = LocalTime.of(23, 59, 59);
        if (range.end.equals(now.toLocalDate())) {
            return now.format(DATETIME_FORMATTER);
        }
        return LocalDateTime.of(range.end, endOfDay).format(DATETIME_FORMATTER);
    }

    private String normalizeDateValue(String raw, LocalDate baseDate) {
        return DateExpressionParser.parseToDate(raw, baseDate).format(DATE_FORMATTER);
    }

    private String normalizeDateTimeValue(String raw, LocalDateTime baseDateTime, boolean startOfDay) {
        return DateExpressionParser.parseToDateTime(raw, baseDateTime, startOfDay).format(DATETIME_FORMATTER);
    }

    private LocalDateRange inferDateRange(String userMessage, LocalDate baseDate) {
        String text = userMessage == null ? "" : userMessage.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        Matcher lastNDays = LAST_N_DAYS.matcher(text);
        if (lastNDays.find()) {
            int days = Integer.parseInt(lastNDays.group(2));
            return new LocalDateRange(baseDate.minusDays(days), baseDate);
        }
        if (text.contains("本月")) {
            LocalDate start = baseDate.withDayOfMonth(1);
            return new LocalDateRange(start, baseDate);
        }
        if (text.contains("上个月")) {
            LocalDate previousMonth = baseDate.minusMonths(1).withDayOfMonth(1);
            LocalDate end = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth());
            return new LocalDateRange(previousMonth, end);
        }
        if (text.contains("最近一周")) {
            return new LocalDateRange(baseDate.minusDays(7), baseDate);
        }
        if (text.contains("最近一个月")) {
            return new LocalDateRange(baseDate.minusMonths(1), baseDate);
        }
        if (text.contains("最近一年")) {
            return new LocalDateRange(baseDate.minusYears(1), baseDate);
        }
        return null;
    }

    private static final class LocalDateRange {
        private final LocalDate start;
        private final LocalDate end;

        private LocalDateRange(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
    }
}
