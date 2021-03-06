package org.jocean.xharbor.reactor;

import static org.junit.Assert.assertEquals;

import org.jocean.http.util.RxNettys;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.xharbor.api.TradeReactor.InOut;
import org.junit.Test;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;

public class BasicAuthenticateTestCase {

    @Test
    public final void testBasicAuthorizer() {
        final BasicAuthenticate authorizer = new BasicAuthenticate(
                new MatchRule(null, "/needauth(\\w)*", null),
                "hello", "world", "demo");

        final DefaultFullHttpRequest orgreq =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST,
                "/needauth/xxx");

        final InOut io =
            authorizer.react(null, new InOut() {
                @Override
                public Observable<? extends DisposableWrapper<HttpObject>> inbound() {
                    return Observable.just(RxNettys.wrap4release(orgreq));
                }
                @Override
                public Observable<? extends Object> outbound() {
                    return null;
                }})
            .toBlocking().value();

        final FullHttpResponse response = io.outbound()
                .map(obj -> (DisposableWrapper<HttpObject>)obj)
                .compose(RxNettys.message2fullresp(null))
                .toBlocking().single().unwrap();

        assertEquals(HttpResponseStatus.UNAUTHORIZED, response.status());
        assertEquals(HttpVersion.HTTP_1_0, response.protocolVersion());
        assertEquals("Basic realm=\"demo\"", response.headers().get(HttpHeaderNames.WWW_AUTHENTICATE));
    }
}
