/*
 * Copyright 2013-2022 the original author or authors.
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

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [PropagationContextElement].
 *
 * @author Brian Clozel
 */
internal class PropagationContextElementTests {

    val observationRegistry = ObservationRegistry.create()

    @Test
    fun `should propagate Reactor context as thread locals in coroutine`(): Unit = runBlocking {
        observationRegistry.observationConfig().observationHandler { true }
        val observation = Observation.start("test", observationRegistry)
        val currentObservation = mono {
            mono(PropagationContextElement(coroutineContext)) {
                observationRegistry.currentObservation
            }.block()
        }
            .contextWrite({ it.put(ObservationThreadLocalAccessor.KEY, observation) })
            .block()

        assertThat(currentObservation).isNotNull
    }

    @Test
    fun `should propagate thread locals in coroutine`(): Unit = runBlocking {
        observationRegistry.observationConfig().observationHandler { true }
        val observation = Observation.start("test", observationRegistry)

        val scope = observation.openScope()
        val currentObservation = mono(PropagationContextElement(coroutineContext)) {
            observationRegistry.currentObservation
        }.block()
        scope.close()
        assertThat(currentObservation).isNotNull
    }
}
