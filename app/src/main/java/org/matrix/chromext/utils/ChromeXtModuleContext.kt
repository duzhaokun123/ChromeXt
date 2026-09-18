package org.matrix.chromext.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.view.View
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.matcher.ElementMatchers
import org.matrix.chromext.Chrome
import java.lang.reflect.Method


/**
 * a wrapper to make dynamic load id resource possible
 *
 * ```kotlin
 * val id = ChromeXtModuleContext.addStringRes("some string")
 *
 * // some code need a context to load resource
 * context = ChromeXtModuleContext.instance()
 * context.getString(id)
 *
 * // remeber to clean up after use
 * ChromeXtModuleContext.removeStringRes(id)
 * ```
 */
@SuppressLint("StaticFieldLeak")
object ChromeXtModuleContext {
  private var context: Context? = null
  private var resources: Resources? = null
  private val strategy by lazy {
    AndroidClassLoadingStrategy.Wrapping(Chrome.getContext().getDir("generated", Context.MODE_PRIVATE))
  }
  private val byteBuddy by lazy { ByteBuddy() }

  private val stringRes = mutableSetOf<Pair<Int, String>>()

  private fun createContext() {
    context = byteBuddy
      .subclass(Context::class.java)
      .method(ElementMatchers.isMethod())
      .intercept(MethodDelegation.to(object {
        @RuntimeType
        fun intercept( @Origin method: Method, @AllArguments args: Array<Any?>): Any? {
//          Log.d(method, args)
          return when (method.name) {
            "getResources" -> {
              if (resources == null) {
                createResources()
              }
              resources
            }
            else -> method.invoke(Chrome.getContext(), *args)
          }
        }
      }))
      .make()
      .load(this::class.java.classLoader, strategy)
      .loaded.newInstance()
  }

  private fun createResources() {
    resources = byteBuddy
      .subclass(Resources::class.java)
      .method(ElementMatchers.isMethod())
      .intercept(MethodDelegation.to(object {
        @RuntimeType
        fun intercept( @Origin method: Method, @AllArguments args: Array<Any?>): Any? {
//          Log.d(method, args)
          when (method.name) {
            "getString" -> {
              stringRes.find { (id, _) ->
                id == args[0]
              }?.let { (_, res) ->
                return res
              }
            }
          }
          return method.invoke(Chrome.getContext().resources, *args)
        }
      }))
      .make()
      .load(this::class.java.classLoader, strategy)
      .loaded.let {
        // don't use newInstance as sdk say:
        // private Resources() {...}
        Unsafe.instance.allocateInstance(it)
      }
  }

  fun instance(): Context {
    if (context == null) {
      createContext()
    }
    return context!!
  }

  fun addStringRes(res: String): Int {
    val id = View.generateViewId()
    stringRes.add(id to res)
    return id
  }

  fun removeStringRes(id: Int) {
    stringRes.removeIf { res -> res.first == id }
  }
}