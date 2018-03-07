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

import brave.Tracing;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.ErrorParser;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration}
 * related to HTTP based communication.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@Configuration
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "spring.sleuth.http.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(TraceWebAutoConfiguration.class)
@EnableConfigurationProperties(SleuthHttpLegacyProperties.class)
public class TraceHttpAutoConfiguration {

	@Autowired HttpClientParser clientParser;
	@Autowired HttpServerParser serverParser;
	@Autowired @ClientSampler HttpSampler clientSampler;
	@Autowired(required = false) @ServerSampler HttpSampler serverSampler;

	@Bean
	@ConditionalOnMissingBean
	// NOTE: stable bean name as might be used outside sleuth
	HttpTracing httpTracing(
			Tracing tracing,
			SkipPatternProvider provider
	) {
		return HttpTracing.newBuilder(tracing)
				.clientParser(this.clientParser)
				.serverParser(this.serverParser)
				.clientSampler(this.clientSampler)
				.serverSampler(new CompositeServerSampler(this.serverSampler, provider))
				.build();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "true")
	HttpClientParser sleuthHttpClientParser(TraceKeys traceKeys) {
		return new SleuthHttpClientParser(traceKeys);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled",
			havingValue = "false", matchIfMissing = true)
	@ConditionalOnMissingBean
	HttpClientParser httpClientParser() {
		return new HttpClientParser();
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled", havingValue = "true")
	HttpServerParser sleuthHttpServerParser(TraceKeys traceKeys, ErrorParser errorParser) {
		return new SleuthHttpServerParser(traceKeys, errorParser);
	}

	@Bean
	@ConditionalOnProperty(name = "spring.sleuth.http.legacy.enabled",
			havingValue = "false", matchIfMissing = true)
	@ConditionalOnMissingBean
	HttpServerParser defaultHttpServerParser() {
		return new HttpServerParser();
	}

	@Bean
	@ConditionalOnMissingBean(name = "sleuthClientSampler")
	HttpSampler sleuthClientSampler() {
		return HttpSampler.TRACE_ID;
	}
}



class CompositeServerSampler extends HttpSampler {

	private final HttpSampler delegate;
	private final SkipPatternProvider provider;

	CompositeServerSampler(HttpSampler delegate, SkipPatternProvider provider) {
		this.delegate = delegate;
		this.provider = provider;
	}

	@Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
		Boolean sleuthSampler = trySleuthSampler(adapter, request);
		if (this.delegate == null) {
			return sleuthSampler;
		}
		// let's do AND of sleuth sampler and the delegate
		Boolean sample = this.delegate.trySample(adapter, request);
		if (!sample) {
			// if the decision was false then doesn't matter what was the sleuth one
			return sample;
		} else if (sample == null) {
			// if there was no sampling decision, delegate to sleuth one
			return sleuthSampler;
		}
		// if there was a decision from delegate combine it with sleuth
		// if sleuth was not defined then it should not affect the delegate one
		return sample && (sleuthSampler == null ? true : sleuthSampler);
	}

	private <Req> Boolean trySleuthSampler(HttpAdapter<Req, ?> adapter, Req request) {
		return new SleuthHttpSampler(this.provider).trySample(adapter, request);
	}
}