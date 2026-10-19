package com.aicit.dto.response;
import com.aicit.entity.Course;
import lombok.Builder;
import lombok.Getter;
@Getter @Builder
public class CourseResponse {
    private Long    id;
    private String  name;
    private String  code;
    private String  description;
    private Integer durationMonths;
    private String  durationLabel;
    private Integer durationDays;
    /** Human-friendly duration for display: label when present, else "<n> Months". */
    private String  durationDisplay;
    private Integer sourceSrNo;
    private String  category;
    private boolean isActive;
    public static CourseResponse from(Course c) {
        String display = c.getDurationLabel();
        if (display == null || display.isBlank()) {
            display = (c.getDurationMonths() != null) ? c.getDurationMonths() + " Months" : null;
        }
        return CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .description(c.getDescription())
                .durationMonths(c.getDurationMonths())
                .durationLabel(c.getDurationLabel())
                .durationDays(c.getDurationDays())
                .durationDisplay(display)
                .sourceSrNo(c.getSourceSrNo())
                .category(c.getCategory())
                .isActive(c.isActive())
                .build();
    }
}
