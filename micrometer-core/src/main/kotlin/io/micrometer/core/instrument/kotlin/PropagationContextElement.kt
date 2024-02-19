/*
 * Copyright 2013-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.kotlin

import io.micrometer.context.ContextRegistry
import io.micrometer.context.ContextSnapshot
import io.micrometer.context.ContextSnapshotFactory
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.reactor.ReactorContext
import reactor.util.context.ContextView
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * [kotlin.coroutines.CoroutineContext.Element] implementation that provides support
 * for Context Propagation with [ContextSnapshotFactory] in Kotlin Coroutines.
 *
 * This [ThreadContextElement] implementation captures and reinstates [ContextSnapshot]
 * into thread context every time the coroutine with this element in the context
 * is resumed on a thread. This attempts to capture from the [ReactorContext] if it is
 * available, or from the current thread locals otherwise.
 *
 * To be effective, this needs to be instantiated and installed in the Coroutine Context
 * hierarchy, like the following:
 *
 * ```
 * suspend fun myfunction() {
 *   withContext(PropagationContextElement(coroutineContext)) {
 *     //..
 *   }
 * }
 * ```
 *
 * The `'org.jetbrains.kotlinx:kotlinx-coroutines-reactor'` dependency is required.
 *
 * @param context the current Coroutine Context
 * @property Key the key for this element in the [CoroutineContext]
 * @author Brian Clozel
 * @since 1.13.0
 */
class PropagationContextElement(private val context: CoroutineContext) : ThreadContextElement<ContextSnapshot.Scope>,
    AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<PropagationContextElement>

    override val key: CoroutineContext.Key<PropagationContextElement> get() = Key

    private val contextSnapshot: ContextSnapshot

    init {
        val contextView: ContextView? = context[ReactorContext]?.context
        val contextSnapshotFactory =
            ContextSnapshotFactory.builder().contextRegistry(ContextRegistry.getInstance()).build()
        if (contextView != null) {
            // capture from the Reactor context if available
            contextSnapshot = contextSnapshotFactory.captureFrom(contextView)
        } else {
            // capture from local Thread locals otherwise
            contextSnapshot = contextSnapshotFactory.captureAll()
        }
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: ContextSnapshot.Scope) {
        oldState.close()
    }

    override fun updateThreadContext(context: CoroutineContext): ContextSnapshot.Scope {
        return contextSnapshot.setThreadLocals()
    }
}
