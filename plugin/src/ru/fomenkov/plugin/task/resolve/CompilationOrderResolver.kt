package ru.fomenkov.plugin.task.resolve

class CompilationOrderResolver {

    /**
     * Determine modules' compilation order
     *
     * @param children module names to its children modules, including transitive
     * @return module names to its compilation order
     */
    fun getModulesCompilationOrder(children: Map<String, Set<String>>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val modules = children.toMutableMap()
        var order = 0

        fun isNextModuleForCompile(moduleName: String): Boolean {
            checkNotNull(modules[moduleName]) { "No module with name $moduleName" }
                .forEach { childModuleName ->
                    if (moduleName != childModuleName && modules.containsKey(childModuleName)) {
                        return false
                    }
                }
            return true
        }
        while (modules.isNotEmpty()) {
            val nextOrderModules = modules.filterKeys(::isNextModuleForCompile).keys
            check(nextOrderModules.isNotEmpty()) { "No filtered modules for compilation order $order" }
            result += nextOrderModules.associateWith { order }
            modules -= nextOrderModules
            order++
        }
        return result
    }
}