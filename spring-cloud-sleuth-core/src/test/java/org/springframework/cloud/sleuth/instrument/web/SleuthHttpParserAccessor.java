/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web;

import brave.ErrorParser;
import brave.http.HttpClientParser;
import brave.http.HttpServerParser;
import org.springframework.cloud.sleuth.TraceKeys;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public class SleuthHttpParserAccessor {
	public static HttpClientParser getClient(TraceKeys traceKeys) {
		return new SleuthHttpClientParser(traceKeys);
	}

	public static HttpServerParser getServer(TraceKeys traceKeys, ErrorParser errorParser) {
		return new SleuthHttpServerParser(traceKeys, errorParser);
	}
}
