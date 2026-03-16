package com.example.agent.infrastructure.tool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses absolute/relative date expressions for tool execution.
 *
 * Supported examples:
 * - 2025-01-31
 * - now / today
 * - yesterday / tomorrow
 * - now-30d / now+7d
 * - now-4w / now-1m / now-1y
 */
public final class DateExpressionParser {

    private static final Pattern RELATIVE_PATTERN = Pattern.compile("^now([+-])(\\d+)([dwmy])$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateExpressionParser() {
    }

    public static LocalDate parseToDate(String expression) {
        return parseToDate(expression, LocalDate.now());
    }

    public static LocalDate parseToDate(String expression, LocalDate baseDate) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("date expression is blank");
        }
        String text = expression.trim().toLowerCase(Locale.ROOT);
        if ("now".equals(text) || "today".equals(text)) {
            return baseDate;
        }
        if ("yesterday".equals(text)) {
            return baseDate.minusDays(1);
        }
        if ("tomorrow".equals(text)) {
            return baseDate.plusDays(1);
        }

        Matcher matcher = RELATIVE_PATTERN.matcher(text);
        if (matcher.matches()) {
            String sign = matcher.group(1);
            int amount = Integer.parseInt(matcher.group(2));
            String unit = matcher.group(3);
            int direction = "-".equals(sign) ? -1 : 1;
            switch (unit) {
                case "d":
                    return baseDate.plusDays((long) direction * amount);
                case "w":
                    return baseDate.plusWeeks((long) direction * amount);
                case "m":
                    return baseDate.plusMonths((long) direction * amount);
                case "y":
                    return baseDate.plusYears((long) direction * amount);
                default:
                    throw new IllegalArgumentException("unsupported relative date unit: " + unit);
            }
        }

        try {
            return LocalDate.parse(text, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("unsupported date expression: " + expression +
                    ", expected yyyy-MM-dd or relative like now-30d", e);
        }
    }

    public static LocalDateTime parseToDateTime(String expression, LocalDateTime baseDateTime, boolean startOfDay) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("date expression is blank");
        }
        String text = expression.trim();
        try {
            return LocalDateTime.parse(text, DATETIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Fall through to date-relative parsing.
        }

        String lowered = text.toLowerCase(Locale.ROOT);
        if ("now".equals(lowered)) {
            return baseDateTime;
        }
        LocalDate resolvedDate = parseToDate(lowered, baseDateTime.toLocalDate());
        return LocalDateTime.of(resolvedDate, startOfDay ? LocalTime.MIN : LocalTime.of(23, 59, 59));
    }
}
