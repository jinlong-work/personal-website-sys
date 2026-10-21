package com.jinlong.system.domain.resume;

import java.util.List;

public final class ResumeRequests {
    private ResumeRequests() { }
    public record Basic(String name, String latinName, String title, String role, String tagline,
            String taglineAccent, String intro, String about, String hometown, String location,
            String email, String phone, String availability) { }
    public record Education(String school, String major, String degree, String start, String end, String description) { }
    public record Experience(String company, String role, String start, String end, String description) { }
    public record Skill(String name, List<String> items, String description) { }
    public record Content(Basic basic, List<Education> education, List<Experience> experience, List<Skill> skills) { }
    public record SaveResume(Long version, Content zh, Content en) { }
}
