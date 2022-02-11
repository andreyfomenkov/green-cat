package ru.fomenkov.plugin.task

interface Task<T> {

    fun run(): T
}