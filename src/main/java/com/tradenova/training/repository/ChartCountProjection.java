package com.tradenova.training.repository;

/** Count grouped by an owned session chart; used by read-only history assembly. */
public interface ChartCountProjection {
    Long getChartId();
    Long getCount();
}
