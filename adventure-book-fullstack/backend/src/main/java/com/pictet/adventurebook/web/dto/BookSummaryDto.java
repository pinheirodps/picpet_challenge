package com.pictet.adventurebook.web.dto;

/**
 * What the library listing (Objective 1) shows for each book — no section or option
 * details, since the home page only needs card-level metadata. {@code sectionCount} is
 * the one derived figure: the suggested design shows a chapter count on each card, and a
 * book's section count is the closest thing the source data actually carries.
 */
public record BookSummaryDto(Long id, String title, String author, String difficulty, int sectionCount) {
}
