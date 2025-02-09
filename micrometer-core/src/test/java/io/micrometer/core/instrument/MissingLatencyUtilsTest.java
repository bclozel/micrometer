/*
 * Copyright 2022 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.testsupport.classpath.ClassPathExclusions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for demonstrating that a timer works without LatencyUtils dependency when using
 * the default pause detector.
 *
 * @author Johnny Lim
 */
@ClassPathExclusions("LatencyUtils-*.jar")
class MissingLatencyUtilsTest {

    @Disabled("See https://github.com/micrometer-metrics/micrometer/issues/3287")
    @Test
    void test() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        assertThatCode(() -> Timer.builder("my.timer").register(registry)).doesNotThrowAnyException();
    }

}
