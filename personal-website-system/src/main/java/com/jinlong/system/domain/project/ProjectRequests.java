package com.jinlong.system.domain.project;

import java.util.List;

public final class ProjectRequests {
    private ProjectRequests() {}
    public record Translation(String name, String type, String description, String category,
            String headline, String summary, List<String> technologies, List<String> achievements) {}
    public record SaveProject(String name, String type, String description, String category,
            String headline, String summary, List<String> technologies, List<String> achievements,
            List<String> images, Translation english, Boolean featured, String status, Integer sortOrder, Long version) {}
    public record ChangeStatus(String status, Long version) {}
}
