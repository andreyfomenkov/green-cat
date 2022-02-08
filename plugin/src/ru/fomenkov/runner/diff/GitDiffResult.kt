package ru.fomenkov.runner.diff

data class GitDiffResult(
    val branch: String,
    val paths: Set<String>,
)