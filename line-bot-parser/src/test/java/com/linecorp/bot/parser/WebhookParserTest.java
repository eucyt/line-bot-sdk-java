/*
 * Copyright 2023 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.bot.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linecorp.bot.webhook.model.CallbackRequest;
import com.linecorp.bot.webhook.model.Event;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;

@ExtendWith(MockitoExtension.class)
public class WebhookParserTest {
    @Mock
    private final SignatureValidator signatureValidator = new MockSignatureValidator();

    static class MockSignatureValidator implements SignatureValidator {
        @Override
        public boolean validateSignature(byte[] content, String headerSignature) {
            return false;
        }
    }

    private WebhookParser parser;

    @BeforeEach
    public void before() {
        parser = new WebhookParser(
                signatureValidator,
                FixedSkipSignatureVerificationSupplier.of(false));
    }

    @Test
    public void testMissingHeader() {
        assertThatThrownBy(() -> parser.handle("", "".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(WebhookParseException.class)
                .hasMessage("Missing 'X-Line-Signature' header");
    }

    @Test
    public void testInvalidSignature() {
        assertThatThrownBy(
                () -> parser.handle("SSSSIGNATURE", "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(WebhookParseException.class)
                .hasMessage("Invalid API signature");
    }

    @Test
    public void testNullRequest() {
        final String signature = "SSSSIGNATURE";
        final byte[] nullContent = "null".getBytes(StandardCharsets.UTF_8);

        when(signatureValidator.validateSignature(nullContent, signature)).thenReturn(true);

        assertThatThrownBy(() -> parser.handle(signature, nullContent))
                .isInstanceOf(WebhookParseException.class)
                .hasMessage("Invalid content");
    }

    @Test
    public void testCallRequest() throws Exception {
        final InputStream resource = getClass().getClassLoader().getResourceAsStream(
                "callback-request.json");
        final byte[] payload = resource.readAllBytes();

        when(signatureValidator.validateSignature(payload, "SSSSIGNATURE")).thenReturn(true);

        final CallbackRequest callbackRequest = parser.handle("SSSSIGNATURE", payload);

        assertThat(callbackRequest).isNotNull();

        final List<Event> result = callbackRequest.events();

        @SuppressWarnings("rawtypes")
        final MessageEvent messageEvent = (MessageEvent) result.get(0);
        final TextMessageContent text = (TextMessageContent) messageEvent.message();
        assertThat(text.text()).isEqualTo("Hello, world");

        final String followedUserId = messageEvent.source().userId();
        assertThat(followedUserId).isEqualTo("u206d25c2ea6bd87c17655609a1c37cb8");
        assertThat(messageEvent.timestamp()).isEqualTo(
                Instant.parse("2016-05-07T13:57:59.859Z").toEpochMilli());
    }

    @Test
    public void testSkipSignatureVerification() throws Exception {
        final InputStream resource = getClass().getClassLoader().getResourceAsStream(
                "callback-request.json");
        final byte[] payload = resource.readAllBytes();

        final var parser = new WebhookParser(
                signatureValidator,
                FixedSkipSignatureVerificationSupplier.of(true));

        // assert no interaction with signatureValidator
        verify(signatureValidator, never()).validateSignature(payload, "SSSSIGNATURE");

        final CallbackRequest callbackRequest = parser.handle("SSSSIGNATURE", payload);

        assertThat(callbackRequest).isNotNull();

        final List<Event> result = callbackRequest.events();

        @SuppressWarnings("rawtypes")
        final MessageEvent messageEvent = (MessageEvent) result.get(0);
        final TextMessageContent text = (TextMessageContent) messageEvent.message();
        assertThat(text.text()).isEqualTo("Hello, world");

        final String followedUserId = messageEvent.source().userId();
        assertThat(followedUserId).isEqualTo("u206d25c2ea6bd87c17655609a1c37cb8");
        assertThat(messageEvent.timestamp()).isEqualTo(
                Instant.parse("2016-05-07T13:57:59.859Z").toEpochMilli());
    }

    @Test
    public void testWithoutSkipSignatureVerificationSupplierInConstructor() throws Exception {
        final InputStream resource = getClass().getClassLoader().getResourceAsStream(
                "callback-request.json");
        final byte[] payload = resource.readAllBytes();

        when(signatureValidator.validateSignature(payload, "SSSSIGNATURE")).thenReturn(true);

        final var parser = new WebhookParser(signatureValidator);
        final CallbackRequest callbackRequest = parser.handle("SSSSIGNATURE", payload);

        assertThat(callbackRequest).isNotNull();

        final List<Event> result = callbackRequest.events();

        @SuppressWarnings("rawtypes")
        final MessageEvent messageEvent = (MessageEvent) result.get(0);
        final TextMessageContent text = (TextMessageContent) messageEvent.message();
        assertThat(text.text()).isEqualTo("Hello, world");

        final String followedUserId = messageEvent.source().userId();
        assertThat(followedUserId).isEqualTo("u206d25c2ea6bd87c17655609a1c37cb8");
        assertThat(messageEvent.timestamp()).isEqualTo(
                Instant.parse("2016-05-07T13:57:59.859Z").toEpochMilli());
    }
}
