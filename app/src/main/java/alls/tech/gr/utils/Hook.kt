package alls.tech.gr.utils

import alls.tech.gr.GR
import alls.tech.gr.core.Config

abstract class Hook(
    val hookName: String,
    val hookDesc: String = "",
) {
    /**
     * Hook specific initialization.
     */
    open fun init() {}

    /**
     * Hook specific cleanup.
     */
    open fun cleanup() {}

    protected fun isHookEnabled(): Boolean {
        return Config.isHookEnabled(hookName)
    }

    protected fun findClass(name: String): Class<*> {
        return GR.loadClass(name)
    }

    protected fun getResource(name: String, type: String): Int {
        return GR.context.resources.getIdentifier(
            name, type, GR.context.packageName
        )
    }

    protected fun getAttribute(name: String): Int {
        return GR.context.resources.getIdentifier(name, "attr"
            , GR.context.packageName)
    }
}