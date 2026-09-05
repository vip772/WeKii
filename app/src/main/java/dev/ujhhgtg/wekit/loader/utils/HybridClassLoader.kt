package dev.ujhhgtg.wekit.loader.utils

import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object HybridClassLoader : ClassLoader(ClassLoaders.BOOT) {

    private val bootClassLoader = ClassLoaders.BOOT
    lateinit var moduleParentClassLoader: ClassLoader
    lateinit var moduleClassLoader: ClassLoader
    lateinit var hostClassLoader: ClassLoader
    val additionalLoaders = mutableListOf<ClassLoader>()

    private val moduleFindClassMethod: Method by lazy {
        ClassLoader::class.java.getDeclaredMethod("findClass", String::class.java).apply {
            isAccessible = true
        }
    }

    private const val PREFIX_BOOT = "BOOT."
    private const val PREFIX_MODULE = "MODULE."
    private const val PREFIX_HOST = "HOST."

    /**
     * BeanShell may ask for a Java nested type using source notation
     * (`Outer.Inner`), while the JVM class file is named `Outer$Inner`.
     * Try the normal name first and then progressively convert the rightmost
     * separators to `$`, without changing ordinary class resolution.
     */
    private fun <T> loadWithNestedFallback(name: String, loader: (String) -> T): T {
        var candidate = name
        var lastFailure: ClassNotFoundException? = null
        while (true) {
            try {
                return loader(candidate)
            } catch (e: ClassNotFoundException) {
                lastFailure = e
                val dot = candidate.lastIndexOf('.')
                if (dot < 0) throw lastFailure
                candidate = candidate.substring(0, dot) + '$' + candidate.substring(dot + 1)
            }
        }
    }

    override fun findClass(name: String): Class<*> {
        when {
            name.startsWith(PREFIX_BOOT) -> {
                return loadWithNestedFallback(name.removePrefix(PREFIX_BOOT)) { bootClassLoader.loadClass(it) }
            }
            name.startsWith(PREFIX_MODULE) -> {
                return loadModuleClass(name.removePrefix(PREFIX_MODULE))
            }
            name.startsWith(PREFIX_HOST) -> {
                if (::hostClassLoader.isInitialized) {
                    return loadWithNestedFallback(name.removePrefix(PREFIX_HOST)) { hostClassLoader.loadClass(it) }
                }
                throw ClassNotFoundException("Forced HOST route failed: hostClassLoader is not initialized. Class: $name")
            }
        }

        runCatching { return loadWithNestedFallback(name) { bootClassLoader.loadClass(it) } }

        if (::moduleParentClassLoader.isInitialized) {
            runCatching { return loadWithNestedFallback(name) { moduleParentClassLoader.loadClass(it) } }
        }

        if (::moduleClassLoader.isInitialized) {
            runCatching { return findModuleOwnClass(name) }
        }

        if (::hostClassLoader.isInitialized) {
            runCatching { return loadWithNestedFallback(name) { hostClassLoader.loadClass(it) } }
        }

        additionalLoaders.forEach {
            runCatching { return loadWithNestedFallback(name) { loaderName -> it.loadClass(loaderName) } }
        }

        throw ClassNotFoundException(name)
    }

    private fun loadModuleClass(name: String): Class<*> {
        if (::moduleClassLoader.isInitialized) {
            runCatching { return findModuleOwnClass(name) }
        }

        if (::moduleParentClassLoader.isInitialized) {
            runCatching { return loadWithNestedFallback(name) { moduleParentClassLoader.loadClass(it) } }
        }

        throw ClassNotFoundException("Forced MODULE route failed. Class: $name")
    }

    private fun findModuleOwnClass(name: String): Class<*> = loadWithNestedFallback(name) { candidate ->
        try {
            moduleFindClassMethod.invoke(moduleClassLoader, candidate) as Class<*>
        } catch (e: InvocationTargetException) {
            throw (e.targetException as? ClassNotFoundException) ?: e
        }
    }
}
