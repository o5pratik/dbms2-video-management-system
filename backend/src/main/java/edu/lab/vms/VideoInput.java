package edu.lab.vms;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record VideoInput(
    @NotBlank(message="Title is required") @Size(max=150) String title,
    @NotNull(message="Category is required") @Min(value=1, message="Choose a category") Long categoryId,
    @NotNull(message="Duration is required") @Min(value=1, message="Duration must be at least 1 minute") Integer duration,
    @NotBlank(message="Creator is required") @Size(max=100) String creatorName,
    @NotNull(message="Upload date is required") LocalDate uploadDate,
    @NotNull(message="Views are required") @Min(value=0, message="Views cannot be negative") Long views,
    @Size(max=2000) String description
) {}

