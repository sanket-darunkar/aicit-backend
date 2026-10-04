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
    private String  category;
    private boolean isActive;

    public static CourseResponse from(Course c) {
        return CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .description(c.getDescription())
                .durationMonths(c.getDurationMonths())
                .category(c.getCategory())
                .isActive(c.isActive())
                .build();
    }
}
