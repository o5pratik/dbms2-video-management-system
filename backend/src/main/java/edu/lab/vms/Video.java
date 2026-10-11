package edu.lab.vms;

import java.time.LocalDate;

public record Video(long videoId, String title, long categoryId, String categoryName,
                    int duration, String creatorName, LocalDate uploadDate,
                    long views, String description) {}

